package com.dani.assistant.core.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

data class ReliabilityStatus(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean) {
    val allGood: Boolean get() = notifications && exactAlarms && batteryUnrestricted
}

/** فحص الإعدادات اللي تخلي المنبهات (أدوية، مناسبات، تذكيرات) تخدم فعلاً. */
object AlarmReliability {
    fun status(ctx: Context): ReliabilityStatus {
        val notif = if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
        else androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val exact = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms() else true
        val battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
        return ReliabilityStatus(notif, exact, battery)
    }

    /** نصيحة حسب صانع الهاتف (منطق صافي). */
    fun vendorHint(manufacturer: String): String {
        val m = manufacturer.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                "Xiaomi: فعّل \"التشغيل التلقائي\" (Autostart) لـ DANI، وخلّي توفير البطارية \"بدون قيود\"، وثبّت التطبيق في قائمة التطبيقات الأخيرة."
            m.contains("huawei") || m.contains("honor") ->
                "Huawei/Honor: من إدارة التشغيل (Launch) فعّل التشغيل اليدوي لـ DANI (تلقائي + تشغيل ثانوي + يعمل في الخلفية)."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
                "Oppo/Realme/OnePlus: فعّل \"السماح بالتشغيل التلقائي\" و\"السماح بالنشاط في الخلفية\" لـ DANI."
            m.contains("vivo") || m.contains("iqoo") ->
                "Vivo: فعّل \"التشغيل التلقائي\" و\"استهلاك عالي للطاقة في الخلفية\" لـ DANI."
            m.contains("samsung") ->
                "Samsung: من البطارية ← حدود الاستخدام في الخلفية، شيل DANI من \"التطبيقات النائمة\"."
            else -> ""
        }
    }
}
