package com.dani.assistant.core.money

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReceiptParserTest {
    private val cats = listOf("أكل", "مواصلات", "فواتير", "صحة", "ترفيه", "شغل", "بيت", "أخرى")
    private fun p(raw: String) = ReceiptParser.parse(raw, cats)!!
    private fun d(s: String) = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s)!!.time

    @Test fun cleanJson() {
        val r = p("""{"total": 1250, "currency": "DZD", "merchant": "Uno", "date": "2026-09-30", "category": "أكل", "items": "خبز، حليب"}""")
        assertEquals(1250L, r.total); assertEquals("DZD", r.currency); assertEquals("Uno", r.merchant)
        assertEquals("أكل", r.category); assertEquals(d("2026-09-30"), r.dateMillis); assertEquals("خبز، حليب", r.items)
    }

    @Test fun markdownFenceAndSurroundingText() {
        val r = p("هذا الرد:\n```json\n{\"total\": 300, \"merchant\": \"X\"}\n```\nشكراً")
        assertEquals(300L, r.total); assertEquals("X", r.merchant)
    }

    @Test fun arrayWrapped() = assertEquals(77L, p("[{\"total\": 77}]").total)

    @Test fun noJsonReturnsNull() {
        assertNull(ReceiptParser.parse("sorry I cannot read this", cats))
        assertNull(ReceiptParser.parse("", cats))
    }

    @Test fun totalAsStringVariants() {
        assertEquals(1250.5, ReceiptParser.parseAmount("1250.5"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1 250"), 0.001)
        assertEquals(1250.5, ReceiptParser.parseAmount("1 250,50 DA"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1,250.00"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1.250,00"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1,250"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1.250"), 0.001)
        assertEquals(12.5, ReceiptParser.parseAmount("12,5"), 0.001)
        assertEquals(1250.0, ReceiptParser.parseAmount("1250 دج"), 0.001)
    }

    @Test fun arabicIndicDigits() {
        assertEquals(1250.0, ReceiptParser.parseAmount("١٢٥٠"), 0.001)
        assertEquals(1250.5, ReceiptParser.parseAmount("۱۲۵۰٫۵"), 0.001)
    }

    @Test fun garbageAmountsAreZero() {
        assertEquals(0.0, ReceiptParser.parseAmount("unreadable"), 0.0)
        assertEquals(0.0, ReceiptParser.parseAmount(null), 0.0)
        assertEquals(0.0, ReceiptParser.parseAmount(-50), 0.0)
        assertEquals(0.0, ReceiptParser.parseAmount(9.9e12), 0.0)
        assertEquals(0.0, ReceiptParser.parseAmount(Double.NaN), 0.0)
        assertEquals(0L, p("{\"total\": null}").total)
        assertEquals(0L, p("{\"merchant\": \"x\"}").total)
    }

    @Test fun roundsToNearestDinar() {
        assertEquals(1251L, p("{\"total\": 1250.5}").total)
        assertEquals(1250L, p("{\"total\": 1250.4}").total)
    }

    @Test fun currencyNormalization() {
        assertEquals("DZD", p("{\"currency\": \"DA\"}").currency)
        assertEquals("DZD", p("{\"currency\": \"دج\"}").currency)
        assertEquals("DZD", p("{\"currency\": \"\"}").currency)
        assertEquals("DZD", p("{}").currency)
        assertEquals("EUR", p("{\"currency\": \"€\"}").currency)
        assertEquals("EUR", p("{\"currency\": \"eur\"}").currency)
        assertEquals("USD", p("{\"currency\": \"$\"}").currency)
        assertEquals("MAD", p("{\"currency\": \"mad\"}").currency)
    }

    @Test fun unknownCategoryFallsBackToOther() {
        assertEquals("أخرى", p("{\"category\": \"food\"}").category)
        assertEquals("فواتير", p("{\"category\": \" فواتير \"}").category)
        assertEquals("أخرى", p("{}").category)
    }

    @Test fun datesStrict() {
        assertEquals(d("2026-02-28"), ReceiptParser.parseDate("2026-02-28"))
        assertEquals(d("2026-09-30"), ReceiptParser.parseDate("30/09/2026"))
        assertEquals(d("2026-09-30"), ReceiptParser.parseDate("30-09-2026"))
        assertNull(ReceiptParser.parseDate("2026-02-30"))
        assertNull(ReceiptParser.parseDate("2026-13-01"))
        assertNull(ReceiptParser.parseDate("1999-01-01"))
        assertNull(ReceiptParser.parseDate(""))
        assertNull(ReceiptParser.parseDate("yesterday"))
        assertNotNull(p("{\"date\": \"2026-09-30\"}").dateMillis)
        assertNull(p("{\"date\": \"2026-09-31\"}").dateMillis)
    }
}
