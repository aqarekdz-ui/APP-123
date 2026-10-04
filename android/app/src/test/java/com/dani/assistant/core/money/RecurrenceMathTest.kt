package com.dani.assistant.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceMathTest {
    private val cur = 2026 * 12 + 9 // أكتوبر 2026

    @Test fun previousMonthDone_thisMonthDue() {
        assertEquals(listOf(cur), RecurrenceMath.dueMonths(cur - 1, cur, today = 10, day = 5))
        assertEquals(listOf(cur), RecurrenceMath.dueMonths(cur - 1, cur, today = 5, day = 5))
    }

    @Test fun dayNotReachedYet_nothingForThisMonth() {
        assertEquals(emptyList<Int>(), RecurrenceMath.dueMonths(cur - 1, cur, today = 4, day = 5))
    }

    @Test fun alreadyRecordedThisMonth() {
        assertEquals(emptyList<Int>(), RecurrenceMath.dueMonths(cur, cur, today = 20, day = 5))
    }

    @Test fun missedMonthsAreCaughtUp() {
        assertEquals(listOf(cur - 2, cur - 1, cur), RecurrenceMath.dueMonths(cur - 3, cur, today = 20, day = 5))
        assertEquals(listOf(cur - 2, cur - 1), RecurrenceMath.dueMonths(cur - 3, cur, today = 3, day = 5))
    }

    @Test fun catchUpIsLimitedToSixMonths() {
        val r = RecurrenceMath.dueMonths(cur - 20, cur, today = 20, day = 5)
        assertEquals(6, r.size)
        assertEquals(cur - 5, r.first()); assertEquals(cur, r.last())
        assertEquals(6, RecurrenceMath.dueMonths(-1, cur, today = 20, day = 5).size)
    }

    @Test fun newItemIsNotBackdated() {
        // اليوم 10 واليوم المحدد 5: فات ← يبدأ من الشهر الجاي
        val last1 = RecurrenceMath.initialLast(cur, today = 10, day = 5)
        assertEquals(cur, last1)
        assertEquals(emptyList<Int>(), RecurrenceMath.dueMonths(last1, cur, today = 10, day = 5))
        // اليوم 3 واليوم المحدد 5: ما فاتش ← يتسجل كي يوصل اليوم
        val last2 = RecurrenceMath.initialLast(cur, today = 3, day = 5)
        assertEquals(cur - 1, last2)
        assertEquals(emptyList<Int>(), RecurrenceMath.dueMonths(last2, cur, today = 3, day = 5))
        assertEquals(listOf(cur), RecurrenceMath.dueMonths(last2, cur, today = 5, day = 5))
    }

    @Test fun yearBoundary() {
        val jan = 2027 * 12
        assertEquals(listOf(jan - 1, jan), RecurrenceMath.dueMonths(jan - 2, jan, today = 15, day = 1))
    }
}
