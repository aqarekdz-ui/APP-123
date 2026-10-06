package com.dani.assistant.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoneyReportsTest {
    private fun ms(y: Int, m: Int, d: Int): Long {
        val c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d, 12, 0, 0); return c.timeInMillis
    }
    private fun e(type: String, amt: Long, date: Long, cat: String = "", note: String = "", person: String = "", settled: Boolean = false) =
        MoneyEntry(id = date + amt, type = type, amount = amt, category = cat, note = note, person = person, date = date, settled = settled)

    @Test fun csvFieldEscaping() {
        assertEquals("abc", MoneyReports.csvField("abc"))
        assertEquals("\"a,b\"", MoneyReports.csvField("a,b"))
        assertEquals("\"he said \"\"hi\"\"\"", MoneyReports.csvField("he said \"hi\""))
        assertEquals("a b", MoneyReports.csvField("a\nb"))
    }

    @Test fun csvFieldBlocksFormulaInjection() {
        assertEquals("'=1+1", MoneyReports.csvField("=1+1"))
        assertEquals("'@cmd", MoneyReports.csvField("@cmd"))
    }

    @Test fun csvHasBomHeaderAndSortedRowsInRangeOnly() {
        val list = listOf(
            e("expense", 500, ms(2026, 10, 5), "أكل", "قهوة, حليب"),
            e("income", 90000, ms(2026, 10, 1), "راتب"),
            e("expense", 100, ms(2026, 9, 30), "أكل")
        )
        val out = MoneyReports.csv(list, ms(2026, 10, 1) - 100000, ms(2026, 11, 1))
        assertTrue(out.startsWith("\uFEFF"))
        val lines = out.trim().split("\r\n")
        assertEquals(3, lines.size)
        assertTrue(lines[1], lines[1].startsWith("2026-10-01,دخل,90000,راتب"))
        assertTrue(lines[2], lines[2].contains("مصروف,500,أكل,\"قهوة, حليب\""))
    }

    @Test fun csvDebtSettledColumn() {
        val out = MoneyReports.csv(listOf(e("debt_to_me", 3000, ms(2026, 10, 2), person = "علي", settled = true)), 0, Long.MAX_VALUE)
        assertTrue(out, out.contains("دين لي,3000,,,علي,نعم"))
    }

    @Test fun categoryTotalsSortedAndExpenseOnly() {
        val list = listOf(
            e("expense", 100, ms(2026, 10, 2), "أكل"), e("expense", 400, ms(2026, 10, 3), "فواتير"),
            e("expense", 50, ms(2026, 10, 4), "أكل"), e("income", 9999, ms(2026, 10, 4), "راتب"),
            e("expense", 70, ms(2026, 10, 5), "")
        )
        val r = MoneyReports.categoryTotals(list, ms(2026, 10, 1), ms(2026, 11, 1))
        assertEquals(listOf("فواتير" to 400L, "أكل" to 150L, "أخرى" to 70L), r)
    }

    @Test fun monthTotalsCoversSixMonthsAcrossYear() {
        val list = listOf(
            e("expense", 100, ms(2026, 10, 2)), e("income", 500, ms(2026, 10, 3)),
            e("expense", 70, ms(2026, 5, 20)), e("expense", 30, ms(2026, 4, 20))
        )
        val r = MoneyReports.monthTotals(list, 6, ms(2026, 10, 15))
        assertEquals(6, r.size)
        assertEquals(5, r.first().month); assertEquals(70L, r.first().expense)
        assertEquals(10, r.last().month); assertEquals(100L, r.last().expense); assertEquals(500L, r.last().income)
        val jan = MoneyReports.monthTotals(emptyList(), 3, ms(2027, 1, 10))
        assertEquals(listOf(11, 12, 1), jan.map { it.month })
        assertEquals(listOf(2026, 2026, 2027), jan.map { it.year })
    }
}

class MoneyPaceTest {
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12): Long {
        val c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d, h, 0, 0); return c.timeInMillis
    }
    private fun ex(amt: Long, date: Long, type: String = "expense") = MoneyEntry(id = date + amt, type = type, amount = amt, date = date)

    @Test fun projectsToEndOfMonthAndComparesSamePeriod() {
        val now = ms(2026, 10, 10)
        val list = listOf(
            ex(1000, ms(2026, 10, 2)), ex(1000, ms(2026, 10, 10, 20)),
            ex(500, ms(2026, 9, 3)), ex(5000, ms(2026, 9, 20)), // خارج نفس الفترة
            ex(9999, ms(2026, 10, 5), "income")
        )
        val p = MoneyReports.pace(list, now)
        assertEquals(2000L, p.spent)
        assertEquals(500L, p.prevSamePeriod)
        assertEquals(6200L, p.projected) // 2000 * 31 / 10
        assertEquals(300, p.pct)
    }

    @Test fun noPreviousMonthMeansNoPercent() {
        val p = MoneyReports.pace(listOf(ex(300, ms(2026, 10, 1))), ms(2026, 10, 15))
        assertEquals(null, p.pct)
        assertEquals(0L, p.prevSamePeriod)
    }

    @Test fun shortPreviousMonthIsCapped() {
        // 31 مارس مقابل فيفري (28 يوم): الفترة السابقة = كامل فيفري
        val list = listOf(ex(100, ms(2026, 3, 31)), ex(400, ms(2026, 2, 28)), ex(50, ms(2026, 2, 1)))
        val p = MoneyReports.pace(list, ms(2026, 3, 31))
        assertEquals(450L, p.prevSamePeriod)
        assertEquals(100L, p.spent)
        assertEquals(100L, p.projected)
    }

    @Test fun fixedExpensesAreNotMultiplied() {
        val now = ms(2026, 10, 10)
        val list = listOf(
            MoneyEntry(id = 1, type = "expense", amount = 30000, note = "🔁 كراء", date = ms(2026, 10, 5)),
            ex(1000, ms(2026, 10, 3))
        )
        val p = MoneyReports.pace(list, now)
        assertEquals(31000L, p.spent)
        assertEquals(30000L, p.fixed)
        assertEquals(30000L + 1000L * 31 / 10, p.projected) // 33100
    }

    @Test fun upcomingFixedIsAddedOnce() {
        val p = MoneyReports.pace(listOf(ex(1000, ms(2026, 10, 3))), ms(2026, 10, 10), upcomingFixed = 5000)
        assertEquals(5000L + 1000L * 31 / 10, p.projected)
        assertEquals(5000L, p.upcomingFixed)
    }

    @Test fun emptyMonth() {
        val p = MoneyReports.pace(emptyList(), ms(2026, 10, 4))
        assertEquals(0L, p.spent); assertEquals(0L, p.projected)
    }
}
