package com.dani.assistant.core.knowledge

import android.content.Context
import com.dani.assistant.core.memory.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class KnowledgeEntry(
    val id: Long,
    val question: String,
    val key: String,
    val answer: String,
    val hits: Int,
    val createdAt: Long
)

/**
 * DANI's private local knowledge file (filesDir/dani_knowledge.json).
 * Local-first: DANI looks here before calling Gemini, and saves new Gemini answers here.
 */
object KnowledgeBase {
    private const val FILE = "dani_knowledge.json"
    private const val MAX_ENTRIES = 300
    private const val MATCH_THRESHOLD = 0.8
    private const val SECRET_THRESHOLD = 0.75
    private const val TTL_MS = 60L * 24 * 3600 * 1000 // entries older than 60 days are re-asked

    private val stopwords = setOf(
        "وش", "واش", "ايش", "شنو", "كيفاش", "كيف", "علاش", "لماذا", "فين", "وين", "اين", "متي", "وقتاش",
        "هي", "هو", "ماهي", "ماهو", "في", "من", "الي", "علي", "عن", "ما", "لي", "تاع", "انت",
        "the", "is", "a", "an", "of", "what", "how", "to"
    )
    private val questionWords = setOf("وش", "واش", "ايش", "شنو", "ماهي", "ماهو", "كم", "عطيني", "اعطيني", "قولي", "what", "quel", "quelle", "donne")
    // Time-sensitive, personal or context-dependent words: never cached
    private val volatileWords = setOf(
        "اليوم", "دوك", "الان", "غدا", "غدوه", "امس", "البارح", "طقس", "سعر", "اسعار", "اخبار", "نتيجه", "مباراه",
        "today", "now", "weather", "price", "news", "score",
        "انا", "عندي", "نحب", "تاعي", "تاعتي", "موعدي", "مهام", "مهمه", "تذكير", "ذكرني",
        "هاذا", "هاذي", "هذا", "هذه", "هادي", "كمل", "زيد", "نفس"
    )

    private val diacritics = Regex("[\u064B-\u0652\u0640]")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val suffixes = listOf("ها", "هم", "نا", "تي", "ي", "ك", "ه")

    private fun normalize(t: String): String = t.lowercase().replace(diacritics, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')

    private fun rawTokens(t: String): List<String> = normalize(t).split(nonWord).filter { it.isNotBlank() }

    private fun stem(w: String): String {
        var s = w
        if (s.startsWith("ال") && s.length > 4) s = s.substring(2)
        for (suf in suffixes) {
            if (s.endsWith(suf) && s.length - suf.length >= 3) { s = s.dropLast(suf.length); break }
        }
        return s
    }

    private fun tokens(t: String): List<String> = rawTokens(t).filter { it !in stopwords }.map { stem(it) }

    fun keyOf(text: String): String = tokens(text).sorted().joinToString(" ")

    private fun isQuestion(text: String): Boolean =
        text.contains('?') || text.contains('؟') || rawTokens(text).any { it in questionWords }

    fun cacheable(question: String, answer: String): Boolean {
        if (question.length > 200) return false
        if (rawTokens(question).any { it in volatileWords }) return false
        if (tokens(question).size < 2) return false
        if (SecretStore.looksSensitive(question)) return false
        val a = answer.trim()
        return a.length in 10..1500 && a != "Error" && a != "..."
    }

    @Synchronized
    private fun load(ctx: Context): MutableList<KnowledgeEntry> {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) {
                val o = arr.getJSONObject(it)
                KnowledgeEntry(o.getLong("id"), o.getString("q"), o.getString("k"), o.getString("a"), o.optInt("h", 0), o.optLong("t", 0L))
            }
        } catch (e: Exception) { mutableListOf() }
    }

    @Synchronized
    private fun save(ctx: Context, list: List<KnowledgeEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("q", it.question).put("k", it.key).put("a", it.answer).put("h", it.hits).put("t", it.createdAt))
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }

    /** Returns a locally known answer, or null if Gemini is needed. */
    fun findLocal(ctx: Context, query: String): String? {
        val qt = tokens(query).toSet()
        if (qt.size < 2) return null

        // Secrets: answered fully on-device, only for explicit questions
        if (SecretStore.looksSensitive(query) && isQuestion(query)) {
            var best: String? = null
            var bestScore = 0.0
            for (s in SecretStore.getAll(ctx)) {
                val inter = qt.intersect(tokens(s).toSet()).size
                if (inter >= 2) {
                    val score = inter.toDouble() / qt.size
                    if (score > bestScore) { bestScore = score; best = s }
                }
            }
            if (best != null && bestScore >= SECRET_THRESHOLD) return best
        }

        val list = load(ctx)
        val now = System.currentTimeMillis()
        var bestIdx = -1
        var bestScore = 0.0
        list.forEachIndexed { i, e ->
            if (now - e.createdAt > TTL_MS) return@forEachIndexed
            val et = e.key.split(" ").filter { it.isNotBlank() }.toSet()
            val inter = qt.intersect(et).size
            if (inter >= 2) {
                val score = inter.toDouble() / maxOf(qt.size, et.size)
                if (score > bestScore) { bestScore = score; bestIdx = i }
            }
        }
        if (bestIdx >= 0 && bestScore >= MATCH_THRESHOLD) {
            val e = list[bestIdx]
            list[bestIdx] = e.copy(hits = e.hits + 1)
            save(ctx, list)
            return e.answer
        }
        return null
    }

    fun put(ctx: Context, question: String, answer: String) {
        val key = keyOf(question)
        if (key.isBlank()) return
        val list = load(ctx)
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0) list[i] = list[i].copy(answer = answer.trim(), createdAt = now)
        else list.add(KnowledgeEntry(now, question.trim(), key, answer.trim(), 0, now))
        while (list.size > MAX_ENTRIES) {
            val victim = list.minWithOrNull(compareBy({ it.hits }, { it.createdAt })) ?: break
            list.remove(victim)
        }
        save(ctx, list)
    }

    /** دمج مدخلات من نسخة احتياطية: يتخطى نفس المفتاح، ويضمن id فريد. يرجع عدد المضاف. */
    fun importEntries(ctx: Context, entries: List<KnowledgeEntry>): Int {
        val list = load(ctx)
        var added = 0
        var nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        entries.forEach { e ->
            if (list.none { it.key == e.key }) {
                val id = if (list.any { it.id == e.id }) nextId++ else e.id
                list.add(e.copy(id = id))
                added++
            }
        }
        while (list.size > MAX_ENTRIES) {
            val victim = list.minWithOrNull(compareBy({ it.hits }, { it.createdAt })) ?: break
            list.remove(victim)
        }
        save(ctx, list)
        return added
    }

    fun getAll(ctx: Context): List<KnowledgeEntry> = load(ctx).sortedByDescending { it.createdAt }

    fun remove(ctx: Context, id: Long) = save(ctx, load(ctx).filter { it.id != id })
}
