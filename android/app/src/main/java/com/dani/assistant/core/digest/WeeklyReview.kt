package com.dani.assistant.core.digest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dani.assistant.DaniApplication
import com.dani.assistant.MainActivity
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** مراجعة أسبوعية: كل أحد 19:00 إشعار بملخص (المنجز، المصاريف، العادات) + نصيحة قصيرة من الذكاء الاصطناعي. */
object WeeklyReview {
    private const val WORK = "dani_weekly_review"
    private const val CHANNEL = "dani_weekly"
    private const val NOTIF_ID = 9400
    private const val HOUR = 19

    private fun nextSundayDelayMs(): Long {
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, HOUR); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || c.timeInMillis <= now + 60_000L) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - now
    }

    /** policy: KEEP عند بدء التطبيق، APPEND_OR_REPLACE من داخل العامل لتجديد الأسبوع الجاي. */
    fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val wm = WorkManager.getInstance(ctx)
        if (!AppSettings.weeklyReviewEnabled(ctx)) { wm.cancelUniqueWork(WORK); return }
        val req = OneTimeWorkRequestBuilder<WeeklyReviewWorker>().setInitialDelay(nextSundayDelayMs(), TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork(WORK, policy, req)
    }

    private fun dayStart(offset: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** ملخص الأسبوع (آخر 7 أيام بما فيها اليوم) كنص محلي. */
    suspend fun summary(ctx: Context): String {
        val from = dayStart(-6)
        val to = dayStart(1)
        val sb = StringBuilder()

        val tasks = DaniApplication.instance.taskRepository.getAllTasks().first()
        val done = tasks.filter { t -> val c = t.completedAt; t.isCompleted && c != null && c >= from && c < to }
        val pending = tasks.filter { !it.isCompleted && it.status.name != "CANCELLED" }
        val overdue = pending.count { t -> val d = t.dueDate; d != null && d < dayStart(0) }
        sb.append("✅ المهام: أنجزت ").append(done.size).append("، باقي ").append(pending.size)
        if (overdue > 0) sb.append(" (منها ").append(overdue).append(" متأخرة)")
        done.take(4).forEach { sb.append("\n  • ").append(it.title.take(50)) }

        val money = MoneyStore.entries(ctx).filter { (it.type == "expense" || it.type == "income") && it.date >= from && it.date < to }
        val exp = money.filter { it.type == "expense" }
        val inc = money.filter { it.type == "income" }.sumOf { it.amount }
        sb.append("\n\n💰 المال: صرفت ").append(MoneyStore.fmt(exp.sumOf { it.amount }))
        if (inc > 0) sb.append("، دخل ").append(MoneyStore.fmt(inc))
        val cats = exp.groupBy { it.category.ifBlank { "أخرى" } }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }.take(3)
        if (cats.isNotEmpty()) sb.append("\n  ").append(cats.joinToString("  •  ") { it.first + " " + MoneyStore.fmt(it.second) })
        val over = MoneyStore.budgets(ctx).filter { (c, b) -> MoneyStore.spentThisMonth(ctx, c) >= b * 8 / 10 }
        if (over.isNotEmpty()) {
            sb.append("\n  🎯 ميزانية قاربت/تجاوزت: ").append(over.keys.joinToString("، ") { c ->
                val b = over[c] ?: 1L
                c + " " + (MoneyStore.spentThisMonth(ctx, c) * 100 / b) + "%"
            })
        }

        val (mg, me) = com.dani.assistant.core.meds.MedStore.adherence(ctx)
        if (me > 0) sb.append("\n\n💊 الأدوية: أخذت ").append(mg).append(" من ").append(me).append(" جرعة (").append(mg * 100 / me).append("%)")

        val (fn, fm) = com.dani.assistant.core.focus.FocusLog.stats(ctx, 6)
        if (fn > 0) sb.append("\n\n🍅 التركيز: ").append(fn).append(" جلسات (").append(fm / 60).append("س ").append(fm % 60).append("د)")

        val habits = HabitStore.list(ctx)
        if (habits.isNotEmpty()) {
            sb.append("\n\n🔥 العادات (من 7):")
            habits.forEach { h ->
                val cnt = (-6..0).count { h.days.contains(HabitStore.dayKey(it)) }
                sb.append("\n  ").append(h.emoji).append(" ").append(h.name).append(": ").append(cnt)
            }
        }
        return sb.toString()
    }

    /** نصيحة قصيرة بالدارجة من الذكاء الاصطناعي، أو null إذا فشل. */
    suspend fun advice(summary: String): String? = try {
        val a = GeminiAI().weeklyAdvice(summary)
        if (a.isBlank()) null else a
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }

    /** المراجعة الكاملة (نص). */
    suspend fun full(ctx: Context): String {
        val s = summary(ctx)
        val a = advice(s)
        return "📋 مراجعة أسبوعك\n\n" + s + (if (a != null) "\n\n💡 " + a else "")
    }

    suspend fun runAndNotify(ctx: Context) {
        val text = full(ctx)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "المراجعة الأسبوعية", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                ctx, NOTIF_ID, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val body = text.removePrefix("📋 مراجعة أسبوعك").trim()
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("📋 مراجعة أسبوعك")
                .setContentText(body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }
}

class WeeklyReviewWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { if (AppSettings.weeklyReviewEnabled(applicationContext)) WeeklyReview.runAndNotify(applicationContext) } catch (e: Exception) { }
        try { WeeklyReview.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE) } catch (e: Exception) { }
        return Result.success()
    }
}
