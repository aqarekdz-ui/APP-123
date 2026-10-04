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
