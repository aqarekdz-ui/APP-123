package com.dani.assistant.core.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventActionCodesTest {
    @Test fun actionCodesNeverCollideWithAlarmOrEachOther() {
        val ids = listOf(1L, 2L, 1_700_000_000_000L, 1_700_000_000_001L, 99L, Long.MAX_VALUE / 3)
        for (id in ids) for (k in 0..1) {
            val all = listOf(
                EventAlarms.code(id, 0), EventAlarms.code(id, 1),
                EventAlarms.actionCode(id, k, 10), EventAlarms.actionCode(id, k, 20), EventAlarms.actionCode(id, k, 30)
            )
            // أكواد k=0/1 للمنبه ≠ أكواد الأزرار/التأجيل لنفس k
            assertTrue("collision for id=" + id + " k=" + k, all.toSet().size == all.size)
        }
    }

    @Test fun codesArePositive() {
        for (id in listOf(1L, 1_700_000_000_000L, Long.MAX_VALUE)) {
            assertTrue(EventAlarms.actionCode(id, 1, 30) >= 0)
        }
    }

    @Test fun snoozeIsOneHour() = assertEquals(3_600_000L, EventAlarms.SNOOZE_MS)
}
