package com.dani.assistant.core.ai

import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Recurrence
import com.google.ai.client.generativeai.GenerativeModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.ai.client.generativeai.type.content
import java.util.concurrent.ConcurrentHashMap
import com.dani.assistant.DaniApplication
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.map

data class ParsedTask(
    val title: String,
    val dueDate: Long?,
    val recurrence: Recurrence,
    val priority: PriorityLevel
)

private data class Backend(val id: String, val provider: String, val model: String)
private data class Turn(val role: String, val text: String)

private const val PERSONA = "You are DANI, a smart Algerian personal assistant. Reply in Algerian Darija briefly. Be friendly. If user seems sad comfort them. If happy celebrate. Use emoji sometimes."

class GeminiAI {
    companion object {
        private val API_KEY = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"

        // سلسلة بدائل مجانية: Gemini أولاً، ثم Groq ثم OpenRouter (إذا زدت مفاتيحهم من ⚙). كل مزود/نموذج له حصة مجانية مستقلة.
        private val GEMINI_MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.6-flash", "gemini-2.5-flash-lite")
        private val cooldownUntil = ConcurrentHashMap<String, Long>()
        private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

        private fun modelFor(name: String): GenerativeModel =
            modelCache.getOrPut(name) { GenerativeModel(modelName = name, apiKey = API_KEY) }

        private fun errText(e: Throwable): String = (e.javaClass.simpleName + " " + (e.message ?: "")).lowercase()

        private fun cooldownFor(e: Throwable): Long {
            val t = errText(e)
            return when {
                "quota" in t || " 429" in t || "exhausted" in t || "rate limit" in t || "too many" in t -> 60_000L
                "not found" in t || " 404" in t || "not supported" in t || "no longer" in t || "deprecated" in t || "decommissioned" in t -> 6 * 3_600_000L
                " 401" in t || "invalid api key" in t -> 10 * 60_000L
                else -> 10_000L
            }
        }

        private fun backends(): List<Backend> {
            val ctx = DaniApplication.instance
            val list = GEMINI_MODELS.map { Backend("gemini:$it", "gemini", it) }.toMutableList()
            if (ProviderSettings.groqKey(ctx).isNotBlank()) {
                list += Backend("groq:openai/gpt-oss-120b", "groq", "openai/gpt-oss-120b")
                list += Backend("groq:openai/gpt-oss-20b", "groq", "openai/gpt-oss-20b")
            }
            if (ProviderSettings.openRouterKey(ctx).isNotBlank()) {
                list += Backend("openrouter:openrouter/free", "openrouter", "openrouter/free")
            }
            return list
        }

        private fun candidates(): List<Backend> {
            val now = System.currentTimeMillis()
            val all = backends()
            val ready = all.filter { (cooldownUntil[it.id] ?: 0L) <= now }
            return if (ready.isEmpty()) all else ready
        }

        private fun fail(b: Backend, e: Exception) {
            cooldownUntil[b.id] = System.currentTimeMillis() + cooldownFor(e)
        }

        private suspend fun openAiChat(base: String, key: String, model: String, messages: List<Turn>, extra: Map<String, Any> = emptyMap()): String =
            withContext(Dispatchers.IO) {
                val conn = URL(base + "/chat/completions").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 60_000
                    conn.doOutput = true
                    conn.setRequestProperty("Authorization", "Bearer " + key)
                    conn.setRequestProperty("Content-Type", "application/json")
                    val arr = JSONArray()
                    messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
                    val body = JSONObject().put("model", model).put("messages", arr).put("max_tokens", 2048)
                    extra.forEach { (k, v) -> body.put(k, v) }
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) throw IOException("HTTP " + code + " " + text.take(300))
                    JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                } finally {
                    conn.disconnect()
                }
            }

        /** يشغّل طلباً على backend واحد. turns: آخر عنصر هو رسالة المستخدم. يرمي استثناء إذا الرد فارغ. */
        private suspend fun run(b: Backend, turns: List<Turn>): String {
            val out = when (b.provider) {
                "gemini" -> {
                    val m = modelFor(b.model)
                    if (turns.size == 1) m.generateContent(turns[0].text).text?.trim().orEmpty()
                    else {
                        val hist = turns.dropLast(1).map { t -> content(t.role) { text(t.text) } }
                        m.startChat(history = hist).sendMessage(turns.last().text).text?.trim().orEmpty()
                    }
                }
                else -> {
                    val ctx = DaniApplication.instance
                    val msgs = mutableListOf<Turn>()
                    if (turns.first().text == PERSONA) {
                        msgs.add(Turn("system", PERSONA))
                        msgs.addAll(turns.drop(1).map { Turn(if (it.role == "model") "assistant" else it.role, it.text) })
                    } else {
                        msgs.addAll(turns.map { Turn(if (it.role == "model") "assistant" else it.role, it.text) })
                    }
                    if (b.provider == "groq") openAiChat("https://api.groq.com/openai/v1", ProviderSettings.groqKey(ctx), b.model, msgs, mapOf("reasoning_effort" to "low"))
                    else openAiChat("https://openrouter.ai/api/v1", ProviderSettings.openRouterKey(ctx), b.model, msgs)
                }
            }
            if (out.isBlank()) throw IllegalStateException("empty reply")
            return out
        }

        private suspend fun <T> withFallback(block: suspend (Backend) -> T): T {
            var last: Exception? = null
            for (b in candidates()) {
                try {
                    return block(b)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(b, e)
                    last = e
                }
            }
            throw last ?: IllegalStateException("no model")
        }

        fun friendlyError(e: Throwable): String {
            val t = errText(e)
            return when {
                "quota" in t || " 429" in t || "exhausted" in t || "rate limit" in t || "too many" in t ->
                    "⚠️ وصلت الحد المجاني دالوقت. عاود بعد دقيقة، أو زيد مفتاح Groq المجاني من زر ⚙ (الأجوبة المحفوظة محلياً تبقى تخدم)."
                "api key" in t || "api_key" in t || " 403" in t || " 401" in t || "permission" in t ->
                    "⚠️ مشكل في مفتاح الذكاء (ممكن مرفوض)."
                "location" in t -> "⚠️ المزود ما يخدمش في منطقتك دالوقت."
                "unknownhost" in t || "unable to resolve" in t || "timeout" in t || "network" in t || "connect" in t ->
                    "⚠️ ما فيش انترنت."
                else -> "Error"
            }
        }
    }

    private suspend fun generate(prompt: String): String? = withFallback { run(it, listOf(Turn("user", prompt))) }

    // Keep chat history short: persona + last 5 exchanges (history is re-sent on every call = tokens)
    private val history = mutableListOf(Turn("user", PERSONA))
    private fun trimHistory() {
        while (history.size > 11) { history.removeAt(1); history.removeAt(1) }
    }

    private fun buildPrompt(msg: String, memories: List<String>): String =
        if (memories.isEmpty()) msg
        else "معلومات عن المستخدم:\n" + memories.joinToString("\n") { "- " + it } + "\n\nرسالة المستخدم: " + msg

    private fun remember(prompt: String, reply: String) {
        history.add(Turn("user", prompt))
        history.add(Turn("model", reply))
    }

    suspend fun sendMessage(msg: String, memories: List<String> = emptyList()): String {
        trimHistory()
        val prompt = buildPrompt(msg, memories)
        val turns = history.toList() + Turn("user", prompt)
        return try {
            val reply = withFallback { run(it, turns) }
            remember(prompt, reply)
            reply
        } catch (e: Exception) { "Error" }
    }

    fun sendMessageStream(msg: String, memories: List<String> = emptyList()): Flow<String> = flow {
        trimHistory()
        val prompt = buildPrompt(msg, memories)
        val turns = history.toList() + Turn("user", prompt)
        var last: Exception? = null
        for (b in candidates()) {
            val sb = StringBuilder()
            try {
                if (b.provider == "gemini") {
                    val hist = history.map { t -> content(t.role) { text(t.text) } }
                    modelFor(b.model).startChat(history = hist).sendMessageStream(prompt).collect { r ->
                        val t = r.text ?: ""
                        if (t.isNotEmpty()) { sb.append(t); emit(t) }
                    }
                } else {
                    val r = run(b, turns) // غير تدفقي: الرد كامل مرة وحدة
                    sb.append(r)
                    emit(r)
                }
                if (sb.isEmpty()) throw IllegalStateException("empty reply")
                remember(prompt, sb.toString())
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(b, e)
                if (sb.isNotEmpty()) throw e // رد جزئي وصل للمستخدم: ما نبدّلوش المزود
                last = e
            }
        }
        throw last ?: IllegalStateException("no model")
    }

    // Returns a short task title if the message contains a task/reminder/appointment, else null
    suspend fun extractTask(msg: String): String? {
        return try {
            val prompt = "حلل الرسالة التالية. إذا كانت تتضمن مهمة أو تذكيراً أو موعداً أو شيئاً يجب فعله، " +
                "أرجع عنواناً قصيراً للمهمة فقط (بدون أي شرح). وإلا أرجع الكلمة NONE فقط.\n\nالرسالة: " + msg
            val out = generate(prompt)?.trim()
            if (out.isNullOrBlank() || out.uppercase().contains("NONE")) null else out.take(120)
        } catch (e: Exception) { null }
    }

    /** يحوّل أمراً طبيعياً بالدارجة ("ذكّرني غدوة 8 نمشي للطبيب") إلى مهمة جاهزة، أو null إذا ماشي أمر. */
    suspend fun parseTaskCommand(msg: String): ParsedTask? {
        return try {
            val now = Date()
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.ENGLISH).format(now)
            val prompt = "أنت محلل أوامر المهام. الوقت الحالي: " + nowStr + ". " +
                "حلل رسالة المستخدم (دارجة جزائرية أو عربية أو فرنسية). إذا كان يطلب تذكيراً أو إضافة مهمة أو موعد، " +
                "أرجع JSON فقط بدون أي نص آخر: {\"title\": \"عنوان قصير بدون كلمات الأمر والوقت\", \"due\": \"yyyy-MM-dd HH:mm\" أو null, " +
                "\"recurrence\": \"NONE|DAILY|WEEKLY|MONTHLY\", \"priority\": \"High|Medium|Low\"}. " +
                "إذا ما هوش طلب مهمة/تذكير أرجع {\"title\": null}. " +
                "قواعد الوقت: غدوة/غدا = اليوم التالي، بعد غدوة = بعد يومين، الصباح بدون ساعة = 08:00، الظهر = 12:00، العصر/العشية = 16:00، المغرب = 18:30، الليل = 21:00. " +
                "إذا ذكر ساعة بدون صباح/مساء اختر أقرب وقت قادم في المستقبل. إذا ما ذكر أي وقت اجعل due = null. " +
                "كل يوم = DAILY، كل أسبوع أو كل يوم أسبوع معيّن = WEEKLY (due = أول موعد قادم)، كل شهر = MONTHLY. " +
                "priority = High فقط إذا قال مهم أو عاجل أو ضروري، Low إذا قال عادي أو مش مستعجل، وإلا Medium.\n\nالرسالة: " + msg
            val out = cleanJson(generate(prompt) ?: return null)
            val o = JSONObject(out)
            val title = o.optString("title", "").trim()
            if (title.isBlank() || title.equals("null", true)) return null
            val dueStr = o.optString("due", "").trim()
            var due: Long? = if (dueStr.isBlank() || dueStr.equals("null", true)) null else {
                val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                f.isLenient = false
                f.parse(dueStr)?.time
            }
            if (due != null && due <= System.currentTimeMillis()) due = null
            val rec = if (due == null) Recurrence.NONE else try { Recurrence.valueOf(o.optString("recurrence", "NONE").uppercase()) } catch (e: Exception) { Recurrence.NONE }
            val pr = when (o.optString("priority", "Medium")) {
                "High" -> PriorityLevel.IMPORTANT
                "Low" -> PriorityLevel.LOW
                else -> PriorityLevel.MEDIUM
            }
            ParsedTask(title.take(120), due, rec, pr)
        } catch (e: Exception) { null }
    }

    private fun cleanJson(t: String): String = t.replace("```json", "").replace("```", "").trim()

    // One call: extracts a task (if any) and new durable facts about the user
    suspend fun analyze(msg: String, known: List<String>): Triple<String?, List<String>, List<String>> {
        if (msg.trim().length < 20) return Triple(null, emptyList(), emptyList())
        return try {
            val prompt = "حلل رسالة المستخدم وأرجع JSON فقط بهذا الشكل بدون أي نص آخر: " +
                "{\"task\": \"عنوان قصير للمهمة أو التذكير أو الموعد إن وجد وإلا null\", \"facts\": [\"...\"], \"secrets\": [\"...\"]}. " +
                "facts = معلومات جديدة ودائمة عن المستخدم (تفضيلات، عادات، عمل، عائلة، اهتمامات، أسلوب يحبه في التعامل)، " +
                "جمل قصيرة (أقل من 100 حرف) وبصيغة الغائب، بحد أقصى 3. " +
                "لا تحفظ الحالة المزاجية العابرة ولا ما هو موجود أصلاً في المعلومات المعروفة. secrets = كلمات السر وأرقام الهويات والبطاقات والحسابات وأي بيانات سرية ذكرها المستخدم، كل واحدة جملة كاملة تذكر ما هي وقيمتها (مثل: كلمة سر واي فاي البيت: 1234)، ولا تضعها في facts. " +
                "إذا لا توجد معلومة جديدة أرجع مصفوفة فارغة.\n\n" +
                "المعلومات المعروفة:\n" + (if (known.isEmpty()) "(لا شيء)" else known.joinToString("\n") { "- " + it }) +
                "\n\nرسالة المستخدم: " + msg
            val out = cleanJson(generate(prompt) ?: return Triple(null, emptyList(), emptyList()))
            val obj = JSONObject(out)
            val task = obj.optString("task", "").trim().let { if (it.isBlank() || it.equals("null", true)) null else it.take(120) }
            val arr: JSONArray? = obj.optJSONArray("facts")
            val facts = if (arr == null) emptyList() else List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotBlank() && it.length <= 150 }.take(3)
            val sec: JSONArray? = obj.optJSONArray("secrets")
            val secrets = if (sec == null) emptyList() else List(sec.length()) { sec.optString(it).trim() }.filter { it.isNotBlank() }.take(3)
            Triple(task, facts, secrets)
        } catch (e: Exception) { Triple(null, emptyList(), emptyList()) }
    }

    // Merge duplicates / near-duplicates when memory grows too large
    suspend fun consolidate(facts: List<String>): List<String>? {
        return try {
            val prompt = "ادمج هذه المعلومات عن المستخدم: احذف التكرار واجمع المتشابه وأبقِ كل ما هو مهم، " +
                "بحد أقصى 30 جملة قصيرة. أرجع JSON array فقط.\n\n" + facts.joinToString("\n") { "- " + it }
            val arr = JSONArray(cleanJson(generate(prompt) ?: return null))
            val list = List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotBlank() }
            if (list.isEmpty()) null else list
        } catch (e: Exception) { null }
    }
}
