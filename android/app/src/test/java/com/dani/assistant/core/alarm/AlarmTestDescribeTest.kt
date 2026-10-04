package com.dani.assistant.core.alarm

import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTestDescribeTest {
    private val planned = 1_000_000L

    @Test fun neverRun() = assertTrue(AlarmTest.describe(0, 0, planned).contains("ما درت"))

    @Test fun waiting() {
        val r = AlarmTest.describe(planned, 0, planned - 20_000)
        assertTrue(r, r.contains("20 ثانية"))
    }

    @Test fun firedOnTime() = assertTrue(AlarmTest.describe(planned, planned + 3_000, planned + 5_000).startsWith("✅"))

    @Test fun firedLate() {
        val r = AlarmTest.describe(planned, planned + 90_000, planned + 100_000)
        assertTrue(r, r.startsWith("⚠️") && r.contains("90"))
    }

    @Test fun notFiredSoonAfterPlannedThenFailed() {
        assertTrue(AlarmTest.describe(planned, 0, planned + 60_000).contains("لسا"))
        assertTrue(AlarmTest.describe(planned, 0, planned + 300_000).startsWith("❌"))
    }
}
