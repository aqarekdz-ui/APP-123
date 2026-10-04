package com.dani.assistant.core.life

import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DayPlannerTest {
    // الأحد 4 أكتوبر 2026
    private fun at(h: Int, m: Int, dayOffset: Int = 0): Long {
        val c = Calendar.getInstance()
        c.clear()
        c.set(2026, Calendar.OCTOBER, 4 + dayOffset, h, m, 0)
        return c.timeInMillis
    }

    private fun plan(tasks: List<Task>, offset: Int = 0, now: Long = at(7, 0)) = DayPlanner.render(tasks, offset, now)

    private fun t(title: String, due: Long? = null, p: PriorityLevel = PriorityLevel.MEDIUM, min: Int = 30, st: TaskStatus = TaskStatus.NEW) =
        Task(title = title, dueDate = due, priority = p, estimatedMinutes = min, status = st)

    @Test fun emptyDay() {
        assertTrue(plan(emptyList()).contains("ما عندكش مهام"))
    }

    @Test fun completedAndCancelledAreIgnored() {
        val r = plan(listOf(
            t("done", at(10, 0), st = TaskStatus.COMPLETED),
            t("cancelled", at(11, 0), st = TaskStatus.CANCELLED)
        ))
        assertTrue(r.contains("ما عندكش مهام"))
    }

    @Test fun fixedTaskKeepsItsHour() {
        val r = plan(listOf(t("اجتماع", at(10, 0), min = 60)))
        assertTrue(r, r.contains("10:00–11:00"))
        assertTrue(r.contains("⏰"))
        assertTrue(r.contains("اجتماع"))
    }

    @Test fun flexibleTaskStartsAtWindowStart() {
        val r = plan(listOf(t("كتابة", at(0, 0))))
        assertTrue(r, r.contains("08:00–08:30"))
        assertFalse(r.contains("00:00"))
    }

    @Test fun higherPriorityGoesFirstWithBreak() {
        val r = plan(listOf(t("عادية", at(0, 0), PriorityLevel.LOW), t("عاجلة", at(0, 0), PriorityLevel.URGENT_CRITICAL)))
        assertTrue(r, r.indexOf("عاجلة") < r.indexOf("عادية"))
        assertTrue(r, r.contains("08:00–08:30"))
        assertTrue(r, r.contains("08:40–09:10"))
    }

    @Test fun flexibleTaskAvoidsFixedSlot() {
        val r = plan(listOf(t("ثابتة", at(8, 0), min = 60), t("مرنة", at(0, 0))))
        assertTrue(r, r.contains("08:00–09:00"))
        assertTrue(r, r.contains("09:10–09:40"))
    }

    @Test fun overdueTasksAppearTodayOnly() {
        val overdue = t("متأخرة", at(9, 0, -1))
        assertTrue(plan(listOf(overdue)).contains("(متأخرة)"))
        assertTrue(plan(listOf(overdue), offset = 1).contains("ما عندكش مهام"))
    }

    @Test fun tomorrowUsesItsOwnTitleAndTasks() {
        val r = plan(listOf(t("بكري", at(9, 0, 1))), offset = 1)
        assertTrue(r, r.contains("غدوة"))
        assertTrue(r, r.contains("09:00–09:30"))
    }

    @Test fun undatedOnlyIfImportant() {
        assertTrue(plan(listOf(t("عادية", null, PriorityLevel.MEDIUM))).contains("ما عندكش مهام"))
        assertTrue(plan(listOf(t("مهمة", null, PriorityLevel.IMPORTANT))).contains("مهمة"))
    }

    @Test fun lateInTheDayTasksAreDropped() {
        val r = plan(listOf(t("متأخرة بزاف", at(0, 0))), now = at(20, 50))
        assertTrue(r, r.contains("ما لقيتش وقت"))
    }

    @Test fun durationIsClamped() {
        val r = plan(listOf(t("طويلة", at(0, 0), min = 600)))
        assertTrue(r, r.contains("08:00–11:00"))
    }
}
