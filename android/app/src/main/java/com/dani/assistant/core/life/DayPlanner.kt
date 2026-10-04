package com.dani.assistant.core.life

import com.dani.assistant.DaniApplication
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * خطة اليوم (محلي بلا Gemini):
 *  - المهام اللي لها ساعة محددة تبقى ثابتة في وقتها.
 *  - الباقي (المتأخرة، مهام اليوم بلا ساعة، والمهمة المهمة بلا تاريخ) يترتب بالأولوية ويتوزع على الأوقات الفارغة.
 */
object DayPlanner {
    private const val START_HOUR = 8
    private const val END_HOUR = 21
    private const val BREAK_MIN = 10
    private const val MIN = 60_000L

    private data class Slot(val task: Task, val start: Long, val end: Long, val fixed: Boolean)

    private fun dayStart(offset: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun hasTime(ms: Long): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return c.get(Calendar.HOUR_OF_DAY) != 0 || c.get(Calendar.MINUTE) != 0
    }

    private fun icon(p: PriorityLevel): String = when (p) {
        PriorityLevel.URGENT_CRITICAL -> "🔴"
        PriorityLevel.IMPORTANT -> "🟠"
        PriorityLevel.MEDIUM -> "🟡"
        else -> "⚪"
    }

    private fun dur(t: Task): Long = t.estimatedMinutes.coerceIn(10, 180) * MIN

    suspend fun plan(offset: Int): String {
        val all = DaniApplication.instance.taskRepository.getAllTasks().first()
            .filter { !it.isCompleted && it.status.name != "CANCELLED" }
        val a = dayStart(offset)
        val b = dayStart(offset + 1)
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val title = if (offset == 0) "اليوم" else "غدوة"

        val todayTasks = all.filter { t -> val d = t.dueDate; d != null && d >= a && d < b }
        val fixedTasks = todayTasks.filter { hasTime(it.dueDate ?: 0L) }
        val noTime = todayTasks.filter { !hasTime(it.dueDate ?: 0L) }
        val overdue = if (offset == 0) all.filter { t -> val d = t.dueDate; d != null && d < a } else emptyList()
        val undated = all.filter { it.dueDate == null && (it.priority == PriorityLevel.URGENT_CRITICAL || it.priority == PriorityLevel.IMPORTANT) }
            .sortedBy { it.priority.level }.take(3)

        val flexible = (overdue + noTime + undated).sortedWith(compareBy<Task>({ it.priority.level }, { it.dueDate ?: Long.MAX_VALUE }))
        if (fixedTasks.isEmpty() && flexible.isEmpty()) return "✅ ما عندكش مهام تتخطط " + title + ". نهار فاضي!"

        val fixed = fixedTasks.sortedBy { it.dueDate }.map { Slot(it, it.dueDate ?: 0L, (it.dueDate ?: 0L) + dur(it), true) }

        val winStart = a + START_HOUR * 60 * MIN
        val winEnd = a + END_HOUR * 60 * MIN
        var cursor = winStart
        if (offset == 0) {
            val q = 15 * MIN
            val now = (System.currentTimeMillis() + q - 1) / q * q
            if (now > cursor) cursor = now
        }

        val placed = ArrayList<Slot>(fixed)
        val dropped = ArrayList<Task>()
        for (t in flexible) {
            val d = dur(t)
            var moved = true
            while (moved) {
                moved = false
                for (f in fixed) {
                    if (cursor < f.end && cursor + d > f.start) { cursor = f.end + BREAK_MIN * MIN; moved = true }
                }
            }
            if (cursor + d > winEnd) { dropped.add(t); continue }
            placed.add(Slot(t, cursor, cursor + d, false))
            cursor += d + BREAK_MIN * MIN
        }

        val sorted = placed.sortedBy { it.start }
        val sb = StringBuilder("📋 خطة ").append(title).append(" (").append(sorted.size).append(" مهام):")
        sorted.forEach { s ->
            sb.append("\n").append(fmt.format(Date(s.start))).append("–").append(fmt.format(Date(s.end))).append(" ")
                .append(icon(s.task.priority)).append(" ").append(s.task.title.take(60))
            if (s.fixed) sb.append(" ⏰")
            else if ((s.task.dueDate ?: Long.MAX_VALUE) < a) sb.append(" (متأخرة)")
        }
        if (dropped.isNotEmpty()) {
            sb.append("\n\n⚠️ ما لقيتش وقت ").append(title).append(" لـ: ").append(dropped.joinToString("، ") { it.title.take(30) })
                .append("\n(أجّلها ولا قلّل مدة مهام أخرى)")
        }
        val totalMin = placed.sumOf { (it.end - it.start) / MIN }
        sb.append("\n\n⏱ مجموع العمل: ").append(totalMin / 60).append("س ").append(totalMin % 60).append("د")
        sb.append("\n⏰ = ساعة ثابتة، الباقي أوقات مقترحة (بين 08:00 و21:00).")
        return sb.toString()
    }
}
