package com.dani.assistant.core.settings

import android.content.Context

/** إعدادات التطبيق البسيطة (SharedPreferences "dani_settings"). */
object AppSettings {
    private const val PREFS = "dani_settings"
    private const val AUTO_LEARN = "auto_learn"
    private const val LOCAL_FIRST = "local_first"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** التعلم التلقائي: استخراج معلومات/أسرار من كلامك وحفظ أجوبة Gemini في ملف المعرفة. */
    fun autoLearn(ctx: Context): Boolean = prefs(ctx).getBoolean(AUTO_LEARN, true)
    fun setAutoLearn(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(AUTO_LEARN, v).apply()

    /** البحث في الملف المحلي أولاً قبل سؤال الذكاء الاصطناعي. */
    fun localFirst(ctx: Context): Boolean = prefs(ctx).getBoolean(LOCAL_FIRST, true)
    fun setLocalFirst(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(LOCAL_FIRST, v).apply()
}
