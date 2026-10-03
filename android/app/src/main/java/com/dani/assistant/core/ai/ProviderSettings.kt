package com.dani.assistant.core.ai

import android.content.Context

/** مفاتيح المزودات المجانية الإضافية (Groq / OpenRouter). ملف prefs منفصل عن الشات حتى ما يتمسحش مع "Clear chat". */
object ProviderSettings {
    private const val PREFS = "dani_providers"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun groqKey(ctx: Context): String = prefs(ctx).getString("groq", null)?.trim().orEmpty()
    fun openRouterKey(ctx: Context): String = prefs(ctx).getString("openrouter", null)?.trim().orEmpty()

    fun save(ctx: Context, groq: String, openRouter: String) {
        prefs(ctx).edit().putString("groq", groq.trim()).putString("openrouter", openRouter.trim()).apply()
    }
}
