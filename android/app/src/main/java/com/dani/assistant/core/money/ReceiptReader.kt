package com.dani.assistant.core.money

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import com.dani.assistant.core.ai.GeminiAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ReceiptResult(
    val total: Long,          // بالدينار، 0 = ما تعرفش
    val merchant: String,
    val category: String,
    val dateMillis: Long?,
    val currency: String,
    val items: String
)

/** قراءة فاتورة من صورة بـ Gemini vision (يتطلب انترنت). */
object ReceiptReader {
    private const val MAX_SIDE = 1600

    private fun loadJpeg(ctx: Context, uri: Uri): ByteArray {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > MAX_SIDE * 2 || opts.outHeight / sample > MAX_SIDE * 2) sample *= 2
        val dec = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, dec) }
            ?: throw IllegalStateException("ما قدرتش نقرا الصورة")
        // تدوير حسب EXIF
        val rot = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) { 0f }
        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        if (rot != 0f || scale < 1f) {
            val m = Matrix()
            if (rot != 0f) m.postRotate(rot)
            if (scale < 1f) m.postScale(scale, scale)
            val nb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (nb !== bmp) { bmp.recycle(); bmp = nb }
        }
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    private val prompt = "Read this receipt/invoice image. Reply with ONLY a JSON object, no markdown: " +
        "{\"total\": <final amount to pay as a number, no thousands separators, 0 if unreadable>, " +
        "\"currency\": <ISO code like DZD, EUR, USD; use DZD if the receipt shows DA, DZD or دج or no currency>, " +
        "\"merchant\": <store/company name, short>, " +
        "\"date\": <yyyy-MM-dd or empty string>, " +
        "\"category\": <exactly one of: أكل, مواصلات, فواتير, صحة, ترفيه, شغل, بيت, أخرى>, " +
        "\"items\": <very short summary of purchased items, max 60 chars, Arabic or original language>}. " +
        "If the image is not a receipt, return total 0 and empty merchant."

    suspend fun read(ctx: Context, uri: Uri): ReceiptResult {
        val jpeg = withContext(Dispatchers.IO) { loadJpeg(ctx, uri) }
        val raw = GeminiAI.visionJson(prompt, Base64.encodeToString(jpeg, Base64.NO_WRAP))
        return ReceiptParser.parse(raw, MoneyStore.expenseCategories)
            ?: throw IllegalStateException("ما فهمتش رد الذكاء الاصطناعي. جرّب صورة أوضح.")
    }
}
