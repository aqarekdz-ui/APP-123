package com.dani.assistant.core.focus

import com.dani.assistant.core.money.MoneyReports
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** تصدير جلسات التركيز CSV (بدون Context): التاريخ، وقت النهاية، الدقائق، المهمة. من الأقدم للأحدث. */
object FocusReports {
    fun csv(sessions: List<FocusSession>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("التاريخ,الساعة,الدقائق,المهمة\r\n")
        for (s in sessions.sortedBy { it.end }) {
            val z = Instant.ofEpochMilli(s.end).atZone(zone)
            sb.append(z.toLocalDate().toString()).append(',')
                .append(String.format(Locale.US, "%02d:%02d", z.hour, z.minute)).append(',')
                .append(s.minutes).append(',')
                .append(MoneyReports.csvField(s.taskTitle)).append("\r\n")
        }
        return sb.toString()
    }
}
