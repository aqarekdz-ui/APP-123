package com.dani.assistant.core.events

import android.content.Context
import java.util.Calendar

/**
 * أوامر المناسبات بالكلام (محلي، بدون Gemini):
 *  "عيد ميلاد أحمد 15 مارس" | "عيد ميلاد سارة 3/5/1995" | "مناسبة عرس فلان 20 جويلية 2027"
 *  "ذكرى زواج 12 ماي" (سنوية) | "مناسباتي" | "قداش بقى لعيد ميلاد أحمد" | "الغي عيد ميلاد أحمد"
 */
object EventCommands {
    private val months = linkedMapOf(
        "جانفي" to 1, "يناير" to 1, "فيفري" to 2, "فبراير" to 2, "مارس" to 3,
        "افريل" to 4, "ابريل" to 4, "ماي" to 5, "مايو" to 5, "جوان" to 6, "يونيو" to 6,
        "جويليه" to 7, "يوليو" to 7, "اوت" to 8, "اغسطس" to 8, "سبتمبر" to 9,
        "اكتوبر" to 10, "نوفمبر" to 11, "ديسمبر" to 12, "دجنبر" to 12
    )
    private val maxDays = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    private val trigRe = Regex("عيد ميلادي|عيد ميلاد|ميلاد|مناسبه|ذكري")
    private val numDate = Regex("(\\d{1,2})\\s*[/\\-.]\\s*(\\d{1,2})(?:\\s*[/\\-.]\\s*(\\d{4}))?")
    private val textDate = Regex(
        "(\\d{1,2})\\s+(?:من\\s+|في\\s+|شهر\\s+)?(" + months.keys.joinToString("|") + ")(?![\\p{L}])(?:\\s+(\\d{4}))?"
    )
    private val yearlyRe = Regex("كل سنه|كل سنة|كل عام|سنويا")
    private val okPrefix = setOf("زيد", "ضيف", "سجل", "دير", "حط", "اضف", "ذكرني", "نبهني", "عندي", "عندنا", "لي", "ب", "ل", "من", "فضلك")
    private val stopWords = setOf(
        "ديال", "تاع", "متاع", "ل", "لـ", "هو", "هي", "يوم", "في", "ف", "عند", "ديالي", "تاعي",
        "هذا", "هاد", "ايام", "بتاريخ", "تاريخ", "من", "كل", "سنه", "عام", "سنويا"
    )

    internal data class PDate(val day: Int, val month: Int, val year: Int, val start: Int, val end: Int)

    internal fun norm(s: String): String = s.lowercase().map {
        when (it) {
            'أ', 'إ', 'آ' -> 'ا'
            'ى' -> 'ي'
            'ة' -> 'ه'
            else -> it
        }
    }.joinToString("")

    private fun has(n: String, vararg words: String): Boolean = words.any { n.contains(it) }

    private fun validDate(d: Int, m: Int): Boolean = m in 1..12 && d in 1..maxDays[m - 1]

