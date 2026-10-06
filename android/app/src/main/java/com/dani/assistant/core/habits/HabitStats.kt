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
    data class Cell(val day: Int, val done: Boolean, val future: Boolean, val scheduled: Boolean = true)

    private fun parse(s: String): LocalDate? = try { LocalDate.parse(s) } catch (e: Exception) { null }

    private fun dates(days: Set<String>): List<LocalDate> = days.mapNotNull { parse(it) }.distinct().sorted()

    /** [weekdays] = أيام الأسبوع المبرمجة (DayOfWeek.value: الاثنين=1..الأحد=7). فارغة = كل يوم. */
    fun isScheduled(weekdays: Set<Int>, date: LocalDate): Boolean =
        weekdays.isEmpty() || weekdays.contains(date.dayOfWeek.value)

    /** بين يومين منجزين، هل ما كاين حتى يوم مبرمج فايت؟ (الأيام غير المبرمجة ما تكسرش السلسلة). */
    private fun connected(a: LocalDate, b: LocalDate, weekdays: Set<Int>): Boolean {
        var x = a.plusDays(1)
        while (x.isBefore(b)) {
            if (isScheduled(weekdays, x)) return false
            x = x.plusDays(1)
        }
        return true
    }

    /** أطول سلسلة في كل السجل (الأيام غير المبرمجة تتجاوز). */
    fun bestStreak(days: Set<String>, weekdays: Set<Int> = emptySet()): Int {
        val ds = dates(days)
        if (ds.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until ds.size) {
            if (connected(ds[i - 1], ds[i], weekdays)) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    /** السلسلة الحالية: إذا اليوم ما تمش نبدأ من البارح (نفس منطق HabitStore.streak). */
    fun currentStreak(days: Set<String>, today: LocalDate, weekdays: Set<Int> = emptySet()): Int {
        val set = days.mapNotNull { parse(it) }.toHashSet()
        var d = if (set.contains(today)) today else today.minusDays(1)
        var n = 0
        var guard = 0
        while (guard++ < 4000) {
            if (set.contains(d)) n++
            else if (isScheduled(weekdays, d)) break
            d = d.minusDays(1)
        }
        return n
    }

    /**
     * نسبة الالتزام في آخر [windowDays] يوم (اليوم داخل). إذا العادة جديدة تتقلّص النافذة
     * لبداية العادة (أقدم من تاريخ الإنشاء وأول يوم مسجّل) باش ما تظلمش العادة الجديدة.
     */
    fun rate(days: Set<String>, createdAt: LocalDate, today: LocalDate, windowDays: Int, weekdays: Set<Int> = emptySet()): Rate {
        if (windowDays <= 0) return Rate(0, 0)
        val ds = dates(days)
        val start = if (ds.isNotEmpty() && ds.first().isBefore(createdAt)) ds.first() else createdAt
        val sinceStart = ChronoUnit.DAYS.between(start, today) + 1
        val span = if (sinceStart <= 0) 1 else minOf(windowDays.toLong(), sinceStart).toInt()
        val from = today.minusDays((span - 1).toLong())
        val set = ds.toHashSet()
        var window = 0
        var done = 0
        var d = from
        while (!d.isAfter(today)) {
            if (isScheduled(weekdays, d)) {
                window++
                if (set.contains(d)) done++
            }
            d = d.plusDays(1)
        }
        return Rate(done, window)
    }

    /** عدد الأيام المنجزة في شهر معيّن. */
    fun monthCount(days: Set<String>, ym: YearMonth): Int =
        dates(days).count { YearMonth.from(it) == ym }

    /** شبكة شهر، الأسبوع يبدأ بالأحد (نفس ترتيب حروف الأيام في الواجهة). الطول دايماً مضاعف 7. */
    fun monthGrid(days: Set<String>, ym: YearMonth, today: LocalDate, weekdays: Set<Int> = emptySet()): List<Cell> {
        val set = days.mapNotNull { parse(it) }.toHashSet()
        val first = ym.atDay(1)
        val lead = if (first.dayOfWeek == DayOfWeek.SUNDAY) 0 else first.dayOfWeek.value // الاثنين=1..السبت=6
        val out = ArrayList<Cell>()
        repeat(lead) { out.add(Cell(0, false, false)) }
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            out.add(Cell(d, set.contains(date), date.isAfter(today), isScheduled(weekdays, date)))
        }
        while (out.size % 7 != 0) out.add(Cell(0, false, false))
        return out
    }

    /** نسبة التزام الكل (كل العادات) في آخر [windowDays] يوم، مرجّحة بأيام كل نافذة. */
    fun overall(rates: List<Rate>): Rate = Rate(rates.sumOf { it.done }, rates.sumOf { it.window })
}
