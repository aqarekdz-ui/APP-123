package com.dani.assistant.core.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** يعرف واش التطبيق موقّع بمفتاح release الثابت (تحديثات بلا حذف) ولا بمفتاح آخر (debug يتبدّل). */
object SigningCheck {
    /** SHA-256 لشهادة dani-release.jks */
    const val RELEASE_SHA256 = "ba16ba269f98fe28d4ab65c382e1216df9615e4243ddcb6e5db862f63f7bd621"

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun isRelease(certs: List<ByteArray>): Boolean = certs.any { sha256Hex(it) == RELEASE_SHA256 }

    @Suppress("DEPRECATION")
    fun certs(ctx: Context): List<ByteArray> = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            (info?.apkContentsSigners ?: emptyArray()).map { it.toByteArray() }
        } else {
            ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures.map { it.toByteArray() }
        }
    } catch (e: Exception) { emptyList() }

    fun isReleaseSigned(ctx: Context): Boolean = isRelease(certs(ctx))
}
