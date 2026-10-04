package com.dani.assistant.core.digest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dani.assistant.DaniApplication
import com.dani.assistant.MainActivity
import com.dani.assistant.core.alarm.NotificationHelper
import com.dani.assistant.core.settings.AppSettings
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** ملخص صباحي يومي بإشعار: مهام اليوم + المتأخرة. الجدولة غير دقيقة بالثانية (لا تحتاج إذن المنبهات). */
object MorningDigest {
    private const val REQUEST_CODE = 7001
    private const val NOTIFICATION_ID = 9001

    private fun pendingIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, Intent(ctx, MorningDigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** يجدول (أو يلغي) الملخص حسب الإعدادات. آمن للاستدعاء عدة مرات. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(ctx)
        if (!AppSettings.digestEnabled(ctx)) {
            am.cancel(pi)
            return
        }
        val minutes = AppSettings.digestMinutes(ctx)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 60_000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: SecurityException) { }
    }

    suspend fun show(ctx: Context) {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24L * 3600 * 1000 - 1

        val pending: List<Task> = DaniApplication.instance.taskRepository.getAllTasks().first()
            .filter { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.CANCELLED }
        val today = pending.filter { it.dueDate != null && it.dueDate in start..end }.sortedBy { it.dueDate }
        val overdue = pending.count { it.dueDate != null && it.dueDate < start }
        val evs = try { com.dani.assistant.core.events.EventStore.upcoming(ctx, 1) } catch (e: Exception) { emptyList() }
        if (today.isEmpty() && overdue == 0 && evs.isEmpty()) return // ما نزعجوكش بإشعار فارغ

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val lines = ArrayList<String>()
        evs.forEach { u -> lines.add((if (u.event.kind == "birthday") "🎂 " else "🎉 ") + (if (u.daysLeft == 0) "اليوم: " else "غدوة: ") + u.event.name + (if (u.age > 0) " (" + u.age + " سنة)" else "")) }
        today.take(6).forEach { lines.add("• " + fmt.format(Date(it.dueDate!!)) + "  " + it.title) }
        if (today.size > 6) lines.add("… و" + (today.size - 6) + " أخرى")
        if (overdue > 0) lines.add("⚠️ " + overdue + " مهمة متأخرة")
        try {
            val pins = com.dani.assistant.core.notes.NoteStore.list(ctx).filter { it.pinned }
            pins.take(2).forEach { lines.add("📌 " + it.text.replace("\n", " ").take(60)) }
            if (pins.size > 2) lines.add("📌 … و" + (pins.size - 2) + " ملاحظات مثبّتة")
        } catch (e: Exception) { }

        val title = if (today.isNotEmpty()) "☀️ صباح الخير! عندك " + today.size + " مهام اليوم" else "☀️ صباح الخير!"
        val open = PendingIntent.getActivity(
            ctx, REQUEST_CODE, Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_DAILY_TASKS)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) { }
    }
}

class MorningDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (AppSettings.digestEnabled(app)) MorningDigest.show(app)
            } catch (e: Exception) {
            } finally {
                MorningDigest.schedule(app) // تجدول لليوم الجاي
                pending.finish()
            }
        }
    }
}
