package com.dani.assistant.core.debug

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** يحفظ آخر crash في ملف ويعرضه في التشغيل الجاي (باش نعرفو السبب بدون adb). */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(c: Context) {
        val app = c.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val head = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "  thread=" + t.name + "\n"
                file(app).writeText((head + sw.toString()).take(7000))
            } catch (x: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }

    fun read(c: Context): String? = try { file(c).takeIf { it.exists() }?.readText() } catch (e: Exception) { null }
    fun clear(c: Context) { try { file(c).delete() } catch (e: Exception) { } }
}

@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    val clip = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("⚠️ التطبيق تعطّل المرة الفايتة", fontSize = 18.sp)
        Text("انسخ النص وابعثو للمطوّر، ثم اضغط متابعة.", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clip.setText(AnnotatedString(text)) }) { Text("نسخ") }
            OutlinedButton(onClick = onContinue) { Text("متابعة") }
        }
        SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(text, fontSize = 10.sp)
        }
    }
}
