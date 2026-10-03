package com.dani.assistant.core.tts

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * صوت جزائري مجاني (ar-DZ) عبر خدمة "القراءة بصوت عالٍ" تاع متصفح Edge — بدون مفتاح ولا حساب.
 * ملاحظة: خدمة غير رسمية من Microsoft، ممكن تتبدّل شروطها؛ إذا فشلت التطبيق يرجع لصوت الهاتف.
 */
object EdgeTts {
    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM = "143.0.3650.75"
    private const val WSS = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=" + TOKEN
    private const val WIN_EPOCH = 11644473600L

    private fun p(c: Context) = c.getSharedPreferences("dani_providers", Context.MODE_PRIVATE)
    fun enabled(c: Context) = p(c).getBoolean("edge_on", true)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("edge_on", v).apply()
    fun voice(c: Context): String = p(c).getString("edge_voice", null) ?: "ar-DZ-AminaNeural"
    fun setVoice(c: Context, v: String) = p(c).edit().putString("edge_voice", v).apply()

    @Volatile private var skewSec = 0L

    private val client = OkHttpClient.Builder().pingInterval(0, java.util.concurrent.TimeUnit.SECONDS).build()

    private fun gec(): String {
        var secs = System.currentTimeMillis() / 1000 + skewSec + WIN_EPOCH
        secs -= secs % 300
        val ticks = secs * 10_000_000L
        val d = MessageDigest.getInstance("SHA-256").digest((ticks.toString() + TOKEN).toByteArray(Charsets.US_ASCII))
        return d.joinToString("") { "%02X".format(it) }
    }

    private fun jsDate(): String {
        val f = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date()) + " GMT+0000 (Coordinated Universal Time)"
    }

    private fun esc(s: String) = s
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private class HandshakeError(msg: String, val serverDate: String?) : Exception(msg)

    private suspend fun fetchOnce(text: String, voice: String): ByteArray = withTimeout(25_000) {
        suspendCancellableCoroutine { cont ->
            val url = WSS + "&ConnectionId=" + UUID.randomUUID().toString().replace("-", "") +
                "&Sec-MS-GEC=" + gec() + "&Sec-MS-GEC-Version=1-" + CHROMIUM
            val major = CHROMIUM.substringBefore('.')
            val req = Request.Builder().url(url)
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36 Edg/$major.0.0.0")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "muid=" + UUID.randomUUID().toString().replace("-", "").uppercase() + ";")
                .build()
            val out = ByteArrayOutputStream()
            var finished = false
            fun done(r: Result<ByteArray>) {
                if (finished) return
                finished = true
                if (cont.isActive) r.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
            }
            val ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "X-Timestamp:" + jsDate() + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                            "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                    )
                    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ar-DZ'><voice name='" + voice +
                        "'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>" + esc(text) + "</prosody></voice></speak>"
                    webSocket.send(
                        "X-RequestId:" + UUID.randomUUID().toString().replace("-", "") + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + jsDate() +
                            "Z\r\nPath:ssml\r\n\r\n" + ssml
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, null)
                        if (out.size() > 0) done(Result.success(out.toByteArray())) else done(Result.failure(IllegalStateException("ما وصل حتى صوت")))
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val b = bytes.toByteArray()
                    if (b.size < 2) return
                    val hl = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
                    val start = hl + 2
                    if (start > b.size) return
                    val header = String(b, 2, minOf(hl, b.size - 2), Charsets.UTF_8)
                    if (header.contains("Path:audio") && b.size > start) out.write(b, start, b.size - start)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    done(Result.failure(HandshakeError((if (code != null) "HTTP $code " else "") + (t.message ?: t.javaClass.simpleName), response?.header("Date"))))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (out.size() > 0) done(Result.success(out.toByteArray())) else done(Result.failure(IllegalStateException("انغلق الاتصال بلا صوت ($code)")))
                }
            })
            cont.invokeOnCancellation { ws.cancel() }
        }
    }

    /** يرجع null إذا نجح، وإلا نص الخطأ. */
    suspend fun speak(ctx: Context, text: String): String? {
        val clean = text.take(1500)
        val voice = voice(ctx)
        val bytes = try {
            withContext(Dispatchers.IO) {
                try {
                    fetchOnce(clean, voice)
                } catch (e: HandshakeError) {
                    // 403 غالباً = ساعة الهاتف مختلفة عن السيرفر: نصحّح الفارق من هيدر Date ونعاود مرة
                    val d = e.serverDate?.let { try { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(it)?.time } catch (x: Exception) { null } }
                    if (d != null) {
                        skewSec = (d - System.currentTimeMillis()) / 1000
                        fetchOnce(clean, voice)
                    } else throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        }
        val f = File(ctx.cacheDir, "dani_edge.mp3")
        try { f.writeBytes(bytes) } catch (e: Exception) { return e.message ?: "كتابة الملف فشلت" }
        return TtsPlayer.play(f)
    }
}
