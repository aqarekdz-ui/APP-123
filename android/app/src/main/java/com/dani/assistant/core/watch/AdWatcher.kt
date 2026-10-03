package com.dani.assistant.core.watch

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dani.assistant.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** إعدادات مراقب الإعلانات (prefs "dani_watch"). */
object WatchSettings {
    private fun p(c: Context) = c.getSharedPreferences("dani_watch", Context.MODE_PRIVATE)

    // الافتراضي = مثال المستخدم: كراء شقق في المحمدية (الجزائر العاصمة) على Ouedkniss
    const val DEFAULT_URL = "https://www.ouedkniss.com/mohammadia-alger_immobilier-r?lang=fr"
    const val DEFAULT_REQUIRED = "location, appartement, alger mohammadia"
    const val DEFAULT_EXCLUDE = "وسيط, وسطاء, وكالة, سمسار, agence, agent, courtier, mandat"

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun url(c: Context): String = p(c).getString("url", null) ?: DEFAULT_URL
    fun setUrl(c: Context, v: String) = p(c).edit().putString("url", v.trim()).apply()
    fun required(c: Context): String = p(c).getString("required", null) ?: DEFAULT_REQUIRED
    fun setRequired(c: Context, v: String) = p(c).edit().putString("required", v).apply()
    fun exclude(c: Context): String = p(c).getString("exclude", null) ?: DEFAULT_EXCLUDE
    fun setExclude(c: Context, v: String) = p(c).edit().putString("exclude", v).apply()
    fun intervalMin(c: Context): Int = p(c).getInt("interval", 60)
    fun setIntervalMin(c: Context, v: Int) = p(c).edit().putInt("interval", v).apply()
    fun lastSummary(c: Context): String = p(c).getString("last", null) ?: "لم يشتغل بعد"
    fun setLastSummary(c: Context, v: String) = p(c).edit().putString("last", v).apply()

    fun seen(c: Context): MutableSet<String> {
        val raw = p(c).getString("seen", null) ?: return LinkedHashSet()
        return try {
            val a = JSONArray(raw)
            LinkedHashSet<String>().also { s -> for (i in 0 until a.length()) s.add(a.getString(i)) }
        } catch (e: Exception) { LinkedHashSet() }
    }
    fun saveSeen(c: Context, s: Set<String>) {
        val a = JSONArray()
        s.toList().takeLast(400).forEach { a.put(it) }
        p(c).edit().putString("seen", a.toString()).apply()
    }
    fun clearSeen(c: Context) = p(c).edit().remove("seen").apply()
}

/** يحمّل صفحة داخل WebView مخفي (الموقع SPA ما يخدمش بدون JavaScript) ويقيّم كود JS. */
internal object WebViewFetcher {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun eval(ctx: Context, url: String, js: String, settleMs: Long, timeoutMs: Long = 60_000): String? =
        withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String?> { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    val wv = WebView(ctx.applicationContext)
                    var done = false
                    fun destroy() {
                        if (done) return
                        done = true
                        try { wv.stopLoading(); wv.destroy() } catch (e: Exception) { }
                    }
                    fun finish(v: String?) {
                        if (cont.isActive) cont.resume(v)
                        handler.post { destroy() }
                    }
                    cont.invokeOnCancellation { handler.post { destroy() } }
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = UA
                    var started = false
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String?) {
                            if (started) return
                            started = true
                            handler.postDelayed({
                                if (done) return@postDelayed
                                view.evaluateJavascript(js) { r -> finish(decode(r)) }
                            }, settleMs)
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) finish(null)
                        }
                    }
                    wv.loadUrl(url)
                }
            }
        }

    private fun decode(r: String?): String? {
        if (r == null || r == "null") return null
        return try { JSONTokener(r).nextValue() as? String } catch (e: Exception) { null }
    }
}

object AdWatcher {
    private const val WORK = "ad_watch"
    private const val WORK_NOW = "ad_watch_now"
    private const val CHANNEL = "dani_watch"

    // يجمع روابط الإعلانات + نص البطاقة (بدون الاعتماد على كلاسات CSS)
    private const val LIST_JS = "(function(){var o=[],s={};document.querySelectorAll('a[href]').forEach(function(a){var u;try{u=new URL(a.href)}catch(e){return}" +
        "if(u.hostname.indexOf('ouedkniss')<0)return;var p=u.pathname;if(!/\\/[^\\/]+-algerie(-d\\d+)?\\/?\$/.test(p))return;if(s[p])return;s[p]=1;" +
        "o.push({u:u.origin+p,t:(a.innerText||a.textContent||'').replace(/\\s+/g,' ').trim().substring(0,200)});});return JSON.stringify(o);})()"

