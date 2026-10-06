package com.dani.assistant.core.meds

import com.dani.assistant.core.money.MoneyReports
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** تصدير سجل الأدوية CSV (بدون Context): صف لكل جرعة (التاريخ، الدواء، الجرعة، الوقت، الحالة). */
object MedReports {
    const val TAKEN = "أُخذت"
    const val MISSED = "فاتت"
    const val UPCOMING = "قادمة"

    private data class Key(val day: String, val med: Long, val t: Int)

    private fun createdDay(m: Med, zone: ZoneId): LocalDate = Instant.ofEpochMilli(m.createdAt).atZone(zone).toLocalDate()

    /**
     * [taken] مفاتيح السجل بصيغة "yyyy-MM-dd|medId|minute". المتوقع = الأدوية النشطة (من يوم إنشائها لين endAt)،
     * والمأخوذ خارج المتوقع (دواء موقوف/محذوف) يظهر هو بعد. اليوم الحالي: الجرعة اللي وقتها ما جاش بعد = قادمة.
     */
    fun csv(
        meds: List<Med>, taken: Set<String>, today: LocalDate, nowMinute: Int,
        days: Int = 90, zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("التاريخ,الدواء,الجرعة,الوقت,الحالة\r\n")
        val from = today.minusDays((days - 1).toLong())
        val byId = meds.associateBy { it.id }
        val keys = LinkedHashSet<Key>()
        var d = from
        while (!d.isAfter(today)) {
            for (m in meds) {
                if (!m.active || createdDay(m, zone).isAfter(d)) continue
                if (m.endAt != 0L && Instant.ofEpochMilli(m.endAt).atZone(zone).toLocalDate().isBefore(d)) continue
                for (t in m.times) keys.add(Key(d.toString(), m.id, t))
            }
            d = d.plusDays(1)
        }
        for (k in taken) {
            val p = k.split("|")
            if (p.size != 3) continue
            val day = try { LocalDate.parse(p[0]) } catch (e: Exception) { continue }
            if (day.isBefore(from) || day.isAfter(today)) continue
            val med = p[1].toLongOrNull() ?: continue
            val t = p[2].toIntOrNull() ?: continue
            keys.add(Key(p[0], med, t))
        }
        val rows = keys.sortedWith(compareBy<Key>({ it.day }, { it.t }, { it.med }))
        for (k in rows) {
            val m = byId[k.med]
            val isTaken = taken.contains(k.day + "|" + k.med + "|" + k.t)
            val status = when {
                isTaken -> TAKEN
                k.day == today.toString() && k.t > nowMinute -> UPCOMING
                else -> MISSED
            }
            sb.append(k.day).append(',')
                .append(MoneyReports.csvField(m?.name ?: "(محذوف)")).append(',')
                .append(MoneyReports.csvField(m?.dose ?: "")).append(',')
                .append(fmtMinute(k.t)).append(',')
                .append(status).append("\r\n")
        }
        return sb.toString()
    }
}
