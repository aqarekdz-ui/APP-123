package com.dani.assistant.core.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitReminderTest {
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo, d, h, mi, 0) }.timeInMillis

    private fun hm(ms: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Triple(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    @Test fun beforeNine_pickssameDay() {
        assertEquals(Triple(6, 21, 0), hm(HabitReminder.nextTrigger(at(2026, Calendar.OCTOBER, 6, 10, 0))))
    }

    @Test fun afterNine_picksTomorrow() {
        assertEquals(Triple(7, 21, 0), hm(HabitReminder.nextTrigger(at(2026, Calendar.OCTOBER, 6, 22, 15))))
    }

    @Test fun withinMinuteBefore_skipsToTomorrow() {
        val now = at(2026, Calendar.OCTOBER, 6, 20, 59) + 30_000L
        assertEquals(Triple(7, 21, 0), hm(HabitReminder.nextTrigger(now)))
    }

    @Test fun monthEndRolls() {
        assertEquals(Triple(1, 21, 0), hm(HabitReminder.nextTrigger(at(2026, Calendar.OCTOBER, 31, 23, 0))))
        assertTrue(HabitReminder.nextTrigger(1_000L) > 1_000L)
    }
}
