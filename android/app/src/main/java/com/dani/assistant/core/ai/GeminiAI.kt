package com.dani.assistant.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.map

class GeminiAI {
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash-exp",
        apiKey = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"
    )
    private val chat = model.startChat(history = listOf(content { text("You are DANI, a smart Algerian personal assistant. Reply in Algerian Darija briefly. Be friendly. If user seems sad comfort them. If happy celebrate. Use emoji sometimes.") }))
    // Keep chat history short: persona + last 5 exchanges (history is re-sent on every call = tokens)
    private fun trimHistory() {
        try { while (chat.history.size > 11) { chat.history.removeAt(1); chat.history.removeAt(1) } } catch (e: Exception) { }
    }

    private fun buildPrompt(msg: String, memories: List<String>): String =
        if (memories.isEmpty()) msg
        else "معلومات عن المستخدم:\n" + memories.joinToString("\n") { "- " + it } + "\n\nرسالة المستخدم: " + msg

    suspend fun sendMessage(msg: String, memories: List<String> = emptyList()): String {
        trimHistory()
        return try { chat.sendMessage(buildPrompt(msg, memories)).text?.trim() ?: "..." } catch (e: Exception) { "Error" }
    }

    fun sendMessageStream(msg: String, memories: List<String> = emptyList()): Flow<String> =
        run { trimHistory(); chat.sendMessageStream(buildPrompt(msg, memories)) }.map { it.text ?: "" }

    // Returns a short task title if the message contains a task/reminder/appointment, else null
    suspend fun extractTask(msg: String): String? {
        return try {
            val prompt = "حلل الرسالة التالية. إذا كانت تتضمن مهمة أو تذكيراً أو موعداً أو شيئاً يجب فعله، " +
                "أرجع عنواناً قصيراً للمهمة فقط (بدون أي شرح). وإلا أرجع الكلمة NONE فقط.\n\nالرسالة: " + msg
            val out = model.generateContent(prompt).text?.trim()
            if (out.isNullOrBlank() || out.uppercase().contains("NONE")) null else out.take(120)
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
            val out = cleanJson(model.generateContent(prompt).text ?: return Triple(null, emptyList(), emptyList()))
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
            val arr = JSONArray(cleanJson(model.generateContent(prompt).text ?: return null))
            val list = List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotBlank() }
            if (list.isEmpty()) null else list
        } catch (e: Exception) { null }
    }
}
