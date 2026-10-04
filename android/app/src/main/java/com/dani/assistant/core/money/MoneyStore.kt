package com.dani.assistant.core.money

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class MoneyEntry(
    val id: Long,
    val type: String,            // expense | income | debt_to_me | debt_i_owe
    val amount: Long,            // بالدينار
    val category: String = "",
    val note: String = "",
    val person: String = "",     // للديون
    val date: Long = System.currentTimeMillis(),
    val settled: Boolean = false,
    val taskId: Long? = null     // مهمة التذكير المرتبطة بالدين
)

/** المصاريف والمداخيل والديون (ملف محلي dani_money.json). */
object MoneyStore {
    private const val FILE = "dani_money.json"

    val expenseCategories = listOf("أكل", "مواصلات", "فواتير", "صحة", "ترفيه", "شغل", "بيت", "أخرى")
    val incomeCategories = listOf("راتب", "شغل", "مبيعات", "أخرى")

    fun fmt(n: Long): String = String.format(Locale.US, "%,d", n) + " دج"

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

    private fun toJson(e: MoneyEntry): JSONObject = JSONObject()
        .put("id", e.id).put("type", e.type).put("amount", e.amount).put("category", e.category)
        .put("note", e.note).put("person", e.person).put("date", e.date).put("settled", e.settled)
        .put("task", e.taskId ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): MoneyEntry = MoneyEntry(
        id = o.optLong("id"), type = o.optString("type", "expense"), amount = o.optLong("amount"),
        category = o.optString("category"), note = o.optString("note"), person = o.optString("person"),
        date = o.optLong("date", System.currentTimeMillis()), settled = o.optBoolean("settled", false),
        taskId = if (o.has("task") && !o.isNull("task")) o.getLong("task") else null
    )

    fun entries(ctx: Context): List<MoneyEntry> {
        val arr = readRoot(ctx).optJSONArray("entries") ?: return emptyList()
        return List(arr.length()) { fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.date }
    }

    @Synchronized
    fun save(ctx: Context, e: MoneyEntry) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("entries") ?: JSONArray()
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == e.id) { out.put(toJson(e)); done = true } else out.put(o)
        }
        if (!done) out.put(toJson(e))
        root.put("entries", out)
        writeRoot(ctx, root)
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("entries") ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        root.put("entries", out)
        writeRoot(ctx, root)
    }

    // ---------------- الميزانية الشهرية لكل فئة ----------------
    fun budgets(ctx: Context): Map<String, Long> {
        val o = readRoot(ctx).optJSONObject("budgets") ?: return emptyMap()
        val m = LinkedHashMap<String, Long>()
        for (k in o.keys()) { val v = o.optLong(k); if (v > 0) m[k] = v }
        return m
    }

    @Synchronized
    fun setBudget(ctx: Context, category: String, amount: Long) {
        val root = readRoot(ctx)
        val o = root.optJSONObject("budgets") ?: JSONObject()
        if (amount > 0) o.put(category, amount) else o.remove(category)
        root.put("budgets", o)
        writeRoot(ctx, root)
    }

    private fun thisMonthStart(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun spentThisMonth(ctx: Context, category: String): Long {
        val start = thisMonthStart()
        return entries(ctx).filter { it.type == "expense" && it.date >= start && it.category.ifBlank { "أخرى" } == category }
            .sumOf { it.amount }
    }

    fun exportJson(ctx: Context): JSONObject {
        val root = readRoot(ctx)
        return JSONObject().put("entries", root.optJSONArray("entries") ?: JSONArray())
            .put("budgets", root.optJSONObject("budgets") ?: JSONObject())
    }

    /** دمج: يضيف فقط العمليات اللي id تاعها غير موجود. */
    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val root = readRoot(ctx)
        var added = 0
        // الميزانيات: دمج بلا مسح (الموجود عندنا يبقى)
        val incB = incoming.optJSONObject("budgets")
        if (incB != null) {
            val curB = root.optJSONObject("budgets") ?: JSONObject()
            for (k in incB.keys()) {
                if (!curB.has(k) && incB.optLong(k) > 0) { curB.put(k, incB.optLong(k)); added++ }
            }
            root.put("budgets", curB)
        }
        val inc = incoming.optJSONArray("entries") ?: JSONArray()
        val cur = root.optJSONArray("entries") ?: JSONArray()
        val ids = HashSet<Long>()
        for (i in 0 until cur.length()) cur.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { cur.put(o); added++ }
        }
        if (added > 0) { root.put("entries", cur); writeRoot(ctx, root) }
        return added
    }
}
