package com.dani.assistant.core.backup

import android.content.Context
import java.util.concurrent.TimeUnit

/** تذكير بالنسخة الاحتياطية: إذا فات أكثر من 14 يوم على آخر نسخة (تلقائية أو يدوية). */
object BackupReminder {
    const val DAYS = 14
    private const val PREFS = "dani_backup_reminder"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** منطق صافي (يتجرّب بـ JUnit). المرجع = آخر نسخة، وإلا أول مرة شفنا فيها التطبيق. */
    fun shouldRemind(lastMs: Long, firstSeenMs: Long, snoozeUntilMs: Long, nowMs: Long): Boolean {
        if (nowMs < snoozeUntilMs) return false
        val ref = if (lastMs > 0) lastMs else firstSeenMs
        if (ref <= 0) return false
        return nowMs - ref >= TimeUnit.DAYS.toMillis(DAYS.toLong())
    }

    /** عدد الأيام من آخر نسخة، أو null إذا ما كانتش أبداً. */
    fun daysSince(lastMs: Long, nowMs: Long): Int? =
        if (lastMs <= 0) null else TimeUnit.MILLISECONDS.toDays(nowMs - lastMs).toInt().coerceAtLeast(0)

    fun markDone(ctx: Context) { p(ctx).edit().putLong("last_ms", System.currentTimeMillis()).putLong("snooze", 0L).apply() }

    fun snooze(ctx: Context, days: Int = 3) {
        p(ctx).edit().putLong("snooze", System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())).apply()
    }

    fun last(ctx: Context): Long = p(ctx).getLong("last_ms", 0L)

    /** يسجل أول ظهور مرة وحدة، ويرجع هل لازم نعرض التذكير. */
    fun shouldRemindNow(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        val pr = p(ctx)
        if (pr.getLong("first_seen", 0L) == 0L) pr.edit().putLong("first_seen", now).apply()
        return shouldRemind(pr.getLong("last_ms", 0L), pr.getLong("first_seen", 0L), pr.getLong("snooze", 0L), now)
    }
}
