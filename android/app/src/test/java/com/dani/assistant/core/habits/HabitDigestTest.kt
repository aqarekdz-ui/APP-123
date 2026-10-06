package com.dani.assistant.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitDigestTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 6) // الثلاثاء
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun streakDays(n: Int, end: LocalDate) = (1..n).map { end.minusDays(it.toLong()).toString() }.toSet()

    @Test fun morning_nullWhenNothingDue() {
        val done = Habit(1, "ماء", "💧", ms(today.minusDays(9)), setOf(today.toString()))
        assertNull(HabitDigest.morning(listOf(done), today).line)
        assertNull(HabitDigest.morning(emptyList(), today).line)
    }

    @Test fun morning_listsDueAndStreakRisk() {
        val a = Habit(1, "ماء", "💧", ms(today.minusDays(20)), streakDays(5, today)) // 5 أيام لحد البارح
        val b = Habit(2, "قراءة", "📖", ms(today.minusDays(20)), emptySet())
        val m = HabitDigest.morning(listOf(a, b), today)
        assertNotNull(m.line)
        assertTrue(m.line!!.contains("(2)"))
        assertTrue(m.line!!.contains("💧 ماء"))
        assertEquals(listOf("💧 ماء (5)"), m.risk)
        assertTrue(m.line!!.contains("🔥"))
    }

    @Test fun morning_skipsUnscheduledToday() {
        val monOnly = Habit(1, "جري", "🏃", ms(today.minusDays(20)), emptySet(), setOf(1)) // الاثنين فقط
        assertNull(HabitDigest.morning(listOf(monOnly), today).line)
    }

    @Test fun morning_truncatesAfterThree() {
        val hs = (1..5).map { Habit(it.toLong(), "ع" + it, "", ms(today.minusDays(20)), emptySet()) }
        val line = HabitDigest.morning(hs, today).line!!
        assertTrue(line.contains("(5)"))
        assertTrue(line.contains("+2"))
    }

    @Test fun weekly_overallAndBestStreak() {
        val a = Habit(1, "ماء", "💧", ms(today.minusDays(30)), streakDays(7, today.plusDays(1)) ) // آخر 7 أيام كاملة
        val b = Habit(2, "قراءة", "📖", ms(today.minusDays(30)), emptySet())
        val s = HabitDigest.weekly(listOf(a, b), today, zone)!!
        assertTrue(s.contains("50%"))
        assertTrue(s.contains("(7/14)"))
        assertTrue(s.contains("💧 ماء 7"))
    }

    @Test fun weekly_nullWhenNoHabits() {
        assertNull(HabitDigest.weekly(emptyList(), today, zone))
    }
}
