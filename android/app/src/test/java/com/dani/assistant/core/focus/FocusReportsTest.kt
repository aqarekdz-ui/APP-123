package com.dani.assistant.core.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusReportsTest {
    private val zone = ZoneId.of("UTC")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun emptyHasOnlyHeader() {
        val c = FocusReports.csv(emptyList(), zone)
        assertTrue(c.startsWith("\uFEFF"))
        assertEquals("التاريخ,الساعة,الدقائق,المهمة", c.removePrefix("\uFEFF").trim())
    }

    @Test fun sortedOldestFirstWithFormatting() {
        val s = listOf(
            FocusSession(2, at(2026, 10, 6, 9, 5), 25, "كتابة, تقرير"),
            FocusSession(1, at(2026, 10, 5, 22, 30), 50, "")
        )
        val r = FocusReports.csv(s, zone).removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals(3, r.size)
        assertEquals("2026-10-05,22:30,50,", r[1])
        assertEquals("2026-10-06,09:05,25,\"كتابة, تقرير\"", r[2])
    }

    @Test fun taskTitleInjectionProtected() {
        val r = FocusReports.csv(listOf(FocusSession(1, at(2026, 1, 1, 0, 0), 15, "@SUM(A1)")), zone)
        assertTrue(r.contains(",'@SUM(A1)"))
    }
}
