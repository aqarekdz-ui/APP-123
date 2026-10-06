package com.dani.assistant.core.habits

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** أسطر العادات للملخص الصباحي والمراجعة الأسبوعية (منطق صافي، بدون Context). */
object HabitDigest {
    const val RISK_MIN_STREAK = 3

    data class Morning(val line: String?, val risk: List<String>)

    private fun label(h: Habit): String = (h.emoji + " " + h.name).trim()

    /** عادات اليوم اللي ما تمّتش بعد (المبرمجة فقط) + السلاسل (>= 3) اللي تتحمى اليوم. */
    fun morning(habits: List<Habit>, today: LocalDate): Morning {
        val key = today.toString()
        val due = habits.filter { HabitStats.isScheduled(it.weekdays, today) && !it.days.contains(key) }
        if (due.isEmpty()) return Morning(null, emptyList())
        val risk = due.mapNotNull { h ->
            val st = HabitStats.currentStreak(h.days, today, h.weekdays)
            if (st >= RISK_MIN_STREAK) label(h) + " (" + st + ")" else null
        }
        val names = due.take(3).joinToString("، ") { label(it) } + (if (due.size > 3) " … +" + (due.size - 3) else "")
        val sb = StringBuilder("✅ عادات اليوم (").append(due.size).append("): ").append(names)
        if (risk.isNotEmpty()) sb.append("\n🔥 حافظ على السلسلة: ").append(risk.joinToString("، "))
        return Morning(sb.toString(), risk)
    }

    data class Evening(val title: String, val body: String)

    /** تذكير مسائي: العادات المبرمجة اليوم وما تمّتش (null إذا كلشي تمّ أو ما كاين شيء مبرمج). */
    fun evening(habits: List<Habit>, today: LocalDate): Evening? {
        val key = today.toString()
        val due = habits.filter { HabitStats.isScheduled(it.weekdays, today) && !it.days.contains(key) }
        if (due.isEmpty()) return null
        val risk = due.mapNotNull { h ->
            val st = HabitStats.currentStreak(h.days, today, h.weekdays)
            if (st >= RISK_MIN_STREAK) label(h) + " (" + st + ")" else null
        }
        val title = if (due.size == 1) "🌙 باقي عادة وحدة اليوم" else "🌙 باقي " + due.size + " عادات اليوم"
        val sb = StringBuilder()
        due.take(6).forEach { sb.append("⬜ ").append(label(it)).append("\n") }
        if (due.size > 6) sb.append("… و").append(due.size - 6).append(" أخرى\n")
        if (risk.isNotEmpty()) sb.append("🔥 سلسلة تتقطع إذا ما درتهاش: ").append(risk.joinToString("، "))
        return Evening(title, sb.toString().trimEnd())
    }

    /** سطر أسبوعي: التزام عام آخر 7 أيام + أطول سلسلة حالية (>= 2). null إذا ما كاين عادات. */
    fun weekly(habits: List<Habit>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (habits.isEmpty()) return null
        val rates = habits.map {
            val created = Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
            HabitStats.rate(it.days, created, today, 7, it.weekdays)
        }
        val all = HabitStats.overall(rates)
        if (all.window == 0) return null
        val sb = StringBuilder("📊 التزام العادات: ").append(all.percent).append("% (").append(all.done).append("/").append(all.window).append(")")
        val best = habits.map { it to HabitStats.currentStreak(it.days, today, it.weekdays) }.maxByOrNull { it.second }
        if (best != null && best.second >= 2) sb.append("\n🏆 أطول سلسلة حالية: ").append(label(best.first)).append(" ").append(best.second)
        return sb.toString()
    }
}
