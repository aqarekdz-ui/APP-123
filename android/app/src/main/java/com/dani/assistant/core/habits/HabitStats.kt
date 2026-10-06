package com.dani.assistant.core.habits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** منطق صافي لإحصائيات العادات (بدون Context). الأيام نصوص yyyy-MM-dd. */
object HabitStats {

    data class Rate(val done: Int, val window: Int) {
        val percent: Int get() = if (window <= 0) 0 else Math.round(done * 100f / window)
    }

    /** خلية في شبكة الشهر: day=0 يعني فراغ. */
    data class Cell(val day: Int, val done: Boolean, val future: Boolean)

    private fun parse(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: Exception) { null }

    private fun dates(days: Set<String>): List<LocalDate> = days.mapNotNull { parse(it) }.distinct().sorted()

    /** أطول سلسلة أيام متتالية في كل السجل. */
    fun bestStreak(days: Set<String>): Int {
        val ds = dates(days)
        if (ds.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until ds.size) {
            if (ChronoUnit.DAYS.between(ds[i - 1], ds[i]) == 1L) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    /** السلسلة الحالية: إذا اليوم ما تمش نبدأ من البارح (نفس منطق HabitStore.streak). */
    fun currentStreak(days: Set<String>, today: LocalDate): Int {
        val set = days.mapNotNull { parse(it) }.toHashSet()
        var d = if (set.contains(today)) today else today.minusDays(1)
        var n = 0
        while (set.contains(d)) { n++; d = d.minusDays(1) }
        return n
    }

    /**
     * نسبة الالتزام في آخر [windowDays] يوم (اليوم داخل). إذا العادة جديدة تتقلّص النافذة
     * لبداية العادة (أقدم من تاريخ الإنشاء وأول يوم مسجّل) باش ما تظلمش العادة الجديدة.
     */
    fun rate(days: Set<String>, createdAt: LocalDate, today: LocalDate, windowDays: Int): Rate {
        if (windowDays <= 0) return Rate(0, 0)
        val ds = dates(days)
        val start = if (ds.isNotEmpty() && ds.first().isBefore(createdAt)) ds.first() else createdAt
        val sinceStart = ChronoUnit.DAYS.between(start, today) + 1
        val window = if (sinceStart <= 0) 1 else minOf(windowDays.toLong(), sinceStart).toInt()
        val from = today.minusDays((window - 1).toLong())
        val done = ds.count { !it.isBefore(from) && !it.isAfter(today) }
        return Rate(done, window)
    }

    /** عدد الأيام المنجزة في شهر معيّن. */
    fun monthCount(days: Set<String>, ym: YearMonth): Int =
        dates(days).count { YearMonth.from(it) == ym }

    /** شبكة شهر، الأسبوع يبدأ بالأحد (نفس ترتيب حروف الأيام في الواجهة). الطول دايماً مضاعف 7. */
    fun monthGrid(days: Set<String>, ym: YearMonth, today: LocalDate): List<Cell> {
        val set = days.mapNotNull { parse(it) }.toHashSet()
        val first = ym.atDay(1)
        val lead = if (first.dayOfWeek == DayOfWeek.SUNDAY) 0 else first.dayOfWeek.value // الاثنين=1..السبت=6
        val out = ArrayList<Cell>()
        repeat(lead) { out.add(Cell(0, false, false)) }
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            out.add(Cell(d, set.contains(date), date.isAfter(today)))
        }
        while (out.size % 7 != 0) out.add(Cell(0, false, false))
        return out
    }

    /** نسبة التزام الكل (كل العادات) في آخر [windowDays] يوم، مرجّحة بأيام كل نافذة. */
    fun overall(rates: List<Rate>): Rate = Rate(rates.sumOf { it.done }, rates.sumOf { it.window })
}
