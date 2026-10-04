package com.dani.assistant.core.life

import android.content.Context
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.habits.Habit
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * أوامر الحياة بالكلام (محلي 100%، بدون Gemini):
 *  - تسجيل: مصروف، دخل، دين (عندو لي / عليّ)، تسديد دين، إنجاز عادة، إلغاء آخر عملية.
 *  - أسئلة: مهام اليوم، العادات، الديون، المصاريف.
 * يرجع نص الرد، أو null إذا الرسالة ما تخصّش هذي الأوامر (فتروح لـ Gemini).
 */
object LifeCommands {
    private const val PREFS = "dani_life"

    private val diacritics = Regex("[\u064B-\u0652\u0640]")
    private val tokenRe = Regex("\\S+")

    private fun clean(raw: String): String {
        val sb = StringBuilder()
        for (ch in diacritics.replace(raw, "")) {
            sb.append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    else -> ch
                }
            )
        }
        return sb.toString().trim()
    }

    private fun norm(s: String): String = s.lowercase().map {
        when (it) {
            'أ', 'إ', 'آ' -> 'ا'
            'ى' -> 'ي'
            'ة' -> 'ه'
            else -> it
        }
    }.joinToString("")

    private fun has(n: String, vararg words: String): Boolean = words.any { n.contains(it) }

    // ---------------- amounts ----------------
    private val units = setOf(
        "كيلو", "كغ", "kg", "لتر", "l", "حبه", "حبات", "قطعه", "قطع", "متر", "م", "غرفه", "غرف", "يوم", "ايام",
        "سنه", "سنين", "ساعه", "ساعات", "دقيقه", "دقايق", "شخص", "اشخاص", "مره", "مرات", "مليون", "مليار", "ملايين", "million"
    )
    private val currencyWords = setOf("دج", "دينار", "دنانير", "da", "dzd", "دين", "الاف", "الف", "k")
    private val amountRe = Regex("(\\d{1,3}(?:[ ,.]\\d{3})+|\\d+)(?:\\s*(الاف|الف|k)(?![\\p{L}]))?")
    private val sepRe = Regex("[ ,.]")

    private data class Amt(val value: Long, val start: Int, val end: Int)

    private fun findAmount(n: String): Amt? {
        for (m in amountRe.findAll(n)) {
            val rawNum = m.groupValues[1].replace(sepRe, "")
            if (rawNum.length >= 9) continue // رقم هاتف
            var v = rawNum.toLongOrNull() ?: continue
            if (m.groupValues[2].isNotEmpty()) v *= 1000
            val after = n.substring(m.range.last + 1).trim().split(Regex("\\s+")).firstOrNull() ?: ""
            if (after in units) continue
            if (v < 10 || v > 1_000_000_000L) continue
            return Amt(v, m.range.first, m.range.last + 1)
        }
        val w = Regex("(?<![\\p{L}])(الفين|الف)(?![\\p{L}])").find(n)
        if (w != null) return Amt(if (w.groupValues[1] == "الفين") 2000L else 1000L, w.range.first, w.range.last + 1)
        return null
    }

    // ---------------- helpers ----------------
    private fun lastPrefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cleanPerson(p: String): String {
        var x = p.trim().trim('،', ',', '.', '؟', '?', '!', ':')
        if (x.length > 3 && x[0] == 'ل' && x[1] in "أإآا") x = x.substring(1)
        return x
    }

    private fun personAfter(n: String, s: String, verbs: Regex): String? {
        val m = verbs.find(n) ?: return null
        for (t in tokenRe.findAll(n, m.range.last + 1)) {
            val w = t.value
            if (w == "ل" || w == "لـ" || w == "من" || w == "لي" || w == "دين") continue
            if (w.all { it.isDigit() || it == ',' || it == '.' } || w in units || w in currencyWords) continue
            val p = cleanPerson(s.substring(t.range.first, t.range.last + 1))
            return if (p.length >= 2) p else null
        }
        return null
    }

    private fun monthRange(offset: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, offset)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return start to c.timeInMillis
    }

    private fun dayRange(offset: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, offset)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        return start to c.timeInMillis
    }

    private fun expenseCategory(n: String): String = when {
        has(n, "قهوه", "اكل", "مطعم", "خبز", "بيتزا", "ساندويتش", "شاي", "حليب", "خضره", "خضرة", "فواكه", "لحم", "غداء", "عشاء", "فطور", "طعام", "سوق", "courses", "coffee", "pizza") -> "أكل"
        has(n, "بنزين", "essence", "طاكسي", "taxi", "ترامواي", "حافله", "ميترو", "وقود", "مازوت", "سياره") -> "مواصلات"
        has(n, "فاتوره", "كهرباء", "غاز", "سونلغاز", "اتصالات", "موبيليس", "جازي", "اوريدو", "انترنت") -> "فواتير"
        has(n, "دواء", "ادويه", "طبيب", "دكتور", "صيدليه", "مستشفي", "تحاليل", "عياده", "اشعه") -> "صحة"
        has(n, "سينما", "لعبه", "خرجه", "رحله", "نتفليكس", "netflix") -> "ترفيه"
        has(n, "شغل", "اعلان", "اشهار", "دعايه", "مكتب", "publicite", "ads", "فيسبوك") -> "شغل"
        has(n, "كراء", "ايجار", "اثاث", "سباك", "كهربائي", "مفروشات", "تصليح") -> "بيت"
        else -> "أخرى"
    }

    private fun incomeCategory(n: String): String = when {
        has(n, "راتب", "شهريه", "salaire") -> "راتب"
        has(n, "شغل", "خدمه", "عموله", "commission") -> "شغل"
        has(n, "بعت", "مبيعات", "سلعه") -> "مبيعات"
        else -> "أخرى"
    }

    private val noteStrip = Regex("(صرفت|دفعت|شريت|كلفني|قبضت|ربحت|دخلولي|دخلتلي|سلفت|سلفني|دج|دينار|دنانير|dzd|\\bda\\b|الاف|آلاف|ألف|الف)", RegexOption.IGNORE_CASE)
    private val notePrefix = Regex("^(على|علي|في|بـ|ب|لـ|ل|من)\\s+")

    private fun noteOf(s: String, a: Amt): String {
        var rest = (s.substring(0, a.start) + " " + s.substring(a.end))
        rest = noteStrip.replace(rest, " ").replace(Regex("\\s+"), " ").trim()
        while (notePrefix.containsMatchIn(rest)) rest = notePrefix.replace(rest, "")
        return rest.trim().take(60)
    }

    private fun monthExpense(ctx: Context): Long {
        val (a, b) = monthRange(0)
        return MoneyStore.entries(ctx).filter { it.type == "expense" && it.date in a until b }.sumOf { it.amount }
    }

    // ---------------- main ----------------
    suspend fun handle(ctx: Context, raw: String): String? {
        val s = clean(raw)
        if (s.isEmpty() || s.length > 220) return null
        val n = norm(s)

        // 1) إلغاء آخر عملية
        if (has(n, "الغي اخر", "امسح اخر", "احذف اخر", "تراجع عن اخر", "الغي التسجيل", "الغي العمليه")) return undoLast(ctx)

        val amt = findAmount(n)
        val qWord = Regex("(^|\\s)(كم|قداش|شحال)(\\s|$)").containsMatchIn(n) || n.contains('؟') || n.contains('?')

        // 2) أسئلة
        if (has(n, "مهامي") || (has(n, "مهام") && has(n, "اليوم", "عندي", "غدوه", "غدا")) ||
            Regex("(واش|شنو|وش)\\s+(عندي|لازم|نديرو|ندير)\\s+(اليوم|دوك|غدوه|غدا)").containsMatchIn(n)
        ) {
            return tasksReply(if (has(n, "غدوه", "غدا")) 1 else 0)
        }
        if (has(n, "عاداتي") || (has(n, "عادات") && has(n, "اليوم"))) return habitsReply(ctx)
        if (amt == null && (has(n, "ديوني", "الديون") || (qWord && has(n, "عندو لي", "عندهم لي", "علي دين", "عليا دين", "شكون عندو", "شكون علي")))) {
            return debtsReply(ctx, n)
        }
        if (amt == null && (has(n, "مصاريفي", "مصروفي", "رصيدي") || (qWord && has(n, "صرفت", "صرفي", "مصروف", "مصاريف", "دخلي")))) {
            return spendingReply(ctx, n)
        }

        // 3) تسديد دين
        trySettle(ctx, n)?.let { return it }

        val foreign = has(n, "مليون", "مليار", "ملايين", "million", "€", "$", "دولار", "يورو", "euro", "usd")

        // 4) دين جديد
        if (amt != null && !foreign) {
            val lend = Regex("سلفت|اقرضت|عطيت")
            val borrow = Regex("استلفت|اقترضت|سلفني|سلفتني")
            val lendOk = n.contains("سلفت") || n.contains("اقرضت") || (n.contains("عطيت") && n.contains("دين"))
            if (borrow.containsMatchIn(n) || (n.contains("عليا دين") || n.contains("علي دين"))) {
                val person = personAfter(n, s, if (borrow.containsMatchIn(n)) borrow else Regex("علي دين|عليا دين"))
                if (person != null) return saveDebt(ctx, "debt_i_owe", person, amt.value, noteOf(s, amt))
            } else if (lendOk) {
                val person = personAfter(n, s, lend)
                if (person != null) return saveDebt(ctx, "debt_to_me", person, amt.value, noteOf(s, amt))
            } else {
                val m = Regex("(\\S+)\\s+(?:عندو لي|عندها لي|عندهم لي|مديرلي|لازم يرجعلي)").find(n)
                if (m != null) {
                    val p = cleanPerson(s.substring(m.groups[1]!!.range.first, m.groups[1]!!.range.last + 1))
                    if (p.length >= 2) return saveDebt(ctx, "debt_to_me", p, amt.value, noteOf(s, amt))
                }
            }
        }

        // 5) مصروف / دخل
        if (amt != null && !foreign) {
            if (has(n, "قبضت", "ربحت", "دخلولي", "دخلتلي")) {
                val e = MoneyEntry(id = System.currentTimeMillis(), type = "income", amount = amt.value, category = incomeCategory(n), note = noteOf(s, amt))
                MoneyStore.save(ctx, e)
                lastPrefs(ctx).edit().putLong("last_entry", e.id).apply()
                return "💵 سجلت دخل " + MoneyStore.fmt(e.amount) + " — " + e.category + (if (e.note.isNotBlank()) " (" + e.note + ")" else "") +
                    "\n↩️ قول \"الغي آخر عملية\" للتراجع."
            }
            if (has(n, "صرفت", "دفعت", "شريت", "كلفني", "كلفتني", "طلعلي", "spent", "paid")) {
                val e = MoneyEntry(id = System.currentTimeMillis(), type = "expense", amount = amt.value, category = expenseCategory(n), note = noteOf(s, amt))
                MoneyStore.save(ctx, e)
                lastPrefs(ctx).edit().putLong("last_entry", e.id).apply()
                return "💸 سجلت مصروف " + MoneyStore.fmt(e.amount) + " — " + e.category + (if (e.note.isNotBlank()) " (" + e.note + ")" else "") +
                    "\n📊 مصروف هذا الشهر: " + MoneyStore.fmt(monthExpense(ctx)) +
                    "\n↩️ قول \"الغي آخر عملية\" للتراجع."
            }
        }

        // 6) إنجاز عادة
        habitDone(ctx, n)?.let { return it }
        return null
    }

    // ---------------- actions ----------------
    private fun saveDebt(ctx: Context, type: String, person: String, amount: Long, note: String): String {
        val e = MoneyEntry(id = System.currentTimeMillis(), type = type, amount = amount, person = person, note = note)
        MoneyStore.save(ctx, e)
        lastPrefs(ctx).edit().putLong("last_entry", e.id).apply()
        return if (type == "debt_to_me")
            "💸 سجلت: " + person + " عندو لك " + MoneyStore.fmt(amount) + "\n(لما يرجعهم قول \"سدد " + person + "\")"
        else
            "🙏 سجلت: عليك لـ " + person + " " + MoneyStore.fmt(amount) + "\n(لما تسدّهم قول \"سددت " + person + "\")"
    }

    private suspend fun trySettle(ctx: Context, n: String): String? {
        if (!has(n, "سدد", "سدلي", "رجعلي", "رجع لي", "خلصني", "خلصلي")) return null
        val open = MoneyStore.entries(ctx).filter { (it.type == "debt_to_me" || it.type == "debt_i_owe") && !it.settled }
        val hit = open.firstOrNull { it.person.length >= 2 && n.contains(norm(it.person)) } ?: return null
        var upd = hit.copy(settled = true)
        val t = hit.taskId
        if (t != null) {
            try { DaniApplication.instance.taskRepository.deleteTaskById(t) } catch (e: Exception) { }
            upd = upd.copy(taskId = null)
        }
        MoneyStore.save(ctx, upd)
        return "✔ تسدّ دين " + hit.person + " (" + MoneyStore.fmt(hit.amount) + ")"
    }

    private fun undoLast(ctx: Context): String {
        val id = lastPrefs(ctx).getLong("last_entry", 0L)
        val e = MoneyStore.entries(ctx).firstOrNull { it.id == id }
        if (id == 0L || e == null) return "ما لقيتش آخر عملية باش نلغيها (تقدر تحذفها من شاشة المال)."
        MoneyStore.delete(ctx, id)
        lastPrefs(ctx).edit().remove("last_entry").apply()
        return "↩️ لغيت: " + MoneyStore.fmt(e.amount) + " " + (e.person.ifBlank { e.category })
    }

    private fun habitTokens(s: String): List<String> =
        norm(s).split(Regex("[^\\p{L}]+")).map { it.removePrefix("ال") }.filter { it.length >= 3 }

    private val habitVerbs = listOf("درت", "عملت", "خلصت", "كملت", "سقيت", "شربت", "مشيت", "قريت", "قرات", "رحت", "تمرنت", "ركضت", "جريت", "خذيت", "اخذت", "علمت", "نمت")

    private fun habitDone(ctx: Context, n: String): String? {
        if (!habitVerbs.any { n.contains(it) }) return null
        val habits = HabitStore.list(ctx)
        if (habits.isEmpty()) return null
        val msg = habitTokens(n)
        var best: Habit? = null
        var bestScore = 0
        for (h in habits) {
            val score = habitTokens(h.name).count { ht -> msg.any { mt -> mt.contains(ht) || ht.contains(mt) } }
            if (score > bestScore) { bestScore = score; best = h }
        }
        val h = best ?: return null
        val today = HabitStore.dayKey(0)
        if (h.days.contains(today)) return "كنت علّمت " + h.emoji + " " + h.name + " اليوم ✅"
        HabitStore.toggle(ctx, h.id, today)
        val updated = HabitStore.list(ctx).firstOrNull { it.id == h.id } ?: h
        val st = HabitStore.streak(updated)
        return "✅ علّمت " + h.emoji + " " + h.name + " اليوم" + (if (st > 1) "\n🔥 سلسلة " + st + " أيام، زيد!" else "")
    }

    // ---------------- queries ----------------
    private suspend fun tasksReply(offset: Int): String {
        val (a, b) = dayRange(offset)
        val tasks = DaniApplication.instance.taskRepository.getAllTasks().first()
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val due = tasks.filter { t -> val d = t.dueDate; !t.isCompleted && d != null && d >= a && d < b }.sortedBy { it.dueDate }
        val overdue = if (offset == 0) tasks.count { t -> val d = t.dueDate; !t.isCompleted && d != null && d < a } else 0
        val title = if (offset == 0) "اليوم" else "غدوة"
        val sb = StringBuilder()
        if (due.isEmpty()) sb.append("✅ ما عندكش مهام مجدولة ").append(title).append(".")
        else {
            sb.append("📅 مهامك ").append(title).append(" (").append(due.size).append("):")
            due.take(10).forEach { t -> sb.append("\n• ").append(fmt.format(Date(t.dueDate ?: 0L))).append(" — ").append(t.title) }
            if (due.size > 10) sb.append("\n... و").append(due.size - 10).append(" أخرى")
        }
        if (overdue > 0) sb.append("\n⚠️ عندك ").append(overdue).append(" مهمة متأخرة.")
        return sb.toString()
    }

    private fun habitsReply(ctx: Context): String {
        val habits = HabitStore.list(ctx)
        if (habits.isEmpty()) return "ما عندكش عادات مسجلة. زيد من الرئيسية ← ✅ العادات."
        val today = HabitStore.dayKey(0)
        val done = habits.count { it.days.contains(today) }
        val sb = StringBuilder("✅ عاداتك اليوم (").append(done).append("/").append(habits.size).append("):")
        habits.forEach { h ->
            val st = HabitStore.streak(h)
            sb.append("\n").append(if (h.days.contains(today)) "✅ " else "⬜ ").append(h.emoji).append(" ").append(h.name)
            if (st > 0) sb.append(" 🔥").append(st)
        }
        return sb.toString()
    }

    private fun debtsReply(ctx: Context, n: String): String {
        val open = MoneyStore.entries(ctx).filter { (it.type == "debt_to_me" || it.type == "debt_i_owe") && !it.settled }
        val toMe = open.filter { it.type == "debt_to_me" }
        val iOwe = open.filter { it.type == "debt_i_owe" }
        val wantToMe = has(n, "عندو لي", "عندهم لي", "شكون عندو") || !has(n, "علي دين", "عليا", "شكون علي", "واش علي")
        val wantIOwe = has(n, "علي دين", "عليا", "شكون علي", "واش علي") || !has(n, "عندو لي", "عندهم لي", "شكون عندو")
        val sb = StringBuilder()
        if (wantToMe) {
            sb.append("💸 عند الناس لك: ").append(MoneyStore.fmt(toMe.sumOf { it.amount }))
            toMe.forEach { sb.append("\n• ").append(it.person).append(" — ").append(MoneyStore.fmt(it.amount)) }
        }
        if (wantIOwe) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append("🙏 عليك للناس: ").append(MoneyStore.fmt(iOwe.sumOf { it.amount }))
            iOwe.forEach { sb.append("\n• ").append(it.person).append(" — ").append(MoneyStore.fmt(it.amount)) }
        }
        return sb.toString()
    }

    private fun spendingReply(ctx: Context, n: String): String {
        val (a, b, title) = when {
            has(n, "البارح") -> dayRange(-1).let { Triple(it.first, it.second, "البارح") }
            has(n, "اليوم") -> dayRange(0).let { Triple(it.first, it.second, "اليوم") }
            has(n, "الاسبوع") -> Triple(dayRange(-6).first, dayRange(0).second, "آخر 7 أيام")
            has(n, "الشهر الفايت", "الشهر الماضي", "الشهر لي فات", "الشهر اللي فات") -> monthRange(-1).let { Triple(it.first, it.second, "الشهر الفايت") }
            else -> monthRange(0).let { Triple(it.first, it.second, "هذا الشهر") }
        }
        val list = MoneyStore.entries(ctx).filter { (it.type == "expense" || it.type == "income") && it.date >= a && it.date < b }
        if (list.isEmpty()) return "ما سجلتش حتى عملية مالية في هذي الفترة (" + title + ")."
        val income = list.filter { it.type == "income" }.sumOf { it.amount }
        val expense = list.filter { it.type == "expense" }.sumOf { it.amount }
        val sb = StringBuilder("📊 ").append(title).append(":\n➖ مصروف: ").append(MoneyStore.fmt(expense))
        sb.append("\n➕ دخل: ").append(MoneyStore.fmt(income)).append("\nالرصيد: ").append(MoneyStore.fmt(income - expense))
        val cats = list.filter { it.type == "expense" }.groupBy { it.category.ifBlank { "أخرى" } }
            .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }.take(4)
        if (cats.isNotEmpty()) sb.append("\n").append(cats.joinToString("  •  ") { it.first + " " + MoneyStore.fmt(it.second) })
        return sb.toString()
    }
}
