package com.dani.assistant.core.habits

import com.dani.assistant.core.money.MoneyReports
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** تصدير العادات CSV (بدون Context): صف لكل يوم، عمود لكل عادة (1 = تمّت، 0 = لا)، وعمود المجموع. */
object HabitReports {

    private fun parse(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: Exception) { null }

    /** أول يوم يدخل في التقرير: أقدم تاريخ بين إنشاء العادات والأيام المسجّلة. null إذا ما كاين عادات. */
    internal fun firstDay(habits: List<Habit>, zone: ZoneId): LocalDate? {
        var first: LocalDate? = null
        for (h in habits) {
            val created = Instant.ofEpochMilli(h.createdAt).atZone(zone).toLocalDate()
            val earliest = h.days.mapNotNull { parse(it) }.minOrNull()
            val start = if (earliest != null && earliest.isBefore(created)) earliest else created
            if (first == null || start.isBefore(first)) first = start
        }
        return first
    }

    /** CSV بـ UTF-8 + BOM، الأيام من الأقدم للأحدث لحد [today]. أيام بعد اليوم تتجاهل. */
    fun csv(habits: List<Habit>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("التاريخ")
        for (h in habits) sb.append(',').append(MoneyReports.csvField((h.emoji + " " + h.name).trim()))
        sb.append(",المجموع\r\n")
        val first = firstDay(habits, zone) ?: return sb.toString()
        var d = first
        while (!d.isAfter(today)) {
            val key = d.toString()
            var total = 0
            sb.append(key)
            for (h in habits) {
                val done = h.days.contains(key)
                if (done) total++
                sb.append(',').append(if (done) "1" else "0")
            }
            sb.append(',').append(total).append("\r\n")
            d = d.plusDays(1)
        }
        return sb.toString()
    }
}
