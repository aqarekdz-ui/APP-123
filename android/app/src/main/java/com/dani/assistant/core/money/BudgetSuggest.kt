package com.dani.assistant.core.money

import java.util.Calendar

/** اقتراح ميزانيات من متوسط آخر أشهر كاملة (منطق صافي، بدون Context). */
object BudgetSuggest {
    data class Suggestion(val category: String, val average: Long, val suggested: Long, val current: Long, val months: Int)

    const val MIN_AVERAGE = 1000L
    const val ROUND_TO = 500L

    private fun monthStart(nowMs: Long, plusMonths: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, plusMonths)
        return c.timeInMillis
    }

    internal fun ceilTo(v: Long, step: Long): Long = if (v <= 0) 0 else ((v + step - 1) / step) * step

    /**
     * آخر [months] أشهر كاملة (بلا الشهر الجاري). المتوسط = المجموع / عدد الأشهر اللي كانت فيها بيانات
     * (من أول مصروف مسجّل)، والمبلغ المقترح = المتوسط مقرّب لفوق لأقرب 500. فئات متوسطها أقل من 1000 دج تتجاهل،
     * والفئة اللي ميزانيتها الحالية = المقترح تتجاهل. مرتبة من الأكبر.
     */
    fun suggest(entries: List<MoneyEntry>, nowMs: Long, current: Map<String, Long>, months: Int = 3): List<Suggestion> {
        val expenses = entries.filter { it.type == "expense" && it.amount > 0 }
        if (expenses.isEmpty() || months <= 0) return emptyList()
        val earliest = expenses.minOf { it.date }
        val windowStart = monthStart(nowMs, -months)
        val windowEnd = monthStart(nowMs, 0)
        // عدد الأشهر الكاملة في النافذة اللي تنتهي بعد أول مصروف
        var eff = 0
        for (back in months downTo 1) if (monthStart(nowMs, -back + 1) > earliest) eff++
        if (eff == 0) return emptyList()
        val inWindow = expenses.filter { it.date in windowStart until windowEnd }
        return inWindow.groupBy { it.category.ifBlank { "أخرى" } }
            .map { (cat, list) ->
                val avg = list.sumOf { it.amount } / eff
                Suggestion(cat, avg, ceilTo(avg, ROUND_TO), current[cat] ?: 0L, eff)
            }
            .filter { it.average >= MIN_AVERAGE && it.suggested != it.current }
            .sortedByDescending { it.average }
    }
}
