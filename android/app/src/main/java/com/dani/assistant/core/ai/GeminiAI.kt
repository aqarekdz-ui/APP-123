package com.dani.assistant.core.ai

import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Recurrence
import com.google.ai.client.generativeai.GenerativeModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.Content
import java.util.concurrent.ConcurrentHashMap
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

class GeminiAI {
    companion object {
        private val API_KEY = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"

        // سلسلة بدائل: كل نموذج له حصة مجانية مستقلة، فإذا نموذج وصل الحد أو توقف ننتقل للي بعده تلقائياً
        private val MODELS = listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.6-flash", "gemini-2.5-flash-lite")
        private val cooldownUntil = ConcurrentHashMap<String, Long>()
        private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

        private fun modelFor(name: String): GenerativeModel =
            modelCache.getOrPut(name) { GenerativeModel(modelName = name, apiKey = API_KEY) }

        private fun errText(e: Throwable): String = (e.javaClass.simpleName + " " + (e.message ?: "")).lowercase()

        private fun cooldownFor(e: Throwable): Long {
            val t = errText(e)
            return when {
                "quota" in t || " 429" in t || "exhausted" in t || "rate limit" in t || "too many" in t -> 60_000L
                "not found" in t || " 404" in t || "not supported" in t || "no longer" in t || "deprecated" in t -> 6 * 3_600_000L
                else -> 10_000L
            }
        }

        private fun candidates(): List<String> {
            val now = System.currentTimeMillis()
            val ready = MODELS.filter { (cooldownUntil[it] ?: 0L) <= now }
            return if (ready.isEmpty()) MODELS else ready
        }

        private suspend fun <T> withFallback(block: suspend (GenerativeModel) -> T): T {
            var last: Exception? = null
            for (name in candidates()) {
                try {
                    return block(modelFor(name))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    cooldownUntil[name] = System.currentTimeMillis() + cooldownFor(e)
                    last = e
                }
            }
            throw last ?: IllegalStateException("no model")
        }

        fun friendlyError(e: Throwable): String {
            val t = errText(e)
            return when {
                "quota" in t || " 429" in t || "exhausted" in t || "rate limit" in t || "too many" in t ->
                    "⚠️ وصلت الحد المجاني لـ Gemini دالوقت. عاود بعد دقيقة (الأجوبة المحفوظة محلياً تبقى تخدم)."
                "api key" in t || "api_key" in t || " 403" in t || "permission" in t ->
                    "⚠️ مشكل في مفتاح Gemini (ممكن مرفوض)."
                "location" in t -> "⚠️ Gemini ما يخدمش في منطقتك دالوقت."
                "unknownhost" in t || "unable to resolve" in t || "timeout" in t || "network" in t || "connect" in t ->
                    "⚠️ ما فيش انترنت."
                else -> "Error"
            }
        }
    }

    private suspend fun generate(prompt: String): String? = withFallback { it.generateContent(prompt).text }

    private val persona = content { text("You are DANI, a smart Algerian personal assistant. Reply in Algerian Darija briefly. Be friendly. If user seems sad comfort them. If happy celebrate. Use emoji sometimes.") }
    // Keep chat history short: persona + last 5 exchanges (history is re-sent on every call = tokens)
    private val history = mutableListOf<Content>(persona)
    private fun trimHistory() {
        while (history.size > 11) { history.removeAt(1); history.removeAt(1) }
    }

    private fun buildPrompt(msg: String, memories: List<String>): String =
        if (memories.isEmpty()) msg
        else "معلومات عن المستخدم:\n" + memories.joinToString("\n") { "- " + it } + "\n\nرسالة المستخدم: " + msg

    private fun remember(prompt: String, reply: String) {
        history.add(content("user") { text(prompt) })
        history.add(content("model") { text(reply) })
    }

    suspend fun sendMessage(msg: String, memories: List<String> = emptyList()): String {
        trimHistory()
        val prompt = buildPrompt(msg, memories)
        return try {
            val reply = withFallback { it.startChat(history = history.toList()).sendMessage(prompt).text?.trim() }
            if (reply.isNullOrEmpty()) "..." else { remember(prompt, reply); reply }
        } catch (e: Exception) { "Error" }
    }

    fun sendMessageStream(msg: String, memories: List<String> = emptyList()): Flow<String> = flow {
        trimHistory()
        val prompt = buildPrompt(msg, memories)
        var last: Exception? = null
        for (name in candidates()) {
            val sb = StringBuilder()
            try {
                val chat = modelFor(name).startChat(history = history.toList())
                chat.sendMessageStream(prompt).collect { r ->
                    val t = r.text ?: ""
                    if (t.isNotEmpty()) { sb.append(t); emit(t) }
                }
                if (sb.isNotEmpty()) remember(prompt, sb.toString())
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cooldownUntil[name] = System.currentTimeMillis() + cooldownFor(e)
                if (sb.isNotEmpty()) throw e // رد جزئي وصل للمستخدم: ما نبدّلوش النموذج
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
