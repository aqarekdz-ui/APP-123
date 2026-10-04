package com.dani.assistant.core.money

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthTotal(val year: Int, val month: Int, val income: Long, val expense: Long)

/** تقارير المال (منطق صافي، يتجرّب بـ JUnit): CSV + مجاميع للرسوم. */
object MoneyReports {
    private val typeLabels = mapOf(
        "expense" to "مصروف", "income" to "دخل", "debt_to_me" to "دين لي", "debt_i_owe" to "دين عليّ"
    )

    /** يحمي من حقن الصيغ في Excel (=, +, -, @) ويهرّب الفواصل والاقتباس. */
    internal fun csvField(raw: String): String {
        var s = raw.replace("\r", " ").replace("\n", " ")
        if (s.isNotEmpty() && s[0] in "=+-@") s = "'" + s
        return if (s.contains(',') || s.contains('"') || s.contains(';')) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** CSV بترميز UTF-8 مع BOM (باش Excel يقرأ العربية). السطور مرتبة من الأقدم للأحدث. */
    fun csv(entries: List<MoneyEntry>, from: Long, to: Long): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder("\uFEFF")
        sb.append("التاريخ,النوع,المبلغ (دج),الفئة,ملاحظة,الشخص,مسدّد\r\n")
        for (e in entries.filter { it.date in from until to }.sortedBy { it.date }) {
            sb.append(df.format(Date(e.date))).append(',')
                .append(typeLabels[e.type] ?: e.type).append(',')
                .append(e.amount).append(',')
                .append(csvField(e.category)).append(',')
                .append(csvField(e.note)).append(',')
                .append(csvField(e.person)).append(',')
                .append(if (e.type.startsWith("debt")) (if (e.settled) "نعم" else "لا") else "")
                .append("\r\n")
        }
        return sb.toString()
    }

    /** مجموع المصاريف حسب الفئة في المجال، من الأكبر للأصغر. */
    fun categoryTotals(entries: List<MoneyEntry>, from: Long, to: Long): List<Pair<String, Long>> =
        entries.filter { it.type == "expense" && it.date in from until to }
            .groupBy { it.category.ifBlank { "أخرى" } }
            .map { (k, v) -> k to v.sumOf { it.amount } }
            .sortedByDescending { it.second }

    /** آخر [count] أشهر (الأقدم أولاً) تنتهي بشهر [nowMs]. */
    fun monthTotals(entries: List<MoneyEntry>, count: Int, nowMs: Long): List<MonthTotal> {
        val out = ArrayList<MonthTotal>()
        for (back in count - 1 downTo 0) {
            val c = Calendar.getInstance()
            c.timeInMillis = nowMs
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.MONTH, -back)
            val a = c.timeInMillis
            val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH) + 1
            c.add(Calendar.MONTH, 1)
            val b = c.timeInMillis
            val inMonth = entries.filter { it.date in a until b }
            out.add(MonthTotal(y, m,
                inMonth.filter { it.type == "income" }.sumOf { it.amount },
                inMonth.filter { it.type == "expense" }.sumOf { it.amount }))
        }
        return out
    }
}
