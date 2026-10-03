package com.dani.assistant.core.realestate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class REClient(
    val id: Long,
    val name: String,
    val phone: String = "",
    val wants: String = "شراء",
    val budget: String = "",
    val area: String = "",
    val notes: String = "",
    val status: String = "جديد",
    val nextFollowUp: Long? = null,
    val taskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class REProperty(
    val id: Long,
    val title: String,
    val deal: String = "بيع",
    val kind: String = "شقة",
    val area: String = "",
    val price: String = "",
    val size: String = "",
    val rooms: String = "",
    val notes: String = "",
    val status: String = "متاح",
    val createdAt: Long = System.currentTimeMillis()
)

/** ملف محلي خاص (filesDir/dani_realestate.json): عملاء وعقارات. */
object RealEstateStore {
    private const val FILE = "dani_realestate.json"
    private const val PREFS = "dani_realestate_prefs"

    val clientStatuses = listOf("جديد", "مهتم", "زيارة", "تفاوض", "تم", "خسارة")
    val propertyStatuses = listOf("متاح", "محجوز", "مباع/مؤجر")
    val deals = listOf("بيع", "كراء")
    val kinds = listOf("شقة", "فيلا", "أرض", "محل", "أخرى")
    val wantsList = listOf("شراء", "كراء")

    // ---------- file ----------
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

    private fun JSONObject.longOrNull(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

    private fun clientToJson(c: REClient): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("phone", c.phone).put("wants", c.wants)
        .put("budget", c.budget).put("area", c.area).put("notes", c.notes).put("status", c.status)
        .put("next", c.nextFollowUp ?: JSONObject.NULL).put("task", c.taskId ?: JSONObject.NULL)
        .put("created", c.createdAt)

    private fun clientFromJson(o: JSONObject): REClient = REClient(
        id = o.optLong("id"), name = o.optString("name"), phone = o.optString("phone"),
        wants = o.optString("wants", "شراء"), budget = o.optString("budget"), area = o.optString("area"),
        notes = o.optString("notes"), status = o.optString("status", "جديد"),
        nextFollowUp = o.longOrNull("next"), taskId = o.longOrNull("task"),
        createdAt = o.optLong("created", System.currentTimeMillis())
    )

    private fun propToJson(p: REProperty): JSONObject = JSONObject()
        .put("id", p.id).put("title", p.title).put("deal", p.deal).put("kind", p.kind)
        .put("area", p.area).put("price", p.price).put("size", p.size).put("rooms", p.rooms)
        .put("notes", p.notes).put("status", p.status).put("created", p.createdAt)

    private fun propFromJson(o: JSONObject): REProperty = REProperty(
        id = o.optLong("id"), title = o.optString("title"), deal = o.optString("deal", "بيع"),
        kind = o.optString("kind", "شقة"), area = o.optString("area"), price = o.optString("price"),
        size = o.optString("size"), rooms = o.optString("rooms"), notes = o.optString("notes"),
        status = o.optString("status", "متاح"), createdAt = o.optLong("created", System.currentTimeMillis())
    )

    private fun upsert(arr: JSONArray?, id: Long, obj: JSONObject): JSONArray {
        val out = JSONArray()
        var done = false
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optLong("id") == id) { out.put(obj); done = true } else out.put(o)
            }
        }
        if (!done) out.put(obj)
        return out
    }

    private fun without(arr: JSONArray?, id: Long): JSONArray {
        val out = JSONArray()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optLong("id") != id) out.put(o)
            }
        }
        return out
    }

    // ---------- clients ----------
    fun clients(ctx: Context): List<REClient> {
        val arr = readRoot(ctx).optJSONArray("clients") ?: return emptyList()
        return List(arr.length()) { clientFromJson(arr.getJSONObject(it)) }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun saveClient(ctx: Context, c: REClient) {
        val root = readRoot(ctx)
        root.put("clients", upsert(root.optJSONArray("clients"), c.id, clientToJson(c)))
        writeRoot(ctx, root)
    }

    @Synchronized
    fun deleteClient(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        root.put("clients", without(root.optJSONArray("clients"), id))
        writeRoot(ctx, root)
    }

    // ---------- properties ----------
    fun properties(ctx: Context): List<REProperty> {
        val arr = readRoot(ctx).optJSONArray("properties") ?: return emptyList()
        return List(arr.length()) { propFromJson(arr.getJSONObject(it)) }.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun saveProperty(ctx: Context, p: REProperty) {
        val root = readRoot(ctx)
        root.put("properties", upsert(root.optJSONArray("properties"), p.id, propToJson(p)))
        writeRoot(ctx, root)
    }

    @Synchronized
    fun deleteProperty(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        root.put("properties", without(root.optJSONArray("properties"), id))
        writeRoot(ctx, root)
    }

    // ---------- contact line for ads ----------
    fun contact(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("contact", "") ?: ""
    fun setContact(ctx: Context, v: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("contact", v).apply()
    }

    // ---------- backup ----------
    fun exportJson(ctx: Context): JSONObject {
        val root = readRoot(ctx)
        return JSONObject()
            .put("clients", root.optJSONArray("clients") ?: JSONArray())
            .put("properties", root.optJSONArray("properties") ?: JSONArray())
    }

    /** دمج (لا يمسح شيء): يضيف فقط العناصر اللي id تاعها غير موجود. يرجع عدد المضاف. */
    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val root = readRoot(ctx)
        var added = 0
        for (name in listOf("clients", "properties")) {
            val cur = root.optJSONArray(name) ?: JSONArray()
            val ids = HashSet<Long>()
            for (i in 0 until cur.length()) cur.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
            val inc = incoming.optJSONArray(name) ?: continue
            for (i in 0 until inc.length()) {
                val o = inc.optJSONObject(i) ?: continue
                if (ids.add(o.optLong("id"))) { cur.put(o); added++ }
            }
            root.put(name, cur)
        }
        if (added > 0) writeRoot(ctx, root)
        return added
    }
}
