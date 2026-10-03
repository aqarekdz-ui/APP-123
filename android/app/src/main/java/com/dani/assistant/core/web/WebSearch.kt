package com.dani.assistant.core.web

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** بحث ويب بسيط بدون مفتاح: DuckDuckGo (HTML) ثم ويكيبيديا (عربي ثم إنجليزي) كاحتياط. */
object WebSearch {
    data class Result(val title: String, val snippet: String, val url: String)

    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

    suspend fun search(query: String, max: Int = 5): List<Result> = withContext(Dispatchers.IO) {
        val ddg = try { duckDuckGo(query, max) } catch (e: Exception) { emptyList() }
        if (ddg.isNotEmpty()) return@withContext ddg
        for (lang in listOf("ar", "en")) {
            val w = try { wikipedia(query, lang, max) } catch (e: Exception) { emptyList() }
            if (w.isNotEmpty()) return@withContext w
        }
        emptyList()
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8,fr;q=0.6")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP " + c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun duckDuckGo(q: String, max: Int): List<Result> {
        val html = get("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
        val linkRe = Regex("<a[^>]*class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val hrefRe = Regex("href=\"([^\"]+)\"")
        val snipRe = Regex("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
        val links = linkRe.findAll(html).toList()
        val snips = snipRe.findAll(html).map { clean(it.groupValues[1]) }.toList()
        val out = ArrayList<Result>()
        for ((i, m) in links.withIndex()) {
            if (out.size >= max) break
            val tag = m.value.substringBefore(">")
            var href = hrefRe.find(tag)?.groupValues?.get(1) ?: continue
            val uddg = Regex("uddg=([^&]+)").find(href)?.groupValues?.get(1)
            href = if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else href.replace("&amp;", "&")
            if (!href.startsWith("http")) continue
            val title = clean(m.groupValues[1])
            if (title.isEmpty()) continue
            out.add(Result(title, snips.getOrElse(i) { "" }, href))
        }
        return out
    }

    private fun wikipedia(q: String, lang: String, max: Int): List<Result> {
        val json = get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=$max&srsearch=" + URLEncoder.encode(q, "UTF-8"))
        val arr = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        val out = ArrayList<Result>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isEmpty()) continue
            out.add(Result(title, clean(o.optString("snippet")), "https://$lang.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")))
        }
        return out
    }
}
