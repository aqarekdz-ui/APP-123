package com.dani.assistant.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dani.assistant.core.alarm.AlarmReliability

private fun open(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return } catch (e: Exception) { }
    }
}

/** بطاقة "⚡ ضمان المنبهات": تبيّن وش ناقص وتفتح الإعداد المناسب مباشرة. */
@Composable
fun AlarmReliabilityCard() {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(AlarmReliability.status(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) st = AlarmReliability.status(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val pkg = ctx.packageName

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("⚡ ضمان المنبهات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(if (st.allGood) "✅ كلشي مفعّل (أدوية، مناسبات، تذكيرات)." else "⚠️ فيه إعدادات ناقصة ممكن تخلي المنبهات ما تدقش.",
            fontSize = 13.sp, color = if (st.allGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

        @Composable
        fun line(ok: Boolean, label: String, onFix: () -> Unit) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text((if (ok) "✅ " else "❌ ") + label, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                if (!ok) OutlinedButton(onClick = onFix) { Text("فعّل") }
            }
        }
        line(st.notifications, "الإشعارات") {
            open(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)))
        }
        line(st.exactAlarms, "المنبهات الدقيقة") {
            if (Build.VERSION.SDK_INT >= 31) open(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg)),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)))
        }
        line(st.batteryUnrestricted, "استثناء توفير البطارية") {
            open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        var testMsg by remember { mutableStateOf(com.dani.assistant.core.alarm.AlarmTest.lastResult(ctx)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val ok = com.dani.assistant.core.alarm.AlarmTest.schedule(ctx)
                testMsg = if (ok) "⏳ برمجت منبه بعد دقيقة. سكّر الشاشة وستنى الإشعار." else "❌ النظام رفض المنبه الدقيق، فعّل الإذن اللي فوق."
            }) { Text("🔔 جرّب منبه (دقيقة)") }
            TextButton(onClick = { testMsg = com.dani.assistant.core.alarm.AlarmTest.lastResult(ctx) }) { Text("حدّث") }
        }
        Text(testMsg, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        val hint = AlarmReliability.vendorHint(Build.MANUFACTURER ?: "")
        if (hint.isNotBlank()) {
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            TextButton(onClick = { open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))) }) { Text("افتح إعدادات التطبيق") }
        }
    }
}
