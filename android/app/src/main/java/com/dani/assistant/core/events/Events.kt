package com.dani.assistant.core.events

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
import java.util.Calendar
import java.util.Locale

/** مناسبة: عيد ميلاد أو مناسبة عامة. year: للميلاد/الذكرى = سنة البداية (0 = مجهولة)، للمرة الواحدة = سنتها. */
data class LifeEvent(
    val id: Long,
    val name: String,
    val kind: String = "birthday",   // birthday | occasion
    val day: Int,
    val month: Int,                  // 1..12
    val year: Int = 0,
    val yearly: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class Upcoming(val event: LifeEvent, val date: Long, val daysLeft: Int, val age: Int)

fun daysText(d: Int): String = when (d) {
    0 -> "اليوم 🎉"
    1 -> "غدوة"
    2 -> "بعد يومين"
    else -> "باقي " + d + " يوم"
}

fun fmtEventDate(e: LifeEvent): String {
    val base = String.format(Locale.US, "%02d/%02d", e.day, e.month)
    return if (e.year > 0) base + "/" + e.year else base
}

fun eventEmoji(e: LifeEvent): String = if (e.kind == "birthday") "🎂" else "🎉"

/** مناسبات (ملف dani_events.json). */
object EventStore {
    private const val FILE = "dani_events.json"

    @Synchronized
    private fun readRoot(ctx: Context): JSONObject {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONObject()
        return try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
    }

    @Synchronized
    private fun writeRoot(ctx: Context, root: JSONObject) {
        val tmp = File(ctx.filesDir, FILE + ".tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun toJson(e: LifeEvent) = JSONObject().put("id", e.id).put("name", e.name).put("kind", e.kind)
        .put("day", e.day).put("month", e.month).put("year", e.year).put("yearly", e.yearly).put("created", e.createdAt)

    private fun fromJson(o: JSONObject) = LifeEvent(
        o.optLong("id"), o.optString("name"), o.optString("kind", "birthday"),
        o.optInt("day", 1), o.optInt("month", 1), o.optInt("year", 0),
        o.optBoolean("yearly", true), o.optLong("created", System.currentTimeMillis())
    )

    fun list(ctx: Context): List<LifeEvent> {
        val a = readRoot(ctx).optJSONArray("events") ?: return emptyList()
        return List(a.length()) { fromJson(a.getJSONObject(it)) }
    }

    @Synchronized
    fun save(ctx: Context, e: LifeEvent) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("events") ?: JSONArray()
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == e.id) { out.put(toJson(e)); done = true } else out.put(o)
        }
        if (!done) out.put(toJson(e))
        root.put("events", out)
        writeRoot(ctx, root)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("events") ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        root.put("events", out)
        writeRoot(ctx, root)
    }

    // ---------- تواريخ ----------
    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** تاريخ المناسبة في سنة y (29 فيفري ← 28 في السنوات العادية)، منتصف الليل. */
    fun occurrence(e: LifeEvent, y: Int): Calendar {
        val c = Calendar.getInstance()
        c.set(Calendar.YEAR, y)
        c.set(Calendar.MONTH, e.month - 1)
        c.set(Calendar.DAY_OF_MONTH, 1)
        val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, minOf(e.day, max))
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** أقرب تاريخ (اليوم أو بعده)، أو null إذا مناسبة مرة وحدة فاتت. */
    fun nextDate(e: LifeEvent): Long? {
        val today = startOfToday()
        val y = Calendar.getInstance().get(Calendar.YEAR)
        if (!e.yearly) {
            if (e.year <= 0) return null
            val t = occurrence(e, e.year).timeInMillis
            return if (t >= today) t else null
        }
        for (yy in y..(y + 1)) {
            val t = occurrence(e, yy).timeInMillis
            if (t >= today) return t
        }
        return null
    }

    fun ageOn(e: LifeEvent, dateMillis: Long): Int {
        if (!e.yearly || e.year <= 0) return 0
        val c = Calendar.getInstance()
        c.timeInMillis = dateMillis
        val a = c.get(Calendar.YEAR) - e.year
        return if (a in 1..130) a else 0
    }

    fun upcoming(ctx: Context, withinDays: Int = Int.MAX_VALUE): List<Upcoming> {
        val today = startOfToday()
        return list(ctx).mapNotNull { e ->
            val t = nextDate(e) ?: return@mapNotNull null
            val d = Math.round((t - today) / 86_400_000.0).toInt()
            if (d > withinDays) null else Upcoming(e, t, d, ageOn(e, t))
        }.sortedBy { it.date }
    }

    fun past(ctx: Context): List<LifeEvent> =
        list(ctx).filter { nextDate(it) == null }.sortedByDescending { if (it.year > 0) occurrence(it, it.year).timeInMillis else 0L }

    // ---------- نسخ احتياطي ----------
    fun exportJson(ctx: Context): JSONObject {
        val r = readRoot(ctx)
        return JSONObject().put("events", r.optJSONArray("events") ?: JSONArray())
    }

    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("events") ?: JSONArray()
        val ids = HashSet<Long>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        var added = 0
        val inc = incoming.optJSONArray("events") ?: JSONArray()
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { arr.put(o); added++ }
        }
        if (added > 0) { root.put("events", arr); writeRoot(ctx, root) }
        return added
    }
}

