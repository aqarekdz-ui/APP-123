package com.dani.assistant.core.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParsingTest {
    private fun amt(s: String): Long? = LifeCommands.findAmount(LifeCommands.norm(LifeCommands.clean(s)))?.value

    @Test fun plainAmounts() {
        assertEquals(500L, amt("صرفت 500 دج"))
        assertEquals(12000L, amt("دفعت 12000"))
        assertEquals(10L, amt("صرفت 10 دج"))
    }

    @Test fun amountPosition() {
        val a = LifeCommands.findAmount("صرفت 500 دج")
        assertNotNull(a)
        assertEquals(5, a!!.start); assertEquals(8, a.end)
    }

    @Test fun arabicIndicDigitsAreConverted() {
        assertEquals("صرفت 500 دج", LifeCommands.clean("صرفت ٥٠٠ دج"))
        assertEquals(750L, amt("صرفت ٧٥٠"))
        assertEquals(1200L, amt("دفعت ۱۲۰۰"))
    }

    @Test fun thousandsSeparators() {
        assertEquals(1500000L, amt("صرفت 1,500,000 دج"))
        assertEquals(1500L, amt("صرفت 1.500 دج"))
        assertEquals(25000L, amt("دفعت 25 000"))
    }

    @Test fun thousandsWords() {
        assertEquals(5000L, amt("صرفت 5 الاف"))
        assertEquals(2000L, amt("دفعت الفين"))
        assertEquals(1000L, amt("دفعت الف دج"))
    }

    @Test fun belowMinimumIsIgnored() {
        assertNull(amt("صرفت 5 دج"))
        assertNull(amt("صرفت 9"))
    }

    @Test fun numbersFollowedByUnitsAreNotMoney() {
        assertNull(amt("شريت 20 كيلو بطاطا"))
        assertNull(amt("نخدم 30 يوم"))
        assertNull(amt("عندي 40 سنه"))
    }

    @Test fun millionsAreNotAccepted() {
        assertNull(amt("صرفت 20 مليون"))
    }

    @Test fun phoneNumbersAreIgnored() {
        assertNull(amt("اتصل بيه 0555123456"))
    }

    @Test fun amountAboveLimitIsIgnored() {
        assertNull(amt("صرفت 2000000000 دج"))
    }

    @Test fun skipsUnitNumberAndTakesTheMoney() {
        assertEquals(800L, amt("شريت 20 كيلو بطاطا ب 800 دج"))
    }
}
