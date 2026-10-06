package com.dani.assistant.core.meds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MedReportsTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 6)

    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun rows(csv: String) = csv.removePrefix("\uFEFF").trim().split("\r\n")

    @Test fun headerAndBom() {
        val c = MedReports.csv(emptyList(), emptySet(), today, 600, 3, zone)
        assertTrue(c.startsWith("\uFEFF"))
        assertEquals(listOf("التاريخ,الدواء,الجرعة,الوقت,الحالة"), rows(c))
    }

    @Test fun takenMissedUpcoming() {
        val m = Med(1, "Doliprane", "500mg", listOf(8 * 60, 20 * 60), true, 0L, ms(today.minusDays(5)))
        val taken = setOf("2026-10-05|1|480", "2026-10-06|1|480")
        val r = rows(MedReports.csv(listOf(m), taken, today, 12 * 60, 2, zone))
        assertEquals(5, r.size) // header + 4 جرعات (يومين × 2)
        assertEquals("2026-10-05,Doliprane,500mg,08:00,${MedReports.TAKEN}", r[1])
        assertEquals("2026-10-05,Doliprane,500mg,20:00,${MedReports.MISSED}", r[2])
        assertEquals("2026-10-06,Doliprane,500mg,08:00,${MedReports.TAKEN}", r[3])
        assertEquals("2026-10-06,Doliprane,500mg,20:00,${MedReports.UPCOMING}", r[4])
    }

    @Test fun noRowsBeforeCreationOrAfterEnd() {
        val created = Med(1, "A", "", listOf(480), true, 0L, ms(today))
        val ended = Med(2, "B", "", listOf(480), true, ms(today.minusDays(1)), ms(today.minusDays(10)))
        val r = rows(MedReports.csv(listOf(created, ended), emptySet(), today, 1000, 3, zone))
        // A: اليوم فقط. B: من 04 لين 05.
        val a = r.filter { it.contains(",A,") }
        val b = r.filter { it.contains(",B,") }
        assertEquals(1, a.size)
        assertEquals(listOf("2026-10-04", "2026-10-05"), b.map { it.substringBefore(',') })
    }

    @Test fun takenOfPausedOrDeletedMedStillListed() {
        val paused = Med(1, "P", "", listOf(480), false, 0L, ms(today.minusDays(9)))
        val taken = setOf("2026-10-05|1|480", "2026-10-05|99|600")
        val r = rows(MedReports.csv(listOf(paused), taken, today, 1000, 3, zone))
        assertEquals(3, r.size)
        assertTrue(r.any { it == "2026-10-05,P,,08:00,${MedReports.TAKEN}" })
        assertTrue(r.any { it == "2026-10-05,(محذوف),,10:00,${MedReports.TAKEN}" })
    }

    @Test fun outOfRangeAndBadKeysIgnored() {
        val m = Med(1, "A", "", listOf(480), true, 0L, ms(today.minusDays(30)))
        val taken = setOf("2026-09-01|1|480", "garbage", "x|y|z", "2026-10-06|1|480")
        val r = rows(MedReports.csv(listOf(m), taken, today, 1000, 2, zone))
        assertEquals(3, r.size)
        assertTrue(r.none { it.startsWith("2026-09-01") })
    }

    @Test fun csvInjectionProtected() {
        val m = Med(1, "=cmd", "a,b", listOf(480), true, 0L, ms(today))
        val r = rows(MedReports.csv(listOf(m), emptySet(), today, 1000, 1, zone))
        assertEquals("2026-10-06,'=cmd,\"a,b\",08:00,${MedReports.MISSED}", r[1])
    }
}
