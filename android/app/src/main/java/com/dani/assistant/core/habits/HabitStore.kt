package com.dani.assistant.core.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class Habit(val id: Long, val name: String, val emoji: String, val createdAt: Long, val days: Set<String>)

/** عادات يومية (ملف محلي dani_habits.json): كل عادة فيها قائمة الأيام اللي تمت فيها. */
object HabitStore {
    private const val FILE = "dani_habits.json"
    val emojis = listOf("💧", "🏃", "📖", "🧘", "💊", "😴", "🥗", "✍️", "🦷", "📵")

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** مفتاح اليوم: offset 0 = اليوم، -1 = البارح... */
    @Synchronized
    fun dayKey(offset: Int = 0): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        return fmt.format(c.time)
    }

    fun streak(h: Habit): Int {
        var offset = if (h.days.contains(dayKey(0))) 0 else -1
        var n = 0
        while (h.days.contains(dayKey(offset))) { n++; offset-- }
        return n
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

    private fun fromJson(o: JSONObject): Habit {
        val d = o.optJSONArray("days") ?: JSONArray()
        return Habit(
            o.optLong("id"), o.optString("name"), o.optString("emoji", "✅"), o.optLong("created"),
            HashSet<String>().also { s -> for (i in 0 until d.length()) s.add(d.optString(i)) }
        )
    }

    fun list(ctx: Context): List<Habit> {
        val arr = readRoot(ctx).optJSONArray("habits") ?: return emptyList()
        return List(arr.length()) { fromJson(arr.getJSONObject(it)) }.sortedBy { it.createdAt }
    }

    @Synchronized
    fun add(ctx: Context, name: String, emoji: String) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("habits") ?: JSONArray()
        val now = System.currentTimeMillis()
        arr.put(JSONObject().put("id", now).put("name", name.trim()).put("emoji", emoji).put("created", now).put("days", JSONArray()))
        root.put("habits", arr)
        writeRoot(ctx, root)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("habits") ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        root.put("habits", out)
        writeRoot(ctx, root)
    }

    @Synchronized
    fun toggle(ctx: Context, id: Long, day: String) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("habits") ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) continue
            val days = HashSet<String>()
            val d = o.optJSONArray("days") ?: JSONArray()
            for (k in 0 until d.length()) days.add(d.optString(k))
            if (!days.remove(day)) days.add(day)
            o.put("days", JSONArray(days.sorted()))
        }
        writeRoot(ctx, root)
    }

    fun exportJson(ctx: Context): JSONObject = JSONObject().put("habits", readRoot(ctx).optJSONArray("habits") ?: JSONArray())

    /** دمج: عادة جديدة تتزاد، وعادة موجودة (نفس id) تتوحد أيامها. يرجع عدد العادات المضافة. */
    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val inc = incoming.optJSONArray("habits") ?: return 0
        val root = readRoot(ctx)
        val cur = root.optJSONArray("habits") ?: JSONArray()
        val byId = HashMap<Long, JSONObject>()
        for (i in 0 until cur.length()) cur.optJSONObject(i)?.let { byId[it.optLong("id")] = it }
        var added = 0
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            val ex = byId[o.optLong("id")]
            if (ex == null) {
                cur.put(o); byId[o.optLong("id")] = o; added++
            } else {
                val days = HashSet<String>()
                for (src in listOf(ex.optJSONArray("days"), o.optJSONArray("days"))) {
                    if (src != null) for (k in 0 until src.length()) days.add(src.optString(k))
                }
                ex.put("days", JSONArray(days.sorted()))
            }
        }
        root.put("habits", cur)
        writeRoot(ctx, root)
        return added
    }
}