/** منبهات المناسبات: k=0 قبل بيوم (09:00)، k=1 في نفس النهار (08:00). تتجدد بعد كل إطلاق. */
object EventAlarms {
    private const val CHANNEL = "dani_events"
    const val ACT = "dani.event.fire"

    fun code(id: Long, k: Int): Int = ((id xor (id ushr 32)).toInt() * 31 + k) and 0x3fffffff

    private fun pi(ctx: Context, id: Long, k: Int): PendingIntent {
        val i = Intent(ctx, EventReceiver::class.java).setAction(ACT).putExtra("ev", id).putExtra("k", k)
        return PendingIntent.getBroadcast(ctx, code(id, k), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, at: Long, p: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) } catch (e2: Exception) { } }
    }

    fun triggerFor(e: LifeEvent, k: Int): Long? {
        val now = System.currentTimeMillis() + 1000
        val y = Calendar.getInstance().get(Calendar.YEAR)
        val years = if (e.yearly) y..(y + 1) else e.year..e.year
        for (yy in years) {
            if (yy <= 0) continue
            val c = EventStore.occurrence(e, yy)
            if (k == 0) c.add(Calendar.DAY_OF_YEAR, -1)
            c.set(Calendar.HOUR_OF_DAY, if (k == 0) 9 else 8)
            c.set(Calendar.MINUTE, 0)
            val t = c.timeInMillis
            if (t > now) return t
        }
        return null
    }

    fun schedule(ctx: Context, e: LifeEvent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (k in 0..1) {
            val t = triggerFor(e, k)
            if (t != null) setAt(ctx, t, pi(ctx, e.id, k)) else am.cancel(pi(ctx, e.id, k))
        }
    }

    fun cancel(ctx: Context, e: LifeEvent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (k in 0..1) {
            am.cancel(pi(ctx, e.id, k))
            try { NotificationManagerCompat.from(ctx).cancel(code(e.id, k)) } catch (ex: Exception) { }
        }
    }

    fun rescheduleAll(ctx: Context) {
        for (e in EventStore.list(ctx)) schedule(ctx, e)
    }

    fun notify(ctx: Context, e: LifeEvent, k: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(NotificationChannel(CHANNEL, "المناسبات وأعياد الميلاد", NotificationManager.IMPORTANCE_HIGH))
            }
            val c = Calendar.getInstance()
            if (k == 0) c.add(Calendar.DAY_OF_YEAR, 1)
            val age = EventStore.ageOn(e, c.timeInMillis)
            val when0 = if (k == 0) "غدوة" else "اليوم"
            val title = if (e.kind == "birthday") "🎂 " + when0 + " عيد ميلاد " + e.name else "🎉 " + when0 + ": " + e.name
            val text = if (age > 0) "يكمل " + age + " سنة — ما تنساش 🎁" else "ما تنساش 😉"
            val open = PendingIntent.getActivity(
                ctx, code(e.id, k), Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(code(e.id, k), n)
        } catch (ex: SecurityException) { } catch (ex: Exception) { }
    }
}

class EventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val id = intent.getLongExtra("ev", 0L)
        val k = intent.getIntExtra("k", 1)
        val e = EventStore.list(ctx).firstOrNull { it.id == id } ?: return
        EventAlarms.notify(ctx, e, k)
        EventAlarms.schedule(ctx, e) // السنة الجاية (أو إلغاء إذا مرة وحدة)
    }
}
