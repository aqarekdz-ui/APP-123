package com.dani.assistant.core.progress

import com.dani.assistant.core.progress.BrainProgressMath.Stage
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BrainProgressMathTest {
    private val now = 1_800_000_000_000L
    private val start = BrainProgressMath.dayStart(now)
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun t(status: TaskStatus, due: Long? = start + 3_600_000L, completedAt: Long? = null) =
        Task(title = "x", status = status, dueDate = due, completedAt = completedAt)

    @Test fun emptyIsZero() {
        val r = BrainProgressMath.compute(emptyList(), now)
        assertEquals(0, r.total); assertEquals(0, r.percent)
        assertEquals(Stage.EMPTY, BrainProgressMath.stage(r.percent, r.total))
    }

    @Test fun fiveOfSevenIs71() {
        val l = (1..5).map { t(TaskStatus.COMPLETED, completedAt = now) } + (1..2).map { t(TaskStatus.NEW) }
        val r = BrainProgressMath.compute(l, now)
        assertEquals(7, r.total); assertEquals(5, r.done); assertEquals(71, r.percent)
    }

    @Test fun allDoneIs100() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.COMPLETED, completedAt = now)), now)
        assertEquals(100, r.percent)
        assertEquals(Stage.COMPLETE, BrainProgressMath.stage(r.percent, r.total))
    }

    @Test fun percentNeverAbove100() {
        assertEquals(100, BrainProgressMath.percent(9, 3))
        assertEquals(0, BrainProgressMath.percent(-1, 3))
    }

    @Test fun cancelledIgnored() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.CANCELLED), t(TaskStatus.NEW)), now)
        assertEquals(1, r.total)
    }

    @Test fun overduePendingCounted() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.NEW, due = start - dayMs)), now)
        assertEquals(1, r.total); assertEquals(0, r.done)
    }

    @Test fun undatedPendingIgnored() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.NEW, due = null)), now)
        assertEquals(0, r.total)
    }

    @Test fun completedYesterdayNotCounted() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.COMPLETED, due = start - dayMs, completedAt = start - 1000L)), now)
        assertEquals(0, r.total)
    }

    @Test fun futureTaskNotCounted() {
        val r = BrainProgressMath.compute(listOf(t(TaskStatus.NEW, due = start + dayMs + 1000L)), now)
        assertEquals(0, r.total)
    }

    @Test fun stageBoundaries() {
        assertEquals(Stage.START, BrainProgressMath.stage(0, 5))
        assertEquals(Stage.START, BrainProgressMath.stage(20, 5))
        assertEquals(Stage.ON_WAY, BrainProgressMath.stage(21, 5))
        assertEquals(Stage.ON_WAY, BrainProgressMath.stage(50, 5))
        assertEquals(Stage.GREAT, BrainProgressMath.stage(51, 5))
        assertEquals(Stage.GREAT, BrainProgressMath.stage(80, 5))
        assertEquals(Stage.ALMOST, BrainProgressMath.stage(81, 5))
        assertEquals(Stage.ALMOST, BrainProgressMath.stage(99, 5))
        assertEquals(Stage.COMPLETE, BrainProgressMath.stage(100, 5))
    }
}
