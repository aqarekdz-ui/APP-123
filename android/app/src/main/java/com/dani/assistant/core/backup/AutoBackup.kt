package com.dani.assistant.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * نسخة احتياطية تلقائية كل أسبوع (WorkManager):
 *  - دايماً داخل مجلد التطبيق، وإذا اخترت مجلد (Documents مثلاً) تتنسخ فيه كذلك.
 *  - تبقى آخر 5 نسخ فقط.
 *  - الأسرار تدخل في النسخة فقط إذا حطيت كلمة سر للنسخة (تتشفر بها).
 */
object AutoBackup {
    private const val PREFS = "dani_autobackup"
    private const val WORK = "dani_auto_backup"
    private const val KEEP = 5
    private const val PREFIX = "dani_backup_"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = p(ctx).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("enabled", v).apply()
        schedule(ctx)
    }

    fun treeUri(ctx: Context): String? = p(ctx).getString("tree", null)

    fun setTree(ctx: Context, uri: Uri?) {
        p(ctx).edit().putString("tree", uri?.toString()).apply()
    }

    fun folderLabel(ctx: Context): String? = treeUri(ctx)?.let { Uri.parse(it).lastPathSegment }

    fun pass(ctx: Context): String = p(ctx).getString("pass", "") ?: ""
    fun setPass(ctx: Context, v: String) { p(ctx).edit().putString("pass", v).apply() }

    fun lastInfo(ctx: Context): String = p(ctx).getString("last_info", "") ?: ""

    fun localDir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups")

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (enabled(ctx)) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    suspend fun runNow(ctx: Context): String = withContext(Dispatchers.IO) {
        try {
            val pass = pass(ctx)
            val bytes = BackupManager.export(ctx, pass).toByteArray(Charsets.UTF_8)
            val name = PREFIX + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json"
            val parts = ArrayList<String>()

            val dir = localDir(ctx)
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
            dir.listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
            parts.add("داخل التطبيق")

            val tree = treeUri(ctx)
            if (tree != null) {
                try {
                    writeToTree(ctx, Uri.parse(tree), name, bytes)
                    parts.add("المجلد المختار")
                } catch (e: Exception) {
                    parts.add("⚠️ المجلد المختار فشل (اخترو من جديد)")
                }
            }

            val info = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date()) + " — " + parts.joinToString(" + ") +
                " (" + (bytes.size / 1024) + " KB، " + (if (pass.isEmpty()) "بدون أسرار" else "مع الأسرار") + ")"
            p(ctx).edit().putString("last_info", info).apply()
            BackupReminder.markDone(ctx)
            "✅ " + info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "❌ فشل النسخ: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun writeToTree(ctx: Context, tree: Uri, name: String, bytes: ByteArray) {
        val cr = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val created = DocumentsContract.createDocument(cr, rootDoc, "application/json", name)
            ?: throw IOException("create failed")
        val out = cr.openOutputStream(created, "wt") ?: throw IOException("open failed")
        out.use { it.write(bytes) }

        // الاحتفاظ بآخر 5 نسخ فقط
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val rows = ArrayList<Pair<String, String>>()
        cr.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val nm = c.getString(1) ?: ""
                if (nm.startsWith(PREFIX)) rows.add(id to nm)
            }
        }
        rows.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (e: Exception) { }
        }
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        AutoBackup.runNow(applicationContext)
        return Result.success()
    }
}
