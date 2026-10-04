package com.dani.assistant.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyWidgetLogicTest {
    private fun h(n: String, done: Boolean, s: Int = 0, e: String = "") = WHabit(n.hashCode().toLong(), e, n, done, s)
    private fun d(n: String, min: Int, taken: Boolean) = WDose(1, min, n, taken)

    @Test fun habitLineStatesAndStreak() {
        assertEquals("☐ 🏃 جري", DailyWidgetLogic.habitLine(h("جري", false, 0, "🏃")))
        assertEquals("☑ 🏃 جري  🔥5", DailyWidgetLogic.habitLine(h("جري", true, 5, "🏃")))
        assertEquals("☐ قراءة", DailyWidgetLogic.habitLine(h("قراءة", false)))
        assertTrue(!DailyWidgetLogic.habitLine(h("x", false, 1)).contains("🔥"))
    }

    @Test fun doseLineFormatsTime() {
        assertEquals("☐ 💊 08:05  دواء", DailyWidgetLogic.doseLine(d("دواء", 8 * 60 + 5, false)))
        assertEquals("☑ 💊 21:30  فيتامين", DailyWidgetLogic.doseLine(d("فيتامين", 21 * 60 + 30, true)))
    }

    @Test fun pickHabitsPutsUndoneFirstAndCapsAtFour() {
        val r = DailyWidgetLogic.pickHabits(listOf(h("a", true), h("b", true), h("c", false), h("d", false), h("e", true), h("f", false)))
        assertEquals(4, r.size)
        assertEquals(listOf("c", "d", "f"), r.take(3).map { it.name })
    }

    @Test fun pickDosesKeepsUntakenAndSortsByTime() {
        val all = listOf(d("a", 480, true), d("b", 600, false), d("c", 720, false), d("d", 840, false), d("e", 900, false), d("f", 300, true))
        val r = DailyWidgetLogic.pickDoses(all)
        assertEquals(listOf("b", "c", "d", "e"), r.map { it.name })
        val few = DailyWidgetLogic.pickDoses(listOf(d("z", 900, false), d("y", 100, true)))
        assertEquals(listOf("y", "z"), few.map { it.name })
    }

    @Test fun titleCounts() {
        assertEquals("DANI · اليوم", DailyWidgetLogic.title(emptyList(), emptyList()))
        assertEquals("DANI · اليوم  ✅ 1/2  💊 0/1",
            DailyWidgetLogic.title(listOf(h("a", true), h("b", false)), listOf(d("x", 480, false))))
    }
}
