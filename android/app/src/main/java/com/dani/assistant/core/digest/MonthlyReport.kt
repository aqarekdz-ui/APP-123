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
import com.dani.assistant.core.money.MoneyReports
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** ملخص الشهر الفايت: يوم 1 على 09:30 إشعار محلي (بلا ذكاء اصطناعي): المصروف، الدخل، الفئات، المقارنة، المهام. */
object MonthlyReport {
    private const val WORK = "dani_monthly_report"
    private const val CHANNEL = "dani_monthly"
    private const val NOTIF_ID = 9700
    private const val HOUR = 9
    private const val MINUTE = 30

    val monthNames = listOf("جانفي", "فيفري", "مارس", "أفريل", "ماي", "جوان", "جويلية", "أوت", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

    /** أول يوم 1 (على 09:30) بعد [nowMs] بأكثر من دقيقة. */
    fun nextTrigger(nowMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, HOUR); c.set(Calendar.MINUTE, MINUTE); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        while (c.timeInMillis <= nowMs + 60_000L) { c.add(Calendar.MONTH, 1); c.set(Calendar.DAY_OF_MONTH, 1) }
        return c.timeInMillis
    }

    /** حدود الشهر: offset=0 هذا الشهر، -1 الفايت... (من، إلى، رقم الشهر 0..11). */
    fun monthRange(nowMs: Long, offset: Int): Triple<Long, Long, Int> {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, offset)
        val a = c.timeInMillis
        val m = c.get(Calendar.MONTH)
        c.add(Calendar.MONTH, 1)
        return Triple(a, c.timeInMillis, m)
    }

    /** نص التقرير (منطق صافي). prevPrevExpense = مصروف الشهر اللي قبلو للمقارنة، أو 0. */
    fun text(monthIdx: Int, expense: Long, income: Long, cats: List<Pair<String, Long>>, prevPrevExpense: Long, doneTasks: Int, entriesCount: Int): String {
        val sb = StringBuilder("📅 ملخص ").append(monthNames[monthIdx.coerceIn(0, 11)])
        if (entriesCount == 0 && doneTasks == 0) return sb.append("\nما سجلتش شيء في هذا الشهر.").toString()
        sb.append("\n➖ مصروف: ").append(MoneyStore.fmt(expense)).append("\n➕ دخل: ").append(MoneyStore.fmt(income))
        sb.append("\nالرصيد: ").append(if (income - expense >= 0) "+" else "-").append(MoneyStore.fmt(Math.abs(income - expense)))
        if (cats.isNotEmpty()) sb.append("\n🏷 ").append(cats.take(3).joinToString("  •  ") { it.first + " " + MoneyStore.fmt(it.second) })
        if (prevPrevExpense > 0) {
            val pct = Math.round((expense - prevPrevExpense) * 100.0 / prevPrevExpense).toInt()
            sb.append("\n📊 مقارنة بالشهر اللي قبلو: ").append(if (pct >= 0) "+" else "").append(pct).append("%")
        }
        sb.append("\n✅ مهام منجزة: ").append(doneTasks)
        return sb.toString()
    }

    suspend fun build(ctx: Context, nowMs: Long = System.currentTimeMillis()): String {
        val (a, b, m) = monthRange(nowMs, -1)
        val (pa, pb, _) = monthRange(nowMs, -2)
        val entries = MoneyStore.entries(ctx)
        val inRange = entries.filter { (it.type == "expense" || it.type == "income") && it.date in a until b }
        val exp = inRange.filter { it.type == "expense" }.sumOf { it.amount }
        val inc = inRange.filter { it.type == "income" }.sumOf { it.amount }
        val cats = MoneyReports.categoryTotals(entries, a, b)
        val prevExp = entries.filter { it.type == "expense" && it.date in pa until pb }.sumOf { it.amount }
        val tasks = try { DaniApplication.instance.taskRepository.getAllTasks().first() } catch (e: Exception) { emptyList() }
        val done = tasks.count { t -> val c = t.completedAt; t.isCompleted && c != null && c in a until b }
        return text(m, exp, inc, cats, prevExp, done, inRange.size)
    }

    fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val wm = WorkManager.getInstance(ctx)
        if (!AppSettings.monthlyReportEnabled(ctx)) { wm.cancelUniqueWork(WORK); return }
        val delay = nextTrigger(System.currentTimeMillis()) - System.currentTimeMillis()
        wm.enqueueUniqueWork(WORK, policy, OneTimeWorkRequestBuilder<MonthlyReportWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    suspend fun runAndNotify(ctx: Context) {
        val body = build(ctx)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "التقرير الشهري", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, NOTIF_ID, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(body.lineSequence().first())
                .setContentText(body.lineSequence().drop(1).firstOrNull() ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body.lineSequence().drop(1).joinToString("\n")))
                .setContentIntent(open).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }
}

class MonthlyReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { if (AppSettings.monthlyReportEnabled(applicationContext)) MonthlyReport.runAndNotify(applicationContext) } catch (e: Exception) { }
        try { MonthlyReport.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE) } catch (e: Exception) { }
        return Result.success()
    }
}
