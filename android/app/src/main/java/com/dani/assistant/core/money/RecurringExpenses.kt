package com.dani.assistant.core.money

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class RecurringItem(
    val id: Long,
    val name: String,
    val amount: Long,
    val category: String = "أخرى",
    val day: Int = 1,           // يوم الشهر 1..28
    val active: Boolean = true,
    val last: Int = -1          // آخر شهر اتسجل (سنة*12+شهر)
)

/** مصاريف ثابتة (كراء، فواتير، اشتراكات): تتسجل لوحدها كل شهر في اليوم المحدد (ملف dani_recurring.json). */
object RecurringExpenses {
    private const val FILE = "dani_recurring.json"
    private const val WORK = "dani_recurring_work"
    private const val CHANNEL = "dani_recurring"

    private fun monthIdx(c: Calendar): Int = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)

    @Synchronized
    private fun readArr(ctx: Context): JSONArray {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return JSONArray()
        return try { JSONObject(f.readText()).optJSONArray("items") ?: JSONArray() } catch (e: Exception) { JSONArray() }
    }

    @Synchronized
    private fun writeArr(ctx: Context, arr: JSONArray) {
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("items", arr).toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    private fun toJson(i: RecurringItem) = JSONObject().put("id", i.id).put("name", i.name).put("amount", i.amount)
        .put("category", i.category).put("day", i.day).put("active", i.active).put("last", i.last)

    private fun fromJson(o: JSONObject) = RecurringItem(
        id = o.optLong("id"), name = o.optString("name"), amount = o.optLong("amount"),
        category = o.optString("category", "أخرى"), day = o.optInt("day", 1).coerceIn(1, 28),
        active = o.optBoolean("active", true), last = o.optInt("last", -1)
    )

    fun list(ctx: Context): List<RecurringItem> {
        val a = readArr(ctx)
        return List(a.length()) { fromJson(a.getJSONObject(it)) }.sortedBy { it.day }
    }

    /** إضافة/تعديل. الجديد ما يسجلش بأثر رجعي: إذا اليوم فات هذا الشهر، يبدأ من الشهر الجاي. */
    @Synchronized
    fun save(ctx: Context, item: RecurringItem) {
        val now = Calendar.getInstance()
        val cur = monthIdx(now)
        val arr = readArr(ctx)
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == item.id) { out.put(toJson(item)); done = true } else out.put(o)
        }
        if (!done) {
            val fresh = if (item.id == 0L) item.copy(id = System.currentTimeMillis()) else item
            val last = RecurrenceMath.initialLast(cur, now.get(Calendar.DAY_OF_MONTH), fresh.day)
            out.put(toJson(fresh.copy(last = last)))
        }
        writeArr(ctx, out)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val arr = readArr(ctx)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        writeArr(ctx, out)
    }

    /** يسجل المستحق (مع تعويض الأشهر الفايتة، حتى 6). يرجع العمليات المسجلة. */
    @Synchronized
    fun runDue(ctx: Context): List<MoneyEntry> {
        val now = Calendar.getInstance()
        val cur = monthIdx(now)
        val today = now.get(Calendar.DAY_OF_MONTH)
        val arr = readArr(ctx)
        val created = ArrayList<MoneyEntry>()
        var changed = false
        val base = System.currentTimeMillis()
        var k = 0L
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val it = fromJson(o)
            if (!it.active || it.amount <= 0) continue
            var last = it.last
            for (idx in RecurrenceMath.dueMonths(last, cur, today, it.day)) {
                val c = Calendar.getInstance()
                c.clear()
                c.set(idx / 12, idx % 12, it.day, 9, 0, 0)
                val e = MoneyEntry(id = base + (k++), type = "expense", amount = it.amount, category = it.category,
                    note = "🔁 " + it.name, date = c.timeInMillis)
                MoneyStore.save(ctx, e)
                created.add(e)
                last = idx
            }
            if (last != it.last) { o.put("last", last); changed = true }
        }
        if (changed) writeArr(ctx, arr)
        return created
    }

    /** يشغّل runDue ويرسل إشعار + فحص الميزانية. */
    fun runAndNotify(ctx: Context): Int {
        val made = runDue(ctx)
        if (made.isEmpty()) return 0
        for (cat in made.map { it.category }.distinct()) BudgetAlerts.check(ctx, cat)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "مصاريف ثابتة", NotificationManager.IMPORTANCE_DEFAULT))
            val body = made.joinToString("\n") { "• " + it.note.removePrefix("🔁 ") + " — " + MoneyStore.fmt(it.amount) }
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("🔁 سجلت " + made.size + " مصروف ثابت")
                .setContentText(body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(9300, n)
        } catch (e: SecurityException) { } catch (e: Exception) { }
        return made.size
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun exportJson(ctx: Context): JSONObject = JSONObject().put("items", readArr(ctx))

    /** دمج: يضيف فقط اللي id تاعها غير موجود. */
    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val inc = incoming.optJSONArray("items") ?: return 0
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

class RecurringWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try { RecurringExpenses.runAndNotify(applicationContext) } catch (e: Exception) { }
        return Result.success()
    }
}
