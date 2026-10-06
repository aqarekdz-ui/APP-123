package com.dani.assistant.core.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthlyReportTest {
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        val c = Calendar.getInstance(); c.clear(); c.set(y, m - 1, d, h, min, 0); return c.timeInMillis
    }
    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

    @Test fun nextTriggerIsFirstOfNextMonthAt0930() {
        val t = cal(MonthlyReport.nextTrigger(ms(2026, 10, 6)))
        assertEquals(2026, t.get(Calendar.YEAR)); assertEquals(Calendar.NOVEMBER, t.get(Calendar.MONTH)); assertEquals(1, t.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, t.get(Calendar.HOUR_OF_DAY)); assertEquals(30, t.get(Calendar.MINUTE))
    }

    @Test fun nextTriggerOnFirstBeforeAndAfter0930() {
        val before = cal(MonthlyReport.nextTrigger(ms(2026, 11, 1, 8, 0)))
        assertEquals(1, before.get(Calendar.DAY_OF_MONTH)); assertEquals(Calendar.NOVEMBER, before.get(Calendar.MONTH))
        val after = cal(MonthlyReport.nextTrigger(ms(2026, 11, 1, 10, 0)))
        assertEquals(Calendar.DECEMBER, after.get(Calendar.MONTH))
    }

    @Test fun nextTriggerCrossesYear() {
        val t = cal(MonthlyReport.nextTrigger(ms(2026, 12, 15)))
        assertEquals(2027, t.get(Calendar.YEAR)); assertEquals(Calendar.JANUARY, t.get(Calendar.MONTH))
    }

    @Test fun monthRangePreviousMonthAcrossYear() {
        val (a, b, m) = MonthlyReport.monthRange(ms(2027, 1, 10), -1)
        assertEquals(11, m) // ديسمبر
        assertEquals(ms(2026, 12, 1, 0), a)
        assertEquals(ms(2027, 1, 1, 0), b)
    }

    @Test fun textWithComparisonAndCategories() {
        val t = MonthlyReport.text(8, 50000, 90000, listOf("أكل" to 20000L, "نقل" to 10000L), 40000, 12, 30)
        assertTrue(t, t.startsWith("📅 ملخص سبتمبر"))
        assertTrue(t, t.contains("+25%"))
        assertTrue(t, t.contains("الرصيد: +"))
        assertTrue(t, t.contains("أكل") && t.contains("مهام منجزة: 12"))
    }

    @Test fun textNegativeBalanceNoComparison() {
        val t = MonthlyReport.text(0, 80000, 50000, emptyList(), 0, 0, 5)
        assertTrue(t, t.contains("الرصيد: -"))
        assertTrue(t, !t.contains("مقارنة"))
        assertTrue(t, t.startsWith("📅 ملخص جانفي"))
    }

    @Test fun emptyMonth() {
        assertTrue(MonthlyReport.text(5, 0, 0, emptyList(), 0, 0, 0).contains("ما سجلتش شيء"))
    }
}
