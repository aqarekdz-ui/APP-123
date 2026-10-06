package com.dani.assistant.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetSuggestTest {
    private fun ms(y: Int, mo: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, mo, d, 12, 0, 0) }.timeInMillis
    private val now = ms(2026, Calendar.OCTOBER, 15)
    private fun exp(date: Long, amount: Long, cat: String, id: Long = date + amount) =
        MoneyEntry(id = id, type = "expense", amount = amount, category = cat, date = date)

    private val threeMonths = listOf(
        exp(ms(2026, Calendar.JULY, 5), 10000, "أكل"),
        exp(ms(2026, Calendar.AUGUST, 5), 12000, "أكل"),
        exp(ms(2026, Calendar.SEPTEMBER, 5), 14000, "أكل"),
        exp(ms(2026, Calendar.SEPTEMBER, 9), 3000, "نقل"),
        exp(ms(2026, Calendar.SEPTEMBER, 12), 300, "أخرى"),            // أقل من الحد
        exp(ms(2026, Calendar.OCTOBER, 2), 99999, "أكل"),               // الشهر الجاري يتجاهل
        MoneyEntry(id = 77, type = "debt_to_me", amount = 50000, person = "علي", date = ms(2026, Calendar.AUGUST, 1))
    )

    @Test fun averagesLastThreeFullMonths_ignoresCurrentAndDebts() {
        val s = HashMap<String, BudgetSuggest.Suggestion>().also { m -> BudgetSuggest.suggest(threeMonths, now, emptyMap()).forEach { m[it.category] = it } }
        assertEquals(12000L, s["أكل"]!!.average)
        assertEquals(12000L, s["أكل"]!!.suggested)
        assertEquals(3, s["أكل"]!!.months)
        assertEquals(1000L, s["نقل"]!!.average)   // 3000 / 3 أشهر
        assertTrue(!s.containsKey("أخرى"))
    }

    @Test fun sortedByAverageDesc() {
        assertEquals(listOf("أكل", "نقل"), BudgetSuggest.suggest(threeMonths, now, emptyMap()).map { it.category })
    }

    @Test fun roundsUpToFiveHundred() {
        val e = listOf(exp(ms(2026, Calendar.SEPTEMBER, 1), 12100, "كراء"))
        val s = BudgetSuggest.suggest(e, now, emptyMap()).single()
        assertEquals(12100L, s.average); assertEquals(12500L, s.suggested); assertEquals(1, s.months)
    }

    @Test fun newUserDividesOnlyByMonthsWithHistory() {
        // أول مصروف في أوت => شهرين فقط (أوت + سبتمبر)
        val e = listOf(exp(ms(2026, Calendar.AUGUST, 20), 6000, "أكل"), exp(ms(2026, Calendar.SEPTEMBER, 3), 4000, "أكل"))
        val s = BudgetSuggest.suggest(e, now, emptyMap()).single()
        assertEquals(2, s.months); assertEquals(5000L, s.average)
    }

    @Test fun noHistoryBeforeCurrentMonth_returnsEmpty() {
        assertTrue(BudgetSuggest.suggest(listOf(exp(ms(2026, Calendar.OCTOBER, 3), 9000, "أكل")), now, emptyMap()).isEmpty())
        assertTrue(BudgetSuggest.suggest(emptyList(), now, emptyMap()).isEmpty())
    }

    @Test fun skipsWhenCurrentBudgetAlreadyEqualsSuggestion_andReportsCurrent() {
        val s = BudgetSuggest.suggest(threeMonths, now, mapOf("أكل" to 12000L, "نقل" to 800L))
        assertEquals(listOf("نقل"), s.map { it.category })
        assertEquals(800L, s.single().current)
    }

    @Test fun ceilTo_edges() {
        assertEquals(0L, BudgetSuggest.ceilTo(0, 500))
        assertEquals(500L, BudgetSuggest.ceilTo(1, 500))
        assertEquals(500L, BudgetSuggest.ceilTo(500, 500))
        assertEquals(1000L, BudgetSuggest.ceilTo(501, 500))
    }
}
