package com.dani.assistant.core.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EventDateParsingTest {
    private fun d(s: String) = EventCommands.findDate(EventCommands.norm(s))

    @Test fun textDateWithoutYear() {
        val r = d("عيد ميلاد أحمد 15 مارس")
        assertNotNull(r)
        assertEquals(15, r!!.day); assertEquals(3, r.month); assertEquals(0, r.year)
    }

    @Test fun textDatePositionsPointToTheDate() {
        val s = EventCommands.norm("عيد ميلاد احمد 15 مارس")
        val r = EventCommands.findDate(s)!!
        assertEquals(s.indexOf("15"), r.start)
        assertEquals(s.length, r.end)
    }

    @Test fun numericDateWithYear() {
        val r = d("عيد ميلاد سارة 3/5/1995")!!
        assertEquals(3, r.day); assertEquals(5, r.month); assertEquals(1995, r.year)
    }

    @Test fun numericDateWithoutYearAndOtherSeparators() {
        val a = d("مناسبة 20-7")!!
        assertEquals(20, a.day); assertEquals(7, a.month)
        val b = d("ذكرى 1.12")!!
        assertEquals(1, b.day); assertEquals(12, b.month)
    }

    @Test fun textDateWithYearAndFiller() {
        val r = d("مناسبة عرس فلان 20 جويليه 2027")!!
        assertEquals(20, r.day); assertEquals(7, r.month); assertEquals(2027, r.year)
        val r2 = d("ذكرى زواج 12 من ماي")!!
        assertEquals(12, r2.day); assertEquals(5, r2.month)
    }

    @Test fun invalidDatesAreRejected() {
        assertNull(d("عيد ميلاد احمد 31/2"))
        assertNull(d("عيد ميلاد احمد 32 مارس"))
        assertNull(d("عيد ميلاد احمد 10/13"))
        assertNull(d("عيد ميلاد احمد 0/5"))
    }

    @Test fun leapDayIsAccepted() {
        val r = d("عيد ميلاد علي 29/2")!!
        assertEquals(29, r.day); assertEquals(2, r.month)
    }

    @Test fun noDateGivesNull() {
        assertNull(d("مناسباتي"))
        assertNull(d("قداش بقى لعيد ميلاد احمد"))
    }

    @Test fun monthWordMustBeWholeWord() {
        assertNull(d("عيد ميلاد احمد 15 مارسيليا"))
    }

    @Test fun normalizeArabicLetters() {
        assertEquals("احمد", EventCommands.norm("أحمد"))
        assertEquals("ميلاده", EventCommands.norm("ميلادة"))
        assertEquals("منتدي", EventCommands.norm("منتدى"))
    }
}