    internal fun findDate(n: String): PDate? {
        numDate.find(n)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (validDate(d, mo)) return PDate(d, mo, y, m.range.first, m.range.last + 1)
        }
        textDate.find(n)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = months[m.groupValues[2]] ?: return null
            val y = m.groupValues[3].ifEmpty { "0" }.toInt()
            if (validDate(d, mo)) return PDate(d, mo, y, m.range.first, m.range.last + 1)
        }
        return null
    }

    fun handle(ctx: Context, s: String, n: String): String? {
        val trig = trigRe.find(n)
        val wantsList = has(n, "مناسباتي", "اعياد الميلاد", "اعياد ميلاد", "اعيادي", "مناسبات قادمه", "مناسبات الشهر", "مناسبات هذا")
        if (trig == null && !wantsList) return null
        val date = findDate(n)
        if (wantsList && date == null) return listReply(ctx)
        if (trig == null) return null

        val cancelVerb = has(n, "الغي", "امسح", "احذف", "شطب", "نحي")
        if (cancelVerb) return cancel(ctx, n, date)
        if (date != null) return add(ctx, s, n, trig, date)
        return countdown(ctx, n)
    }

    private fun listReply(ctx: Context): String {
        val ups = EventStore.upcoming(ctx)
        if (ups.isEmpty()) return "🎂 ما عندكش مناسبات. قول مثلا: \"عيد ميلاد أحمد 15 مارس\" ولا من الرئيسية ← 🎂 المناسبات."
        return "🎂 مناسباتك القادمة:\n" + ups.take(8).joinToString("\n") { lineOf(it) }
    }

    private fun lineOf(u: Upcoming): String =
        eventEmoji(u.event) + " " + u.event.name + " — " + fmtEventDate(u.event).take(5) + " (" + daysText(u.daysLeft) + ")" +
            (if (u.age > 0) " • " + u.age + " سنة" else "")

    private fun countdown(ctx: Context, n: String): String? {
        val hit = EventStore.upcoming(ctx).firstOrNull { it.event.name.length >= 2 && n.contains(norm(it.event.name)) } ?: return null
        return lineOf(hit)
    }

    private fun cancel(ctx: Context, n: String, date: PDate?): String? {
        val all = EventStore.list(ctx)
        var hits = all.filter { it.name.length >= 2 && n.contains(norm(it.name)) }
        if (hits.isEmpty() && date != null) hits = all.filter { it.day == date.day && it.month == date.month }
        if (hits.isEmpty()) return "ما لقيتش مناسبة بهاد الاسم. قول \"مناسباتي\" باش تشوف اللي عندك."
        hits.forEach { EventAlarms.cancel(ctx, it); EventStore.delete(ctx, it.id) }
        return "🗑 لغيت: " + hits.joinToString("، ") { it.name }
    }

    private fun add(ctx: Context, s: String, n: String, trig: MatchResult, date: PDate): String? {
        if (date.start < trig.range.last + 1) return null
        val prefix = n.substring(0, trig.range.first).trim()
        if (prefix.isNotEmpty() && prefix.split(Regex("\\s+")).any { it !in okPrefix }) return null

        val occasion = trig.value == "مناسبه" || trig.value == "ذكري"
        val src = if (s.length == n.length) s else n
        var seg = src.substring(trig.range.last + 1, date.start)
        if (seg.isBlank() && date.end <= src.length) seg = src.substring(date.end)
        seg = yearlyRe.replace(seg, " ")
        val tokens = seg.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && norm(tokens.first()) in stopWords) tokens.removeAt(0)
        while (tokens.isNotEmpty() && norm(tokens.last()) in stopWords) tokens.removeAt(tokens.size - 1)
        var name = tokens.joinToString(" ").trim()
        if (name.isEmpty()) {
            if (trig.value == "عيد ميلادي") name = "أنا"
            else return "شكون؟ مثال: \"عيد ميلاد أحمد 15 مارس\""
        }
        if (name.length > 40) return null

        val cy = Calendar.getInstance().get(Calendar.YEAR)
        val yearly = !occasion || has(n, "كل سنه", "كل عام", "سنويا") || trig.value == "ذكري"
        val kind = if (occasion) "occasion" else "birthday"
        var year = 0
        if (yearly) {
            if (date.year > cy) return "السنة " + date.year + " مستقبلية، تاريخ البداية لازم يكون فات."
            if (date.year >= 1900) year = date.year
        } else {
            val probe = LifeEvent(0, name, kind, date.day, date.month, cy, false)
            year = if (date.year > 0) date.year
            else if (EventStore.occurrence(probe, cy).timeInMillis >= EventStore.startOfToday()) cy else cy + 1
        }

        val all = EventStore.list(ctx)
        val old = all.firstOrNull { norm(it.name) == norm(name) && it.day == date.day && it.month == date.month }
        val ev = LifeEvent(old?.id ?: System.currentTimeMillis(), name, kind, date.day, date.month, year, yearly, old?.createdAt ?: System.currentTimeMillis())
        if (!yearly && EventStore.nextDate(ev) == null) return "هاد التاريخ فات (" + fmtEventDate(ev) + ")."

        EventStore.save(ctx, ev)
        EventAlarms.schedule(ctx, ev)
        val next = EventStore.nextDate(ev)
        val left = if (next != null) Math.round((next - EventStore.startOfToday()) / 86_400_000.0).toInt() else -1
        return eventEmoji(ev) + (if (old != null) " حدّثت " else " سجلت ") + (if (occasion) "" else "عيد ميلاد ") + name +
            " — " + String.format("%02d/%02d", ev.day, ev.month) + (if (yearly) " كل سنة" else "") +
            (if (left >= 0) " (" + daysText(left) + ")" else "") +
            "\nنفكّرك قبل بيوم وفي نفس النهار." +
            (if (occasion && !yearly) "\n(باش تتكرر كل سنة زيد \"كل سنة\" في الأمر)" else "")
    }
}
