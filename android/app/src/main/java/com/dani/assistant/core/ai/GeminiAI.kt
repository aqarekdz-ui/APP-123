package com.dani.assistant.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiAI {
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash-exp",
        apiKey = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"
    )
    private val chat = model.startChat(history = listOf(content { text("You are DANI, a smart Algerian personal assistant. Reply in Algerian Darija briefly. Be friendly. If user seems sad comfort them. If happy celebrate. Use emoji sometimes.") }))
    suspend fun sendMessage(msg: String) = try { chat.sendMessage(msg).text?.trim() ?: "..." } catch(e: Exception) { "Error" }
}
