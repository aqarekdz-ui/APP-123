package com.dani.assistant.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GeminiAI {
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash-exp",
        apiKey = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"
    )
    private val chat = model.startChat(history = listOf(content { text("You are DANI, a smart Algerian personal assistant. Reply in Algerian Darija briefly. Be friendly. If user seems sad comfort them. If happy celebrate. Use emoji sometimes.") }))
    private fun buildPrompt(msg: String, memories: List<String>): String =
        if (memories.isEmpty()) msg
        else "معلومات عن المستخدم:\n" + memories.joinToString("\n") { "- " + it } + "\n\nرسالة المستخدم: " + msg

    suspend fun sendMessage(msg: String, memories: List<String> = emptyList()): String {
        return try { chat.sendMessage(buildPrompt(msg, memories)).text?.trim() ?: "..." } catch (e: Exception) { "Error" }
    }

    fun sendMessageStream(msg: String, memories: List<String> = emptyList()): Flow<String> =
        chat.sendMessageStream(buildPrompt(msg, memories)).map { it.text ?: "" }

    // Returns a short task title if the message contains a task/reminder/appointment, else null
    suspend fun extractTask(msg: String): String? {
        return try {
            val prompt = "حلل الرسالة التالية. إذا كانت تتضمن مهمة أو تذكيراً أو موعداً أو شيئاً يجب فعله، " +
                "أرجع عنواناً قصيراً للمهمة فقط (بدون أي شرح). وإلا أرجع الكلمة NONE فقط.\n\nالرسالة: " + msg
            val out = model.generateContent(prompt).text?.trim()
            if (out.isNullOrBlank() || out.uppercase().contains("NONE")) null else out.take(120)
        } catch (e: Exception) { null }
    }
}
