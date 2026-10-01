package com.dani.assistant.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiAI {
    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash-exp",
        apiKey = "AQ.Ab8RN6LuSvRXB1xvu-" + "dQbYF4jC0RgNI6Ux79sIEijjPnE6Y97A"
    )

    private val chat = model.startChat(
        history = listOf(
            content {
                text("""
You are DANI, a smart Algerian personal assistant.
Rules:
1. Speak in Algerian Darija (Arabic dialect)
2. Keep replies short (1-3 sentences max)
3. Be friendly and respectful
4. If user asks for reminder or task, confirm understanding
5. If user seems sad, comfort them gently
6. If user seems happy, celebrate with them
7. Use emoji sometimes
                """)
            }
        )
    )

    suspend fun sendMessage(message: String): String {
        return try {
            val response = chat.sendMessage(message)
            response.text?.trim() ?: "Please repeat"
        } catch (e: Exception) {
            "Check internet and try again"
        }
    }
}
