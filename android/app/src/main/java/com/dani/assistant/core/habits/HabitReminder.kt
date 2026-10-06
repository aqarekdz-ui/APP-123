package com.dani.assistant.core.habits

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
import com.dani.assistant.MainActivity
import com.dani.assistant.core.settings.AppSettings
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** تذكير مسائي بالعادات (WorkManager OneTime يجدّد نفسو): 21:00، وما يجيش إذا كلشي تمّ. */
object HabitReminder {
    private const val WORK = "dani_habit_reminder"
    private const val CHANNEL = "dani_habits"
    private const val NOTIF_ID = 9800
    const val HOUR = 21
    const val MINUTE = 0

    /** أول HH:MM بعد [nowMs] بأكثر من دقيقة (منطق صافي). */
    fun nextTrigger(nowMs: Long, hour: Int = HOUR, minute: Int = MINUTE): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        while (c.timeInMillis <= nowMs + 60_000L) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(ctx: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val wm = WorkManager.getInstance(ctx)
        if (!AppSettings.habitReminderEnabled(ctx)) { wm.cancelUniqueWork(WORK); return }
        val delay = nextTrigger(System.currentTimeMillis()) - System.currentTimeMillis()
        wm.enqueueUniqueWork(WORK, policy, OneTimeWorkRequestBuilder<HabitReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    fun runAndNotify(ctx: Context) {
        val ev = HabitDigest.evening(HabitStore.list(ctx), LocalDate.now()) ?: return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "تذكير العادات", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                ctx, NOTIF_ID, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(ev.title)
                .setContentText(ev.body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(ev.body))
                .setContentIntent(open).setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }
}

class HabitReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { if (AppSettings.habitReminderEnabled(applicationContext)) HabitReminder.runAndNotify(applicationContext) } catch (e: Exception) { }
        try { HabitReminder.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE) } catch (e: Exception) { }
        return Result.success()
    }
}
