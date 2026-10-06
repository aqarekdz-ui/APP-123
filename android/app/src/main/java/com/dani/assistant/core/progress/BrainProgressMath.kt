package com.dani.assistant.core.progress

import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import java.util.Calendar

/** حساب نسبة إنجاز مهام اليوم (منطق صافي بلا Context). */
object BrainProgressMath {

    data class Result(val done: Int, val total: Int) {
        val percent: Int get() = percent(done, total)
    }

    enum class Stage(val label: String, val glow: Float, val level: Int) {
        EMPTY("ما كاينش مهام اليوم", 0.30f, 0),
        START("البداية", 0.35f, 0),
        ON_WAY("في الطريق", 0.50f, 1),
        GREAT("تقدم ممتاز", 0.65f, 2),
        ALMOST("تقريباً كملت", 0.80f, 3),
        COMPLETE("المهمة تمّت ✅", 1.00f, 4)
    }

    fun percent(done: Int, total: Int): Int {
        if (total <= 0) return 0
        return (done * 100 / total).coerceIn(0, 100)
    }

    fun stage(percent: Int, total: Int): Stage {
        if (total <= 0) return Stage.EMPTY
        return when {
            percent >= 100 -> Stage.COMPLETE
            percent >= 81 -> Stage.ALMOST
            percent >= 51 -> Stage.GREAT
            percent >= 21 -> Stage.ON_WAY
            else -> Stage.START
        }
    }

    fun dayStart(now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * مهام اليوم = (استحقاقها اليوم) أو (متأخرة وما اتنجزتش) أو (اتنجزت اليوم).
     * الملغاة ما تتحسبش. مهمة بلا تاريخ ما اتنجزتش = ما تتحسبش.
     */
    fun compute(tasks: List<Task>, now: Long): Result {
        val start = dayStart(now)
        val end = start + 24L * 60L * 60L * 1000L
        var total = 0
        var done = 0
        for (t in tasks) {
            if (t.status == TaskStatus.CANCELLED) continue
            val due = t.dueDate
            val ca = t.completedAt
            val dueToday = due != null && due >= start && due < end
            val overduePending = !t.isCompleted && due != null && due < start
            val completedToday = t.isCompleted && ca != null && ca >= start && ca < end
            if (dueToday || overduePending || completedToday) {
                total++
                if (t.isCompleted) done++
            }
        }
        return Result(done, total)
    }
}
