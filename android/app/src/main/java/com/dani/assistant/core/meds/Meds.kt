package com.dani.assistant.core.meds

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
import java.util.Locale

data class Med(
    val id: Long,
    val name: String,
    val dose: String = "",
    val times: List<Int> = listOf(8 * 60),   // دقائق من منتصف الليل
    val active: Boolean = true,
    val endAt: Long = 0L,                     // 0 = علاج مستمر
    val createdAt: Long = System.currentTimeMillis()
)

data class Dose(val med: Med, val minute: Int, val taken: Boolean)

fun fmtMinute(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

/** أدوية بمواعيد (ملف dani_meds.json) + سجل الجرعات المأخوذة. */
object MedStore {
    private const val FILE = "dani_meds.json"
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayKey(offset: Int = 0): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        return synchronized(dayFmt) { dayFmt.format(c.time) }
    }

    @Synchronized
    private fun readRoot(ctx: Context): JSONObject {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONObject()
        return try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
    }

    @Synchronized
    private fun writeRoot(ctx: Context, root: JSONObject) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun toJson(m: Med) = JSONObject().put("id", m.id).put("name", m.name).put("dose", m.dose)
        .put("times", JSONArray(m.times)).put("active", m.active).put("end", m.endAt).put("created", m.createdAt)

    private fun fromJson(o: JSONObject): Med {
        val t = o.optJSONArray("times") ?: JSONArray()
        return Med(o.optLong("id"), o.optString("name"), o.optString("dose"), List(t.length()) { t.optInt(it) }.sorted(),
            o.optBoolean("active", true), o.optLong("end", 0L), o.optLong("created", System.currentTimeMillis()))
    }

    fun list(ctx: Context): List<Med> {
        val a = readRoot(ctx).optJSONArray("meds") ?: return emptyList()
        return List(a.length()) { fromJson(a.getJSONObject(it)) }.sortedBy { it.times.firstOrNull() ?: 0 }
    }

    @Synchronized
    fun save(ctx: Context, m: Med) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("meds") ?: JSONArray()
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == m.id) { out.put(toJson(m)); done = true } else out.put(o)
        }
        if (!done) out.put(toJson(m))
        root.put("meds", out)
        writeRoot(ctx, root)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("meds") ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        root.put("meds", out)
        writeRoot(ctx, root)
    }

    private fun logKey(day: String, med: Long, t: Int) = "$day|$med|$t"

    private fun takenSet(ctx: Context): HashSet<String> {
        val a = readRoot(ctx).optJSONArray("log") ?: return HashSet()
        val s = HashSet<String>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { s.add(logKey(it.optString("day"), it.optLong("med"), it.optInt("t"))) }
        return s
    }

    /** مفاتيح السجل "yyyy-MM-dd|medId|minute" (للتصدير). */
    fun takenKeys(ctx: Context): Set<String> = takenSet(ctx)

    fun isTaken(ctx: Context, medId: Long, minute: Int, day: String = dayKey(0)): Boolean =
        takenSet(ctx).contains(logKey(day, medId, minute))

    @Synchronized
    fun mark(ctx: Context, medId: Long, minute: Int, taken: Boolean, day: String = dayKey(0)) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("log") ?: JSONArray()
        val out = JSONArray()
        val key = logKey(day, medId, minute)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (logKey(o.optString("day"), o.optLong("med"), o.optInt("t")) != key) out.put(o)
        }
        if (taken) out.put(JSONObject().put("day", day).put("med", medId).put("t", minute).put("ts", System.currentTimeMillis()))
        root.put("log", out)
        writeRoot(ctx, root)
        try { com.dani.assistant.widget.DailyWidgetProvider.refresh(ctx) } catch (e: Exception) { }
    }

    private fun dayStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun isLive(m: Med): Boolean = m.active && (m.endAt == 0L || m.endAt >= dayStart())

    fun todayDoses(ctx: Context): List<Dose> {
        val taken = takenSet(ctx)
        val day = dayKey(0)
        return list(ctx).filter { isLive(it) }.flatMap { m -> m.times.map { Dose(m, it, taken.contains(logKey(day, m.id, it))) } }
            .sortedBy { it.minute }
    }

    fun nowMinute(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** (المأخوذ، المتوقع) في آخر 7 أيام. اليوم الحالي يحسب الجرعات اللي وقتها فات فقط. */
    fun adherence(ctx: Context): Pair<Int, Int> {
        val taken = takenSet(ctx)
        val meds = list(ctx).filter { it.active }
        var got = 0; var exp = 0
        for (d in 0 downTo -6) {
            val day = dayKey(d)
            val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, d)
            c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59)
            for (m in meds) {
                if (m.createdAt > c.timeInMillis) continue
                if (m.endAt != 0L && d < 0 && m.endAt < c.timeInMillis - 86_400_000L) continue
                for (t in m.times) {
                    if (d == 0 && t > nowMinute()) continue
                    exp++
                    if (taken.contains(logKey(day, m.id, t))) got++
                }
            }
        }
        return got to exp
    }

    fun exportJson(ctx: Context): JSONObject {
        val r = readRoot(ctx)
        return JSONObject().put("meds", r.optJSONArray("meds") ?: JSONArray()).put("log", r.optJSONArray("log") ?: JSONArray())
    }

    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val root = readRoot(ctx)
        var added = 0
        val meds = root.optJSONArray("meds") ?: JSONArray()
        val ids = HashSet<Long>()
        for (i in 0 until meds.length()) meds.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        val incM = incoming.optJSONArray("meds") ?: JSONArray()
        for (i in 0 until incM.length()) {
            val o = incM.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { meds.put(o); added++ }
        }
        val log = root.optJSONArray("log") ?: JSONArray()
        val keys = HashSet<String>()
        for (i in 0 until log.length()) log.optJSONObject(i)?.let { keys.add(logKey(it.optString("day"), it.optLong("med"), it.optInt("t"))) }
        val incL = incoming.optJSONArray("log") ?: JSONArray()
        for (i in 0 until incL.length()) {
            val o = incL.optJSONObject(i) ?: continue
            if (keys.add(logKey(o.optString("day"), o.optLong("med"), o.optInt("t")))) log.put(o)
        }
        if (added > 0 || incL.length() > 0) { root.put("meds", meds).put("log", log); writeRoot(ctx, root) }
        return added
    }
}

