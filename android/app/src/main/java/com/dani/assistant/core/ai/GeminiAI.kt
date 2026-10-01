package com.dani.assistant.core.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
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
                    أنت DANI، مساعد شخصي ذكي جزائري.
                    تتكلم بالدارجة الجزائرية وتفهمها.
                    مهمتك:
                    1. تساعد المستخدم في مهامه اليومية
                    2. تفهم طلباته وتضيف مهام تلقائياً
                    3. ترد بإيجاز وود
                    
                    مثال:
                    المستخدم: "ذكّرني نغسل الحوايج غداً"
                    أنت: "واخا، غادي نذكّرك تغسل الحوايج غداً. واش وقت مناسب؟"
                """)
            }
        )
    )
    
    suspend fun sendMessage(message: String): String {
        return try {
            val response = chat.sendMessage(message)
            response.text?.trim() ?: "عافاك عاود قول"
        } catch (e: Exception) {
            "عافاك تحقق من الإنترنت وحاول مرة أخرى"
        }
    }
}
