package com.dani.assistant.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitReportsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 6)
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun empty_hasBomAndHeaderOnly() {
        val s = HabitReports.csv(emptyList(), today, zone)
        assertTrue(s.startsWith("\uFEFF"))
        assertEquals("\uFEFFالتاريخ,المجموع\r\n", s)
    }

    @Test fun rowsPerDay_andTotals() {
        val a = Habit(1, "ماء", "💧", ms(today.minusDays(2)), setOf(today.minusDays(2).toString(), today.toString()))
        val b = Habit(2, "قراءة", "📖", ms(today.minusDays(2)), setOf(today.toString()))
        val lines = HabitReports.csv(listOf(a, b), today, zone).trimEnd().split("\r\n")
        assertEquals("\uFEFFالتاريخ,💧 ماء,📖 قراءة,المجموع", lines[0])
        assertEquals(4, lines.size) // header + 3 أيام
        assertEquals("2026-10-04,1,0,1", lines[1])
        assertEquals("2026-10-05,0,0,0", lines[2])
        assertEquals("2026-10-06,1,1,2", lines[3])
    }

    @Test fun backdatedDaysExtendRange_futureIgnored() {
        val h = Habit(1, "x", "✅", ms(today), setOf(today.minusDays(1).toString(), today.plusDays(3).toString()))
        val lines = HabitReports.csv(listOf(h), today, zone).trimEnd().split("\r\n")
        assertEquals(3, lines.size) // header + البارح + اليوم
        assertEquals("2026-10-05,1,1", lines[1])
        assertEquals("2026-10-06,0,0", lines[2])
    }

    @Test fun nameWithCommaOrFormulaIsEscaped() {
        val h = Habit(1, "=SUM(A1),x", "", ms(today), emptySet())
        val header = HabitReports.csv(listOf(h), today, zone).split("\r\n")[0]
        assertEquals("\uFEFFالتاريخ,\"'=SUM(A1),x\",المجموع", header)
    }
}
