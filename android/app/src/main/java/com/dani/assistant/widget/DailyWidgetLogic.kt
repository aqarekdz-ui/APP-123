package com.dani.assistant.widget

import java.util.Locale

data class WHabit(val id: Long, val emoji: String, val name: String, val done: Boolean, val streak: Int)
data class WDose(val medId: Long, val minute: Int, val name: String, val taken: Boolean)

/** منطق صافي لويدجت "اليوم" (يتجرّب بـ JUnit). */
object DailyWidgetLogic {
    const val MAX_HABITS = 4
    const val MAX_DOSES = 4

    fun habitLine(h: WHabit): String =
        (if (h.done) "☑ " else "☐ ") + (if (h.emoji.isNotBlank()) h.emoji + " " else "") + h.name.take(24) +
            (if (h.streak >= 2) "  🔥" + h.streak else "")

    fun doseLine(d: WDose): String =
        (if (d.taken) "☑ " else "☐ ") + "💊 " + String.format(Locale.US, "%02d:%02d", d.minute / 60, d.minute % 60) + "  " + d.name.take(22)

    /** العادات: غير المنجزة أولاً، وبعدها المنجزة، بحد أقصى [MAX_HABITS]. */
    fun pickHabits(all: List<WHabit>): List<WHabit> =
        all.sortedBy { it.done }.take(MAX_HABITS)

    /** الجرعات: غير المأخوذة أولاً (حسب الوقت)، وبعدها المأخوذة، ثم نرتب المختارة حسب الوقت. */
    fun pickDoses(all: List<WDose>): List<WDose> =
        all.sortedWith(compareBy<WDose>({ it.taken }, { it.minute })).take(MAX_DOSES).sortedBy { it.minute }

    fun title(habits: List<WHabit>, doses: List<WDose>): String {
        val hd = habits.count { it.done }
        val dt = doses.count { it.taken }
        val parts = ArrayList<String>()
        if (habits.isNotEmpty()) parts.add("✅ " + hd + "/" + habits.size)
        if (doses.isNotEmpty()) parts.add("💊 " + dt + "/" + doses.size)
        return "DANI · اليوم" + (if (parts.isEmpty()) "" else "  " + parts.joinToString("  "))
    }
}
