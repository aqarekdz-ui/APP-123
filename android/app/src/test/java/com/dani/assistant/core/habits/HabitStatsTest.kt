package com.dani.assistant.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HabitStatsTest {
    private val today = LocalDate.of(2026, 10, 6) // الثلاثاء

    @Test fun bestStreak_basic() {
        assertEquals(0, HabitStats.bestStreak(emptySet()))
        assertEquals(1, HabitStats.bestStreak(setOf("2026-10-01")))
        val d = setOf("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-10", "2026-09-11")
        assertEquals(3, HabitStats.bestStreak(d))
    }

    @Test fun bestStreak_acrossMonthAndIgnoresGarbage() {
        val d = setOf("2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "garbage")
        assertEquals(4, HabitStats.bestStreak(d))
    }

    @Test fun currentStreak_todayOrYesterday() {
        assertEquals(0, HabitStats.currentStreak(emptySet(), today))
        assertEquals(2, HabitStats.currentStreak(setOf("2026-10-05", "2026-10-06"), today))
        assertEquals(2, HabitStats.currentStreak(setOf("2026-10-04", "2026-10-05"), today)) // اليوم ما تمش بعد
        assertEquals(0, HabitStats.currentStreak(setOf("2026-10-03"), today)) // انقطعت
    }

    @Test fun rate_fullWindow() {
        val created = LocalDate.of(2026, 1, 1)
        val d = (0 until 15).map { today.minusDays(it.toLong()).toString() }.toSet()
        val r = HabitStats.rate(d, created, today, 30)
        assertEquals(15, r.done); assertEquals(30, r.window); assertEquals(50, r.percent)
    }

    @Test fun rate_newHabitShrinksWindow() {
        val created = today.minusDays(3)
        val r = HabitStats.rate(setOf(today.toString(), today.minusDays(1).toString()), created, today, 30)
        assertEquals(4, r.window); assertEquals(2, r.done); assertEquals(50, r.percent)
    }

    @Test fun rate_backdatedBeforeCreation() {
        val created = today // أنشئت اليوم لكن سجّلنا أيام قبل
        val d = setOf(today.minusDays(2).toString(), today.toString())
        val r = HabitStats.rate(d, created, today, 30)
        assertEquals(3, r.window); assertEquals(2, r.done)
    }

    @Test fun rate_ignoresOutsideWindowAndFuture() {
        val created = LocalDate.of(2025, 1, 1)
        val d = setOf(today.minusDays(40).toString(), today.plusDays(2).toString(), today.toString())
        val r = HabitStats.rate(d, created, today, 7)
        assertEquals(1, r.done); assertEquals(7, r.window)
    }

    @Test fun monthGrid_shape() {
        // أكتوبر 2026: 1 أكتوبر = الخميس => 4 فراغات (أحد..أربعاء)
        val g = HabitStats.monthGrid(setOf("2026-10-01", "2026-10-05"), YearMonth.of(2026, 10), today)
        assertEquals(0, g.size % 7)
        assertEquals(4, g.takeWhile { it.day == 0 }.size)
        assertEquals(31, g.count { it.day > 0 })
        assertTrue(g.first { it.day == 1 }.done)
        assertTrue(g.first { it.day == 5 }.done)
        assertTrue(!g.first { it.day == 2 }.done)
        assertTrue(g.first { it.day == 7 }.future)
        assertTrue(!g.first { it.day == 6 }.future)
    }

    @Test fun monthGrid_sundayStartHasNoLead() {
        // نوفمبر 2026: 1 نوفمبر = الأحد
        val g = HabitStats.monthGrid(emptySet(), YearMonth.of(2026, 11), today)
        assertEquals(1, g.first().day)
    }

    @Test fun monthCount_and_overall() {
        val d = setOf("2026-09-30", "2026-10-01", "2026-10-02")
        assertEquals(2, HabitStats.monthCount(d, YearMonth.of(2026, 10)))
        val o = HabitStats.overall(listOf(HabitStats.Rate(5, 10), HabitStats.Rate(1, 10)))
        assertEquals(6, o.done); assertEquals(20, o.window); assertEquals(30, o.percent)
    }

    // ---- أيام مخصّصة: الثلاثاء(2) والخميس(4). اليوم = الثلاثاء 2026-10-06
    private val tueThu = setOf(2, 4)

    @Test fun scheduled_basic() {
        assertTrue(HabitStats.isScheduled(emptySet(), today))
        assertTrue(HabitStats.isScheduled(tueThu, today))
        assertTrue(!HabitStats.isScheduled(tueThu, today.minusDays(1))) // الاثنين
    }

    @Test fun streak_skipsUnscheduledDays() {
        val d = setOf("2026-09-29", "2026-10-01", "2026-10-06")
        assertEquals(3, HabitStats.currentStreak(d, today, tueThu))
        assertEquals(1, HabitStats.currentStreak(d, today)) // بدون برنامج: اليوم فقط
    }

    @Test fun streak_todayNotDoneYetStillCounts_butMissedBreaks() {
        assertEquals(2, HabitStats.currentStreak(setOf("2026-09-29", "2026-10-01"), today, tueThu))
        assertEquals(0, HabitStats.currentStreak(setOf("2026-09-29"), today, tueThu)) // فاتت الخميس 1 أكتوبر
    }

    @Test fun bestStreak_withSchedule() {
        val d = setOf("2026-09-22", "2026-09-29", "2026-10-01", "2026-10-06")
        // 22 → 29 فاتت الخميس 24 => تنقطع، ثم 29,1,6 = 3
        assertEquals(3, HabitStats.bestStreak(d, tueThu))
        assertEquals(1, HabitStats.bestStreak(d))
    }

    @Test fun rate_countsOnlyScheduledDays() {
        val created = LocalDate.of(2026, 1, 1)
        val d = setOf("2026-10-06", "2026-10-01", "2026-10-02") // 2 أكتوبر جمعة (غير مبرمج) ما يتحسبش
        val r = HabitStats.rate(d, created, today, 7, tueThu) // 30 سبت..6 أكت: الخميس 1 والثلاثاء 6
        assertEquals(2, r.window); assertEquals(2, r.done); assertEquals(100, r.percent)
    }

    @Test fun monthGrid_marksUnscheduled() {
        val g = HabitStats.monthGrid(emptySet(), YearMonth.of(2026, 10), today, tueThu)
        assertTrue(g.first { it.day == 6 }.scheduled)   // الثلاثاء
        assertTrue(!g.first { it.day == 5 }.scheduled)  // الاثنين
    }
}
