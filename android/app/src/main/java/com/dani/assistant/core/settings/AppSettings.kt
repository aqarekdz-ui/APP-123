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

    /** المراجعة الأسبوعية بالذكاء الاصطناعي (كل أحد 19:00). */
    fun weeklyReviewEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("weekly_review", true)
    fun setWeeklyReviewEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("weekly_review", v).apply()

    /** تقرير الشهر الفايت (يوم 1 على 09:30). */
    fun monthlyReportEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("monthly_report", true)
    fun setMonthlyReportEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("monthly_report", v).apply()

    /** تذكير العادات المسائي (21:00): فقط إذا بقات عادات ما تمّتش. */
    fun habitReminderEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("habit_reminder", true)
    fun setHabitReminderEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("habit_reminder", v).apply()

    /** وقت الملخص بالدقائق من منتصف الليل (الافتراضي 08:00). */
    fun digestMinutes(ctx: Context): Int = prefs(ctx).getInt("digest_min", 8 * 60)
    fun setDigestMinutes(ctx: Context, m: Int) = prefs(ctx).edit().putInt("digest_min", m).apply()

    /** قفل التطبيق بالبصمة أو قفل شاشة الهاتف. */
    fun lockEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("app_lock", false)
    fun setLockEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("app_lock", v).apply()

    /** وضع الثيم: 0 داكن (افتراضي)، 1 فاتح، 2 حسب النظام. */
    fun themeMode(ctx: Context): Int = prefs(ctx).getInt("theme_mode", 0)
    fun setThemeMode(ctx: Context, v: Int) = prefs(ctx).edit().putInt("theme_mode", v).apply()

    /** البحث في الملف المحلي أولاً قبل سؤال الذكاء الاصطناعي. */
    fun localFirst(ctx: Context): Boolean = prefs(ctx).getBoolean(LOCAL_FIRST, true)
    fun setLocalFirst(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(LOCAL_FIRST, v).apply()
}
