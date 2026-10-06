package com.dani.assistant.core.life

import android.content.Context
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.habits.Habit
import com.dani.assistant.core.habits.HabitStore
import com.dani.assistant.core.meds.MedStore
import com.dani.assistant.core.money.BudgetAlerts
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.money.RecurringExpenses
import com.dani.assistant.core.money.RecurringItem
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

    internal fun clean(raw: String): String {
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

    internal fun norm(s: String): String = s.lowercase().map {
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

    internal data class Amt(val value: Long, val start: Int, val end: Int)

    internal fun findAmount(n: String): Amt? {
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

        // 0) الملاحظات (قبل "الغي آخر")
        com.dani.assistant.core.notes.NoteCommands.handle(ctx, s, n)?.let { return it }

        // 0b) بحث شامل
        Regex("(?:بحث شامل|دور في كلشي|دور في كل شي|لقي كلشي|فتش في كلشي)\\s*(?:على|عن|ب)?\\s*(.{2,})").find(n)?.let { m ->
            return com.dani.assistant.core.search.GlobalSearch.chatReply(ctx, m.groupValues[1])
        }

        // 1) إلغاء آخر عملية
        if (has(n, "الغي اخر", "امسح اخر", "احذف اخر", "تراجع عن اخر", "الغي التسجيل", "الغي العمليه")) return undoLast(ctx)

        // 1e) المناسبات وأعياد الميلاد
        com.dani.assistant.core.events.EventCommands.handle(ctx, s, n)?.let { return it }

        val amt = findAmount(n)

        // 1b) الميزانية
        if (has(n, "ميزانيه", "ميزانيات", "ميزانيتي", "ميزانيتى")) budgetCommand(ctx, n, amt)?.let { return it }

        // 1h) الأدوية
        medCommand(ctx, n)?.let { return it }

        // 1g) مؤقت التركيز
        if (has(n, "بومودورو", "pomodoro", "ابدا تركيز", "ابدأ تركيز", "نبدا تركيز", "نبدأ تركيز", "وقف التركيز", "وقف تركيز", "كم ركزت", "قداش ركزت", "شحال ركزت", "تركيز اليوم")) {
            if (has(n, "وقف")) {
                val st = com.dani.assistant.core.focus.PomodoroTimer.state(ctx)
                if (st.phase == "idle") return "⏱ ما كاين حتى مؤقت شغال."
                com.dani.assistant.core.focus.PomodoroTimer.stop(ctx)
                return "⏹ وقفت المؤقت."
            }
            if (has(n, "كم ركزت", "قداش ركزت", "شحال ركزت", "تركيز اليوم")) return com.dani.assistant.core.focus.PomodoroTimer.summaryToday(ctx)
            val st = com.dani.assistant.core.focus.PomodoroTimer.state(ctx)
            if (st.phase != "idle") return "⏱ المؤقت شغال (" + (if (st.phase == "focus") "تركيز" else "راحة") + ")، افتح الرئيسية ← ⏱ تركيز."
            val mins = (Regex("(\\d{1,2})\\s*(?:د|دقيقه|دقايق|min)?").find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 25).let { if (it in 5..90) it else 25 }
            com.dani.assistant.core.focus.PomodoroTimer.startFocus(ctx, mins)
            return "🍅 بدينا تركيز " + mins + " دقيقة. ننبهك كي تخلص (الإشعار فيه العدّ التنازلي)."
        }

        // 1f) هدف كبير: "هدف جديد: نتعلم الإنجليزية" ← يتقسم لمهام بالذكاء الاصطناعي
        goalRe.find(s)?.let { m ->
            val text = m.groupValues[2].trim().trim('.', '،', '!')
            if (text.length >= 4) {
                val horizon = when {
                    has(n, "اسبوع") -> 7
                    has(n, "3 اشهر", "ثلاث اشهر", "ثلاثه اشهر", "سنه", "6 اشهر") -> 90
                    else -> 30
                }
                val (g, msg) = com.dani.assistant.core.goals.GoalPlanner.create(ctx, text, horizon)
                if (g == null) return "🎯 " + msg
                val tasks = DaniApplication.instance.taskRepository.getAllTasks().first().filter { g.taskIds.contains(it.id) }.sortedBy { it.dueDate }
                val f = SimpleDateFormat("dd/MM", Locale.getDefault())
                return "🎯 هدفك: " + g.title + "\nقسّمتو لـ " + tasks.size + " مهام (تلقاهم في المهام والتقويم):\n" +
                    tasks.joinToString("\n") { "• " + (it.dueDate?.let { d -> f.format(Date(d)) } ?: "") + " — " + it.title } +
                    "\n📊 تابع التقدم من الرئيسية ← 🎯 الأهداف."
            }
        }

        // 1e) خطة اليوم
        if (amt == null && has(n, "خطه اليوم", "خطه غدوه", "خطه غدا", "رتب يومي", "رتب ليوم", "رتبلي", "رتب مهامي", "نظم يومي", "نظملي")) {
            return DayPlanner.plan(if (has(n, "غدوه", "غدا")) 1 else 0)
        }

        // 1d) مراجعة الأسبوع
        if (has(n, "مراجعه الاسبوع", "مراجعه اسبوعيه", "ملخص الاسبوع", "مراجعه اسبوعي") && amt == null) {
            return com.dani.assistant.core.digest.WeeklyReview.full(ctx)
        }

        // 1c) مصاريف ثابتة
        if (has(n, "ثابت", "كل شهر", "شهريا")) recurringCommand(ctx, n)?.let { return it }

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
                val warn = BudgetAlerts.check(ctx, e.category)
                return "💸 سجلت مصروف " + MoneyStore.fmt(e.amount) + " — " + e.category + (if (e.note.isNotBlank()) " (" + e.note + ")" else "") +
                    "\n📊 مصروف هذا الشهر: " + MoneyStore.fmt(monthExpense(ctx)) +
                    (if (warn != null) "\n\n" + warn else "") +
                    "\n↩️ قول \"الغي آخر عملية\" للتراجع."
            }
        }

        // 6) إنجاز عادة
        habitDone(ctx, n)?.let { return it }
        return null
    }

    // ---------------- budget ----------------
    private fun budgetCategory(n: String): String? {
        for (c in MoneyStore.expenseCategories) if (n.contains(norm(c))) return c
        val g = expenseCategory(n)
        return if (g != "أخرى") g else null
    }

    private fun budgetLine(ctx: Context, cat: String, budget: Long): String {
        val spent = MoneyStore.spentThisMonth(ctx, cat)
        val pct = (spent * 100 / budget).toInt()
        val left = budget - spent
        val icon = if (pct >= 100) "🚨" else if (pct >= 80) "⚠️" else "✅"
        return icon + " " + cat + ": " + MoneyStore.fmt(spent) + " / " + MoneyStore.fmt(budget) + " (" + pct + "%) — " +
            (if (left >= 0) "بقالك " + MoneyStore.fmt(left) else "زايد " + MoneyStore.fmt(-left))
    }

    private fun budgetCommand(ctx: Context, n: String, amt: Amt?): String? {
        val cat = budgetCategory(n)
        // حذف
        if (has(n, "الغي ميزانيه", "امسح ميزانيه", "احذف ميزانيه")) {
            if (cat == null) return "أي فئة؟ (أكل، مواصلات، فواتير، صحة، ترفيه، شغل، بيت، أخرى)"
            MoneyStore.setBudget(ctx, cat, 0)
            return "🗑 لغيت ميزانية " + cat
        }
        // تحديد
        if (amt != null) {
            if (cat == null) return "أي فئة؟ مثال: \"ميزانية الأكل 20000\" (أكل، مواصلات، فواتير، صحة، ترفيه، شغل، بيت، أخرى)"
            MoneyStore.setBudget(ctx, cat, amt.value)
            val spent = MoneyStore.spentThisMonth(ctx, cat)
            return "🎯 ميزانية " + cat + " = " + MoneyStore.fmt(amt.value) + " في الشهر\n" + budgetLine(ctx, cat, amt.value) +
                "\n(نخبرك عند 80% وعند التجاوز)"
        }
        // استعلام
        val all = MoneyStore.budgets(ctx)
        if (cat != null) {
            val b = all[cat] ?: return "ما حطيتش ميزانية لـ" + cat + ". قول مثلا: \"ميزانية " + cat + " 20000\""
            return budgetLine(ctx, cat, b)
        }
        if (all.isEmpty()) return "ما عندكش ميزانيات. قول مثلا: \"ميزانية الأكل 20000\" ولا من شاشة المال ← الميزانية."
        return "🎯 ميزانيات هذا الشهر:\n" + all.entries.joinToString("\n") { budgetLine(ctx, it.key, it.value) }
    }

    // ---------------- meds ----------------
    private fun medCommand(ctx: Context, n: String): String? {
        val meds = MedStore.list(ctx).filter { MedStore.isLive(it) }
        if (meds.isEmpty()) return null
        val doses = MedStore.todayDoses(ctx)
        val takeVerb = has(n, "اخذت", "خذيت", "شربت", "بلعت", "خذات", "تعاطيت")
        val generic = has(n, "دواء", "دوا ", "الدوا", "ادويه", "علاج", "حبوبي", "حبه")
        val named = meds.firstOrNull { it.name.length >= 2 && n.contains(norm(it.name)) }
        if (takeVerb && (named != null || generic)) {
            val nowMin = MedStore.nowMinute()
            val d = doses.filter { !it.taken && (named == null || it.med.id == named.id) }
                .minByOrNull { Math.abs(it.minute - nowMin) }
                ?: return "✅ كل جرعات " + (named?.name ?: "اليوم") + " متسجلة."
            MedStore.mark(ctx, d.med.id, d.minute, true)
            com.dani.assistant.core.meds.MedAlarms.cancelNotif(ctx, d.med.id, d.minute)
            val left = doses.count { !it.taken } - 1
            return "✅ علّمت " + d.med.name + " (" + com.dani.assistant.core.meds.fmtMinute(d.minute) + ")" + (if (left > 0) "\nباقي " + left + " جرعات اليوم." else "\n🎉 كملت جرعات اليوم!")
        }
        if (has(n, "ادويتي", "الادويه اليوم", "جرعات اليوم", "جرعاتي", "واش من دواء", "ادويه اليوم")) {
            if (doses.isEmpty()) return "💊 ما كاينش جرعات اليوم."
            return "💊 جرعات اليوم:\n" + doses.joinToString("\n") {
                (if (it.taken) "✅ " else "⬜ ") + com.dani.assistant.core.meds.fmtMinute(it.minute) + " — " + it.med.name + (if (it.med.dose.isNotBlank()) " (" + it.med.dose + ")" else "")
            }
        }
        return null
    }

    // ---------------- recurring ----------------
    private val goalRe = Regex("^(هدف جديد|حدد هدف|عندي هدف|هدفي)\\s*(?:هو|هي|:|ان|أن|إن)?\\s*(.{4,})$")
    private val dayRe = Regex("يوم\\s*(\\d{1,2})(?!\\d)")
    private val recStrip = Regex("(مصروف|مصاريف|مصاريفي|ثابت|ثابته|الثابته|كل|شهر|شهريا|يوم|صرف|ندفع|ندير|زيد|سجل|اضف|دج|دينار)")

    private fun recurringCommand(ctx: Context, n: String): String? {
        val items = RecurringExpenses.list(ctx)
        // حذف
        if (has(n, "الغي", "امسح", "احذف")) {
            val hit = items.firstOrNull { it.name.length >= 2 && n.contains(norm(it.name)) } ?: return null
            RecurringExpenses.delete(ctx, hit.id)
            return "🗑 لغيت المصروف الثابت: " + hit.name
        }
        val dm = dayRe.find(n)
        val n2 = if (dm != null) n.replace(dayRe, " ") else n
        val amt = findAmount(n2)
        // عرض
        if (amt == null) {
            if (!has(n, "مصاريف", "مصاريفي", "مصروف")) return null
            if (items.isEmpty()) return "ما عندكش مصاريف ثابتة. قول مثلا: \"مصروف ثابت كراء 30000 يوم 5\" ولا من شاشة المال ← 🔁 ثابتة."
            return "🔁 مصاريفك الثابتة:\n" + items.joinToString("\n") {
                (if (it.active) "• " else "⏸ ") + it.name + " — " + MoneyStore.fmt(it.amount) + " (يوم " + it.day + ")"
            } + "\nالمجموع: " + MoneyStore.fmt(items.filter { it.active }.sumOf { it.amount })
        }
        // إضافة
        if (!has(n, "مصروف", "مصاريف", "كراء", "فاتوره", "اشتراك", "ندفع", "ندير", "سجل", "زيد", "اضف")) return null
        val day = (dm?.groupValues?.get(1)?.toIntOrNull() ?: 1).coerceIn(1, 28)
        val noteRaw = noteOf(n2, amt)
        var name = recStrip.replace(noteRaw, " ").replace(Regex("\\s+"), " ").trim()
        while (notePrefix.containsMatchIn(name)) name = notePrefix.replace(name, "")
        val cat = expenseCategory(n2)
        if (name.isBlank()) name = cat
        val exist = items.firstOrNull { it.name == name }
        RecurringExpenses.save(ctx, RecurringItem(id = exist?.id ?: 0L, name = name, amount = amt.value, category = cat, day = day, active = exist?.active ?: true, last = exist?.last ?: -1))
        return "🔁 سجلت مصروف ثابت: " + name + " — " + MoneyStore.fmt(amt.value) + " كل شهر يوم " + day + " (" + cat + ")\nيتسجل لوحدو. لإلغائه قول \"الغي المصروف الثابت " + name + "\"."
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
        if (title == "هذا الشهر") {
            val nowC = java.util.Calendar.getInstance()
            val upc = try {
                com.dani.assistant.core.money.RecurrenceMath.upcomingTotal(com.dani.assistant.core.money.RecurringExpenses.list(ctx),
                    nowC.get(java.util.Calendar.YEAR) * 12 + nowC.get(java.util.Calendar.MONTH), nowC.get(java.util.Calendar.DAY_OF_MONTH))
            } catch (e: Exception) { 0L }
            val p = com.dani.assistant.core.money.MoneyReports.pace(MoneyStore.entries(ctx), System.currentTimeMillis(), upc)
            if (p.spent > 0) {
                sb.append("\n\n📈 توقع نهاية الشهر: ~").append(MoneyStore.fmt(p.projected)).append(if (p.fixed > 0 || p.upcomingFixed > 0) " (الثابتة ما تتضاعفش" + (if (p.upcomingFixed > 0) "، وجايين " + MoneyStore.fmt(p.upcomingFixed) else "") + ")" else "")
                if (p.pct != null) sb.append("\nمقارنة بنفس الفترة من الشهر الفايت (").append(MoneyStore.fmt(p.prevSamePeriod)).append("): ").append(if (p.pct >= 0) "+" else "").append(p.pct).append("%")
            }
        }
        return sb.toString()
    }
}
