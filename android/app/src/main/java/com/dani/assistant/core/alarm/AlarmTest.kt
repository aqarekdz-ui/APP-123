package com.dani.assistant.core.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** تجربة منبه حقيقي بعد دقيقة: يتأكد أن الهاتف يدق المنبهات الدقيقة (حتى والشاشة مطفية). */
object AlarmTest {
    private const val PREFS = "dani_alarm_test"
    private const val CHANNEL = "dani_alarm_test"
    private const val NOTIF_ID = 9600
    const val ACTION = "dani.alarm.test"
    const val DELAY_SEC = 60

    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 9600, Intent(ctx, AlarmTestReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context): Boolean {
        val at = System.currentTimeMillis() + DELAY_SEC * 1000L
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("planned", at).putLong("fired", 0L).apply()
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx)); true }
        catch (e: SecurityException) { false }
    }

    fun onFired(ctx: Context) {
        val now = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fired", now).apply()
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "تجربة المنبه", NotificationManager.IMPORTANCE_HIGH))
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("⏰ المنبه اشتغل")
                .setContentText("المنبهات الدقيقة تخدم على هاتفك.")
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }

    /** نص نتيجة آخر تجربة. */
    fun lastResult(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return describe(p.getLong("planned", 0L), p.getLong("fired", 0L), System.currentTimeMillis())
    }

    /** منطق صافي (يتجرّب بـ JUnit). */
    fun describe(planned: Long, fired: Long, now: Long): String {
        if (planned == 0L) return "ما درت حتى تجربة."
        if (fired > 0L) {
            val late = ((fired - planned) / 1000).toInt()
            return if (late <= 30) "✅ دقّ في وقتو (فرق " + late.coerceAtLeast(0) + " ثانية)."
            else "⚠️ دقّ لكن تأخر " + late + " ثانية — البطارية تأخّر المنبهات."
        }
        return if (now < planned) "⏳ ننتظر المنبه (باقي " + ((planned - now + 999) / 1000) + " ثانية)..."
        else if (now - planned <= 120_000L) "⏳ لسا ما دقش، استنى شوية..."
        else "❌ ما دقش. فعّل الاستثناءات اللي فوق وعاود."
    }
}

class AlarmTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmTest.ACTION) AlarmTest.onFired(context.applicationContext)
    }
}
