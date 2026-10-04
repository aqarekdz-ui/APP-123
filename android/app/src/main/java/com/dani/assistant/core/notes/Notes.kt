package com.dani.assistant.core.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(
    val id: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pinned: Boolean = false
)

/** دفتر ملاحظات (ملف dani_notes.json). */
object NoteStore {
    private const val FILE = "dani_notes.json"

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

    private fun toJson(n: Note) = JSONObject().put("id", n.id).put("text", n.text)
        .put("created", n.createdAt).put("updated", n.updatedAt).put("pinned", n.pinned)

    private fun fromJson(o: JSONObject): Note {
        val c = o.optLong("created", System.currentTimeMillis())
        return Note(o.optLong("id"), o.optString("text"), c, o.optLong("updated", c), o.optBoolean("pinned", false))
    }

    /** المثبّتة أولاً ثم الأحدث. */
    fun list(ctx: Context): List<Note> {
        val a = readRoot(ctx).optJSONArray("notes") ?: return emptyList()
        return List(a.length()) { fromJson(a.getJSONObject(it)) }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }

    fun search(ctx: Context, q: String): List<Note> {
        val k = q.trim().lowercase()
        if (k.isEmpty()) return list(ctx)
        return list(ctx).filter { it.text.lowercase().contains(k) }
    }

    @Synchronized
    fun save(ctx: Context, n: Note) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("notes") ?: JSONArray()
        val out = JSONArray()
        var done = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") == n.id) { out.put(toJson(n)); done = true } else out.put(o)
        }
        if (!done) out.put(toJson(n))
        root.put("notes", out)
        writeRoot(ctx, root)
    }

    fun add(ctx: Context, text: String): Note {
        val n = Note(System.currentTimeMillis(), text.trim())
        save(ctx, n)
        return n
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("notes") ?: return
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optLong("id") != id) out.put(o)
        }
        root.put("notes", out)
        writeRoot(ctx, root)
    }

    fun latest(ctx: Context): Note? = list(ctx).maxByOrNull { it.createdAt }

    fun exportJson(ctx: Context): JSONObject =
        JSONObject().put("notes", readRoot(ctx).optJSONArray("notes") ?: JSONArray())

    @Synchronized
    fun importJson(ctx: Context, incoming: JSONObject): Int {
        val root = readRoot(ctx)
        val arr = root.optJSONArray("notes") ?: JSONArray()
        val ids = HashSet<Long>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { ids.add(it.optLong("id")) }
        var added = 0
        val inc = incoming.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until inc.length()) {
            val o = inc.optJSONObject(i) ?: continue
            if (ids.add(o.optLong("id"))) { arr.put(o); added++ }
        }
        if (added > 0) { root.put("notes", arr); writeRoot(ctx, root) }
        return added
    }
}

/**
 * أوامر الملاحظات بالكلام (محلي):
 *  "ملاحظة: كلم المحامي غدوة" | "دوّن عندي موعد..." | "ملاحظاتي" | "لقى ملاحظة المحامي" | "الغي آخر ملاحظة"
 */
object NoteCommands {
    private val addRe = Regex("^(?:(?:زيد|ضيف|سجل|اكتب|دير)\\s+)?(?:ملاحظه|دون|note)(?:\\s*[:：]\\s*|\\s+)(.{2,})$")
    private val searchRe = Regex("(?:لقي|ابحث|دور|فتش)\\s+(?:في\\s+)?(?:ملاحظاتي|ملاحظه|ملاحظات)\\s*(?:على|عن|فيها|ب)?\\s*(.+)")

    private fun has(n: String, vararg w: String) = w.any { n.contains(it) }

    private fun fmt(n: Note): String =
        (if (n.pinned) "📌 " else "📝 ") + n.text.replace("\n", " ").take(80) + (if (n.text.length > 80) "…" else "")

    fun handle(ctx: Context, s: String, n: String): String? {
        if (!has(n, "ملاحظ", "دون", "note")) return null

        if (has(n, "الغي", "امسح", "احذف", "نحي") && has(n, "اخر ملاحظه")) {
            val l = NoteStore.latest(ctx) ?: return "ما عندكش ملاحظات."
            NoteStore.delete(ctx, l.id)
            return "🗑 لغيت آخر ملاحظة: " + fmt(l)
        }
        searchRe.find(n)?.let { m ->
            val q = m.groupValues[1].trim()
            val hits = NoteStore.search(ctx, q)
            if (hits.isEmpty()) return "ما لقيتش ملاحظة فيها \"" + q + "\"."
            return "🔎 لقيت " + hits.size + ":\n" + hits.take(6).joinToString("\n") { fmt(it) }
        }
        if (has(n, "ملاحظاتي", "دفتر الملاحظات", "اخر الملاحظات", "اخر ملاحظات")) {
            val all = NoteStore.list(ctx)
            if (all.isEmpty()) return "📝 ما عندكش ملاحظات. قول: \"ملاحظة: ...\" ولا افتح 📝 ملاحظات من الرئيسية."
            return "📝 ملاحظاتك (" + all.size + "):\n" + all.take(6).joinToString("\n") { fmt(it) }
        }
        val m = addRe.find(n) ?: return null
        val src = if (s.length == n.length) s else n
        val text = src.substring(m.groups[1]!!.range.first).trim()
        if (text.length < 2) return null
        val note = NoteStore.add(ctx, text)
        return "📝 دوّنت: " + note.text.take(100) + (if (note.text.length > 100) "…" else "")
    }
}
