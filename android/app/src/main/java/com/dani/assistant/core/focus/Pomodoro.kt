package com.dani.assistant.core.focus

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
import com.dani.assistant.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class FocusSession(val id: Long, val end: Long, val minutes: Int, val taskTitle: String = "")

data class PomodoroState(
    val phase: String,        // idle | focus | break
    val endAt: Long,
    val taskId: Long,
    val taskTitle: String,
    val focusMin: Int,
    val cycle: Int
)

/** سجل جلسات التركيز (ملف dani_focus.json). */
object FocusLog {
    private const val FILE = "dani_focus.json"

    @Synchronized
    private fun readArr(ctx: Context): JSONArray {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONArray()
        return try { JSONObject(f.readText()).optJSONArray("sessions") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    }

    @Synchronized
    private fun writeArr(ctx: Context, arr: JSONArray) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("sessions", arr).toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    fun list(ctx: Context): List<FocusSession> {
        val a = readArr(ctx)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            FocusSession(o.optLong("id"), o.optLong("end"), o.optInt("min"), o.optString("task"))
        }
    }

    @Synchronized
    fun add(ctx: Context, s: FocusSession) {
        val arr = readArr(ctx)
        arr.put(JSONObject().put("id", s.id).put("end", s.end).put("min", s.minutes).put("task", s.taskTitle))
        writeArr(ctx, arr)
    }

    private fun dayStart(offset: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** (عدد الجلسات، مجموع الدقائق) من offsetFrom إلى اليوم. */
    fun stats(ctx: Context, daysBack: Int): Pair<Int, Int> {
        val from = dayStart(-daysBack)
        val l = list(ctx).filter { it.end >= from }
        return l.size to l.sumOf { it.minutes }
    }

    fun exportJson(ctx: Context): JSONObject = JSONObject().put("sessions", readArr(ctx))

    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val inc = incoming.optJSONArray("sessions") ?: return 0
        val cur = readArr(ctx)
        val ids = HashSet<Long>()
        for (i in 0 until cur.length()) cur.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        var added = 0
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { cur.put(o); added++ }
        }
        if (added > 0) writeArr(ctx, cur)
        return added
    }
}

/** مؤقت Pomodoro: تركيز ← راحة (5د، وكل 4 جلسات 15د). العدّ التنازلي في إشعار مستمر، والمنبه عبر AlarmManager. */
object PomodoroTimer {
    private const val PREFS = "dani_focus_state"
    private const val RUN_CHANNEL = "dani_focus_run"
    private const val ALERT_CHANNEL = "dani_focus_alert"
    private const val NOTIF_ID = 9500
    private const val REQ = 7301

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(ctx: Context): PomodoroState {
        val s = p(ctx)
        return PomodoroState(
            s.getString("phase", "idle") ?: "idle", s.getLong("end", 0L), s.getLong("task_id", 0L),
            s.getString("task_title", "") ?: "", s.getInt("focus_min", 25), s.getInt("cycle", 0)
        )
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, REQ, Intent(ctx, PomodoroReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun arm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx)) } catch (e: SecurityException) { }
    }

    private fun setPhase(ctx: Context, phase: String, endAt: Long) {
        p(ctx).edit().putString("phase", phase).putLong("end", endAt).apply()
    }

    fun startFocus(ctx: Context, minutes: Int, taskId: Long = 0L, taskTitle: String = "") {
        val end = System.currentTimeMillis() + minutes * 60_000L
        p(ctx).edit().putString("phase", "focus").putLong("end", end).putLong("task_id", taskId)
            .putString("task_title", taskTitle).putInt("focus_min", minutes).apply()
        arm(ctx, end)
        showRunning(ctx, "🍅 تركيز" + (if (taskTitle.isNotBlank()) ": " + taskTitle.take(40) else ""), end, alert = false)
    }

    fun stop(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        setPhase(ctx, "idle", 0L)
        try { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) } catch (e: Exception) { }
    }

    /** يمر للمرحلة الجاية (تستعملو المنبه، أو الشاشة إذا الوقت فات). */
    fun advance(ctx: Context) {
        val st = state(ctx)
        val now = System.currentTimeMillis()
        when (st.phase) {
            "focus" -> {
                FocusLog.add(ctx, FocusSession(now, now, st.focusMin, st.taskTitle))
                val cycle = st.cycle + 1
                val br = if (cycle % 4 == 0) 15 else 5
                val end = now + br * 60_000L
                p(ctx).edit().putInt("cycle", cycle).apply()
                setPhase(ctx, "break", end)
                arm(ctx, end)
                showRunning(ctx, "🎉 خلصت جلسة تركيز! ارتاح " + br + " دقائق" + (if (br == 15) " (راحة طويلة)" else ""), end, alert = true)
            }
            "break" -> {
                setPhase(ctx, "idle", 0L)
                showDone(ctx, "☕ خلصت الراحة", "جاهز لجلسة تركيز جديدة؟ افتح DANI ← ⏱ تركيز.")
            }
        }
    }

    fun advanceIfDue(ctx: Context) {
        val st = state(ctx)
        if (st.phase != "idle" && st.endAt in 1..System.currentTimeMillis()) advance(ctx)
    }

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(RUN_CHANNEL, "مؤقت التركيز (جاري)", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "انتهاء جلسة التركيز", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, REQ, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun showRunning(ctx: Context, title: String, endAt: Long, alert: Boolean) {
        try {
            ensureChannels(ctx)
            val b = NotificationCompat.Builder(ctx, if (alert) ALERT_CHANNEL else RUN_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentIntent(openApp(ctx))
                .setOngoing(true)
                .setOnlyAlertOnce(!alert)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(endAt)
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build())
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }

    private fun showDone(ctx: Context, title: String, body: String) {
        try {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title).setContentText(body)
                .setContentIntent(openApp(ctx)).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }

    fun summaryToday(ctx: Context): String {
        val (n, m) = FocusLog.stats(ctx, 0)
        return if (n == 0) "ما ركزتش اليوم بعد." else "🍅 اليوم: " + n + " جلسات، " + (m / 60) + "س " + (m % 60) + "د تركيز"
    }
}

class PomodoroReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try { PomodoroTimer.advance(context.applicationContext) } catch (e: Exception) { }
    }
}
