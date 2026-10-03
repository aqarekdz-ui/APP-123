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

    /** الملخص الصباحي اليومي (إشعار بمهام اليوم). */
    fun digestEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("digest", true)
    fun setDigestEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("digest", v).apply()

    /** وقت الملخص بالدقائق من منتصف الليل (الافتراضي 08:00). */
    fun digestMinutes(ctx: Context): Int = prefs(ctx).getInt("digest_min", 8 * 60)
    fun setDigestMinutes(ctx: Context, m: Int) = prefs(ctx).edit().putInt("digest_min", m).apply()

    /** البحث في الملف المحلي أولاً قبل سؤال الذكاء الاصطناعي. */
    fun localFirst(ctx: Context): Boolean = prefs(ctx).getBoolean(LOCAL_FIRST, true)
    fun setLocalFirst(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(LOCAL_FIRST, v).apply()
}