    // وصف الإعلان: og:description / meta description، وإلا نص الصفحة
    private const val DETAIL_JS = "(function(){var m=document.querySelector('meta[property=\"og:description\"]')||document.querySelector('meta[name=\"description\"]');" +
        "return JSON.stringify({d:(m&&m.content)||'',b:((document.body&&document.body.innerText)||'').substring(0,4000)});})()"

    private const val MAX_DETAIL_PER_RUN = 8

    /** يطبّق الإعدادات: يجدول الفحص الدوري أو يلغيه. */
    fun reschedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!WatchSettings.enabled(ctx)) { wm.cancelUniqueWork(WORK); return }
        val mins = WatchSettings.intervalMin(ctx).coerceAtLeast(15).toLong()
        val req = PeriodicWorkRequestBuilder<WatchWorker>(mins, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<WatchWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    private fun words(csv: String) = csv.split(',', '،', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    private data class Ad(val url: String, val title: String)

    suspend fun runOnce(ctx: Context): String {
        val url = WatchSettings.url(ctx)
        val required = words(WatchSettings.required(ctx))
        val exclude = words(WatchSettings.exclude(ctx))
        val stamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())

        var listJson = WebViewFetcher.eval(ctx, url, LIST_JS, settleMs = 8_000)
        var arr = parseArray(listJson)
        if (arr.length() == 0) { // الصفحة ممكن تأخرت: محاولة ثانية بانتظار أطول
            listJson = WebViewFetcher.eval(ctx, url, LIST_JS, settleMs = 16_000)
            arr = parseArray(listJson)
        }
        if (arr.length() == 0) {
            val s = "$stamp: ما لقيتش إعلانات في الصفحة (الموقع ممكن حظر الطلب، تغيّر، ولا ما فيش إنترنت)"
            WatchSettings.setLastSummary(ctx, s)
            return s
        }

        val seen = WatchSettings.seen(ctx)
        val candidates = ArrayList<Ad>()
        var total = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("u")
            val slug = u.substringAfterLast('/').replace('-', ' ')
            val hay = (o.optString("t") + " " + slug).lowercase()
            if (slug.lowercase().startsWith("cherche") || slug.lowercase().startsWith("recherche")) continue // طلبات بحث مش عروض
            if (!required.all { hay.contains(it) }) continue
            total++
            if (u in seen) continue
            candidates.add(Ad(u, o.optString("t").ifEmpty { slug }))
        }

        val good = ArrayList<Ad>()
        var excluded = 0
        var checked = 0
        for (ad in candidates) {
            if (checked >= MAX_DETAIL_PER_RUN) break // الباقي يتفحص في الدورة الجاية
            checked++
            val dj = WebViewFetcher.eval(ctx, ad.url, DETAIL_JS, settleMs = 6_000)
            if (dj == null) continue // فشل التحميل: ما نعلّموهش مشاهد، نعاود لاحقاً
            val text = try {
                val o = JSONObject(dj)
                val d = o.optString("d")
                if (d.length >= 40) d else o.optString("b")
            } catch (e: Exception) { "" }
            seen.add(ad.url)
            val low = text.lowercase()
            if (exclude.any { low.contains(it) }) { excluded++; continue }
            good.add(ad)
        }
        WatchSettings.saveSeen(ctx, seen)

        val s = "$stamp: ${arr.length()} إعلان في الصفحة، $total مطابق للكلمات، فُحص $checked، " +
            "$excluded فيه وسيط/وكالة، ${good.size} جديد وصالح" +
            (if (candidates.size > checked) " (باقي ${candidates.size - checked} للدورة الجاية)" else "")
        WatchSettings.setLastSummary(ctx, s)
        if (good.isNotEmpty()) notify(ctx, good)
        return s
    }

    private fun parseArray(s: String?): JSONArray = try { if (s == null) JSONArray() else JSONArray(s) } catch (e: Exception) { JSONArray() }

    private fun notify(ctx: Context, ads: List<Ad>) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "مراقب الإعلانات", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = if (ads.size == 1) Intent(Intent.ACTION_VIEW, Uri.parse(ads[0].url))
        else Intent(Intent.ACTION_VIEW, Uri.parse(WatchSettings.url(ctx)))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 7001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = ads.take(5).joinToString("\n") { "• " + it.title.take(90) }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("🏠 " + ads.size + " إعلان جديد بدون وسيط")
            .setContentText(ads[0].title.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(9101, n) } catch (e: SecurityException) { }
    }
}

class WatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try { AdWatcher.runOnce(applicationContext); Result.success() }
        catch (e: Exception) {
            WatchSettings.setLastSummary(applicationContext, "خطأ: " + (e.message ?: e.javaClass.simpleName))
            Result.success() // ما نعاودوش بعنف: الدورة الجاية تكفي
        }
    }
}
