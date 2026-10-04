package com.dani.assistant.core.search

import android.content.Context
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.events.EventStore
import com.dani.assistant.core.events.fmtEventDate
import com.dani.assistant.core.goals.GoalStore
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.knowledge.KnowledgeBase
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.meds.MedStore
import com.dani.assistant.core.meds.fmtMinute
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.notes.NoteStore
import com.dani.assistant.core.realestate.RealEstateStore
import com.dani.assistant.presentation.navigation.Screen
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Hit(val emoji: String, val kind: String, val title: String, val sub: String, val route: String)

/** بحث شامل محلي (بدون الأسرار المشفّرة): مهام، ملاحظات، مناسبات، مال، عملاء/عقارات، أهداف، عادات، أدوية، ذاكرة، معرفة. */
object GlobalSearch {
    private const val PER_KIND = 8

    private fun norm(s: String): String = s.lowercase().map {
        when (it) {
            'أ', 'إ', 'آ' -> 'ا'
            'ى' -> 'ي'
            'ة' -> 'ه'
            else -> it
        }
    }.joinToString("").replace(Regex("[\\u064B-\\u0652\\u0640]"), "")

    private fun tokens(q: String): List<String> = norm(q).split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun match(toks: List<String>, vararg fields: String): Boolean {
        val h = norm(fields.joinToString(" "))
        return toks.all { h.contains(it) }
    }

    private fun short(s: String, n: Int = 90): String {
        val t = s.replace("\n", " ").trim()
        return if (t.length > n) t.take(n) + "…" else t
    }

    private fun typeLabel(t: String) = when (t) {
        "expense" -> "مصروف"
        "income" -> "دخل"
        "debt_to_me" -> "دين لي"
        "debt_i_owe" -> "دين عليّ"
        else -> t
    }

    suspend fun search(ctx: Context, q: String): List<Hit> {
        val toks = tokens(q)
        if (toks.isEmpty() || q.trim().length < 2) return emptyList()
        val out = ArrayList<Hit>()
        val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        try {
            DaniApplication.instance.taskRepository.getAllTasks().first()
                .filter { match(toks, it.title, it.description ?: "") }.take(PER_KIND)
                .forEach { t ->
                    out.add(Hit("✅", "مهمة", t.title,
                        (if (t.isCompleted) "منجزة" else "قائمة") + (t.dueDate?.let { " • " + df.format(Date(it)) } ?: ""), Screen.Tasks.route))
                }
        } catch (e: Exception) { }

        NoteStore.list(ctx).filter { match(toks, it.text) }.take(PER_KIND)
            .forEach { out.add(Hit("📝", "ملاحظة", short(it.text), df.format(Date(it.updatedAt)), "notes")) }

        EventStore.list(ctx).filter { match(toks, it.name) }.take(PER_KIND)
            .forEach { out.add(Hit(if (it.kind == "birthday") "🎂" else "🎉", "مناسبة", it.name, fmtEventDate(it), "events")) }

        MoneyStore.entries(ctx).filter { match(toks, it.category, it.note, it.person, typeLabel(it.type), it.amount.toString()) }
            .sortedByDescending { it.date }.take(PER_KIND)
            .forEach {
                out.add(Hit("💰", typeLabel(it.type), MoneyStore.fmt(it.amount) + " — " + (it.person.ifBlank { it.category }),
                    short(it.note, 50) + (if (it.note.isNotBlank()) " • " else "") + df.format(Date(it.date)), "money"))
            }

        RealEstateStore.clients(ctx).filter { match(toks, it.name, it.phone, it.area, it.notes, it.wants, it.budget) }.take(PER_KIND)
            .forEach { out.add(Hit("👤", "عميل", it.name, it.wants + (if (it.area.isNotBlank()) " • " + it.area else "") + " • " + it.status, "realestate")) }
        RealEstateStore.properties(ctx).filter { match(toks, it.title, it.area, it.notes, it.kind, it.price) }.take(PER_KIND)
            .forEach { out.add(Hit("🏠", "عقار", it.title, it.deal + " • " + it.kind + (if (it.price.isNotBlank()) " • " + it.price else ""), "realestate")) }

        GoalStore.list(ctx).filter { match(toks, it.title) }.take(PER_KIND)
            .forEach { out.add(Hit("🎯", "هدف", it.title, it.horizonDays.toString() + " يوم", "goals")) }
        HabitStore.list(ctx).filter { match(toks, it.name) }.take(PER_KIND)
            .forEach { out.add(Hit("✅", "عادة", it.emoji + " " + it.name, "", "habits")) }
        MedStore.list(ctx).filter { match(toks, it.name, it.dose) }.take(PER_KIND)
            .forEach { out.add(Hit("💊", "دواء", it.name, it.times.joinToString(" ") { t -> fmtMinute(t) }, "meds")) }

        MemoryStore.getAll(ctx).filter { match(toks, it) }.take(PER_KIND)
            .forEach { out.add(Hit("🧠", "معلومة", short(it), "", Screen.Memory.route)) }
        KnowledgeBase.getAll(ctx).filter { match(toks, it.question, it.answer) }.take(PER_KIND)
            .forEach { out.add(Hit("📚", "معرفة", short(it.question, 60), short(it.answer, 60), Screen.Memory.route)) }
        return out
    }

    suspend fun chatReply(ctx: Context, q: String): String {
        val hits = search(ctx, q)
        if (hits.isEmpty()) return "🔎 ما لقيت والو على \"" + q.trim() + "\"."
        return "🔎 لقيت " + hits.size + " على \"" + q.trim() + "\":\n" + hits.take(10).joinToString("\n") {
            it.emoji + " [" + it.kind + "] " + it.title + (if (it.sub.isNotBlank()) " — " + it.sub else "")
        } + (if (hits.size > 10) "\n… وزيد (افتح 🔎 بحث من الرئيسية)" else "")
    }
}
