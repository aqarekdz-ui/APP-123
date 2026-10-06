package com.dani.assistant.core.money

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** تحليل رد Gemini للفاتورة (منطق صافي، يتجرّب بـ JUnit). متسامح مع: markdown، نص قبل/بعد JSON، مصفوفة، أرقام كنصوص وبالعربية. */
object ReceiptParser {
    private val arabicDigits = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4', '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4', '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    /** يستخرج كائن JSON من الرد، أو null إذا ما لقاش. */
    fun extractObject(raw: String): JSONObject? {
        val t = raw.replace("```json", "").replace("```", "").trim()
        try { return JSONObject(t) } catch (e: Exception) { }
        try { val a = JSONArray(t); if (a.length() > 0) return a.optJSONObject(0) } catch (e: Exception) { }
        val s = t.indexOf('{'); val e = t.lastIndexOf('}')
        if (s >= 0 && e > s) try { return JSONObject(t.substring(s, e + 1)) } catch (ex: Exception) { }
        return null
    }

    /** مبلغ من رقم أو نص ("1 250,50 DA"، "1,250.00"، "١٢٥٠"). 0 إذا ما فهمش أو غير معقول. */
    fun parseAmount(v: Any?): Double {
        if (v == null || v == JSONObject.NULL) return 0.0
        if (v is Number) return sane(v.toDouble())
        var s = v.toString().map { arabicDigits[it] ?: it }.joinToString("")
            .replace('\u00A0', ' ').replace('\u202F', ' ').replace("٬", ",").replace("٫", ".")
        s = s.replace(" ", "")
        val m = Regex("\\d[\\d.,]*").find(s) ?: return 0.0
        var n = m.value.trimEnd('.', ',')
        val hasC = n.contains(','); val hasD = n.contains('.')
        n = when {
            hasC && hasD -> if (n.lastIndexOf(',') > n.lastIndexOf('.')) n.replace(".", "").replace(',', '.') else n.replace(",", "")
            hasC -> if (Regex("^\\d{1,3}(,\\d{3})+$").matches(n)) n.replace(",", "") else n.replace(',', '.')
            hasD -> if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(n)) n.replace(".", "") else n
            else -> n
        }
        return sane(n.toDoubleOrNull() ?: 0.0)
    }

    private fun sane(d: Double): Double = if (d.isNaN() || d.isInfinite() || d < 0 || d > 1_000_000_000.0) 0.0 else d

    fun normCurrency(raw: String): String {
        val c = raw.trim().uppercase().replace(".", "").replace(" ", "")
        return when (c) {
            "", "DA", "DZ", "DZD", "دج", "دينار", "DINAR", "DINARS", "دينارجزائري" -> "DZD"
            "€", "EURO", "EUROS", "EUR" -> "EUR"
            "$", "US$", "USD" -> "USD"
            else -> c
        }
    }

    /** تاريخ صارم: yyyy-MM-dd أو dd/MM/yyyy أو dd-MM-yyyy، سنة >= 2000. */
    fun parseDate(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        for (f in listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy")) {
            try {
                val df = SimpleDateFormat(f, Locale.US); df.isLenient = false
                val d = df.parse(t) ?: continue
                if (df.format(d) != t) continue
                val y = java.util.Calendar.getInstance().apply { time = d }.get(java.util.Calendar.YEAR)
                if (y >= 2000) return d.time
            } catch (e: Exception) { }
        }
        return null
    }

    /** يرجع null إذا الرد ما فيهش JSON. */
    fun parse(raw: String, categories: List<String>): ReceiptResult? {
        val o = extractObject(raw) ?: return null
        val cat = o.optString("category").trim().let { if (it in categories) it else "أخرى" }
        return ReceiptResult(
            Math.round(parseAmount(o.opt("total"))),
            o.optString("merchant").trim(), cat, parseDate(o.optString("date")),
            normCurrency(o.optString("currency")), o.optString("items").trim()
        )
    }
}
