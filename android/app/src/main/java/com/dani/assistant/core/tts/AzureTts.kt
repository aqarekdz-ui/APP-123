package com.dani.assistant.core.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * صوت جزائري (ar-DZ) عبر Azure Speech REST. محركات Android المحلية فيها عربية فصحى فقط.
 * المفتاح والمنطقة من حساب Azure (الطبقة المجانية F0 كافية للاستعمال الشخصي).
 */
object AzureTts {
    private fun p(c: Context) = c.getSharedPreferences("dani_providers", Context.MODE_PRIVATE)

    fun key(c: Context): String = p(c).getString("azure_key", "") ?: ""
    fun setKey(c: Context, v: String) = p(c).edit().putString("azure_key", v.trim()).apply()
    fun region(c: Context): String = (p(c).getString("azure_region", null) ?: "francecentral")
    fun setRegion(c: Context, v: String) = p(c).edit().putString("azure_region", v.trim().lowercase()).apply()
    fun voice(c: Context): String = p(c).getString("azure_voice", null) ?: "ar-DZ-AminaNeural"
    fun setVoice(c: Context, v: String) = p(c).edit().putString("azure_voice", v).apply()
    fun enabled(c: Context) = key(c).isNotBlank()

    fun stop() = TtsPlayer.stop()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** يرجع null إذا نجح التشغيل، وإلا نص الخطأ. */
    suspend fun speak(ctx: Context, text: String): String? {
        val k = key(ctx)
        if (k.isBlank()) return "ما كاين مفتاح"
        val file = try {
            withContext(Dispatchers.IO) {
                val body = "<speak version='1.0' xml:lang='ar-DZ'><voice xml:lang='ar-DZ' name='" + voice(ctx) + "'>" + esc(text.take(1500)) + "</voice></speak>"
                val c = URL("https://" + region(ctx) + ".tts.speech.microsoft.com/cognitiveservices/v1").openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 10000
                    c.readTimeout = 20000
                    c.doOutput = true
                    c.setRequestProperty("Ocp-Apim-Subscription-Key", k)
                    c.setRequestProperty("Content-Type", "application/ssml+xml")
                    c.setRequestProperty("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                    c.setRequestProperty("User-Agent", "DANI")
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    if (c.responseCode !in 200..299) {
                        val err = try { c.errorStream?.bufferedReader()?.readText()?.take(120) } catch (e: Exception) { null }
                        throw IllegalStateException("HTTP " + c.responseCode + (if (!err.isNullOrBlank()) " " + err else ""))
                    }
                    val f = File(ctx.cacheDir, "dani_tts.mp3")
                    c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    f
                } finally { c.disconnect() }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        }
        return TtsPlayer.play(file)
    }
}
