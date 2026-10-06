package com.dani.assistant.core.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** مشاركة نص CSV كملف (cache/exports عبر FileProvider). */
object CsvShare {
    fun share(ctx: Context, fileName: String, content: String) {
        try {
            val dir = File(ctx.cacheDir, "exports")
            dir.mkdirs()
            val f = File(dir, fileName)
            f.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "تصدير CSV"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "ما قدرتش نصدّر: " + (e.message ?: "خطأ"), Toast.LENGTH_LONG).show()
        }
    }
}