/** منبهات الجرعات: منبه دقيق لكل جرعة، يتجدد كل يوم. */
object MedAlarms {
    private const val CHANNEL = "dani_meds"
    const val ACT_DOSE = "dani.med.dose"
    const val ACT_TAKEN = "dani.med.taken"
    const val ACT_SNOOZE = "dani.med.snooze"

    fun code(medId: Long, minute: Int): Int = ((medId xor (medId ushr 32)).toInt() * 31 + minute) and 0x3fffffff

    private fun pi(ctx: Context, medId: Long, minute: Int, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, MedReceiver::class.java).setAction(ACT_DOSE).putExtra("med", medId).putExtra("t", minute).putExtra("snooze", snooze)
        val rc = if (snooze) code(medId, minute) or 0x40000000 else code(medId, minute)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, at: Long, p: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) } catch (e2: Exception) { } }
    }

    fun nextTrigger(minute: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, minute / 60); c.set(Calendar.MINUTE, minute % 60); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun rescheduleAll(ctx: Context) {
        for (m in MedStore.list(ctx)) {
            if (MedStore.isLive(m)) m.times.forEach { setAt(ctx, nextTrigger(it), pi(ctx, m.id, it, false)) }
            else cancelMed(ctx, m)
        }
    }

    fun cancelMed(ctx: Context, m: Med) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        m.times.forEach {
            am.cancel(pi(ctx, m.id, it, false))
            am.cancel(pi(ctx, m.id, it, true))
            cancelNotif(ctx, m.id, it)
        }
    }

    fun cancelNotif(ctx: Context, medId: Long, minute: Int) {
        try { NotificationManagerCompat.from(ctx).cancel(code(medId, minute)) } catch (e: Exception) { }
    }

    fun snooze(ctx: Context, medId: Long, minute: Int, minutes: Int = 10) {
        setAt(ctx, System.currentTimeMillis() + minutes * 60_000L, pi(ctx, medId, minute, true))
    }

    fun reschedule(ctx: Context, m: Med, minute: Int) = setAt(ctx, nextTrigger(minute), pi(ctx, m.id, minute, false))

    fun notify(ctx: Context, m: Med, minute: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(NotificationChannel(CHANNEL, "تذكير الأدوية", NotificationManager.IMPORTANCE_HIGH))
            }
            fun act(a: String, rc: Int, snz: Boolean) = PendingIntent.getBroadcast(
                ctx, rc, Intent(ctx, MedReceiver::class.java).setAction(a).putExtra("med", m.id).putExtra("t", minute).putExtra("snooze", snz),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val base = code(m.id, minute)
            val open = PendingIntent.getActivity(
                ctx, base, Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("💊 وقت الدواء: " + m.name)
                .setContentText((if (m.dose.isNotBlank()) m.dose + " — " else "") + fmtMinute(minute))
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(android.R.drawable.checkbox_on_background, "✔ أخذتو", act(ACT_TAKEN, base + 1, false))
                .addAction(android.R.drawable.ic_popup_sync, "⏰ بعد 10 د", act(ACT_SNOOZE, base + 2, false))
                .build()
            NotificationManagerCompat.from(ctx).notify(base, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
    }
}

class MedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val medId = intent.getLongExtra("med", 0L)
        val t = intent.getIntExtra("t", -1)
        val m = MedStore.list(ctx).firstOrNull { it.id == medId } ?: return
        if (t < 0) return
        when (intent.action) {
            MedAlarms.ACT_TAKEN -> {
                MedStore.mark(ctx, medId, t, true)
                MedAlarms.cancelNotif(ctx, medId, t)
            }
            MedAlarms.ACT_SNOOZE -> {
                MedAlarms.cancelNotif(ctx, medId, t)
                MedAlarms.snooze(ctx, medId, t)
            }
            MedAlarms.ACT_DOSE -> {
                val snooze = intent.getBooleanExtra("snooze", false)
                if (!snooze && MedStore.isLive(m)) MedAlarms.reschedule(ctx, m, t) // جرعة الغد
                if (MedStore.isLive(m) && !MedStore.isTaken(ctx, medId, t)) MedAlarms.notify(ctx, m, t)
            }
        }
    }
}
