package com.dani.assistant.presentation.money

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.money.BudgetAlerts
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.core.money.ReceiptReader
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var read by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("أخرى") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var camUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        busy = true; status = "⏳ نقرا الفاتورة..."; read = false
        scope.launch {
            try {
                val r = ReceiptReader.read(context, uri)
                amount = if (r.total > 0) r.total.toString() else ""
                note = (r.merchant + (if (r.items.isNotBlank()) " — " + r.items else "")).trim()
                category = r.category
                dateMillis = r.dateMillis
                read = true
                status = when {
                    r.total <= 0L -> "⚠️ ما قدرتش نقرا المبلغ. دخلو يدوياً."
                    r.currency != "DZD" -> "⚠️ العملة " + r.currency + " (ماشي دج). صحّح المبلغ بالدينار قبل التسجيل."
                    else -> "✅ قريت الفاتورة. راجع المعلومات وسجّل."
                }
            } catch (e: Exception) {
                read = true
                status = GeminiAI.friendlyError(e)
            } finally { busy = false }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) process(u) else status = "ما تصوّرت حتى صورة."
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) process(u)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🧾 فاتورة ← مصروف", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                try {
                    val dir = File(context.cacheDir, "receipts"); dir.mkdirs()
                    val f = File(dir, "r_" + System.currentTimeMillis() + ".jpg")
                    val u = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    camUri = u
                    camera.launch(u)
                } catch (e: Exception) { status = "ما قدرتش نفتح الكاميرا: " + (e.message ?: "") }
            }) { Text("📷 صوّر") }
            Button(enabled = !busy, onClick = { gallery.launch("image/*") }) { Text("🖼 من المعرض") }
        }
        if (status.isNotBlank()) Text(status, fontSize = 13.sp)
        if (read) {
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("المبلغ (دج)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة / المحل") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MoneyStore.expenseCategories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) }
            }
            Button(enabled = (amount.toLongOrNull() ?: 0L) >= 1L, onClick = {
                val e = MoneyEntry(
                    id = System.currentTimeMillis(), type = "expense", amount = amount.toLong(),
                    category = category, note = note.trim().take(60), date = dateMillis ?: System.currentTimeMillis()
                )
                MoneyStore.save(context, e)
                val warn = BudgetAlerts.check(context, category)
                status = "💸 سجلت مصروف " + MoneyStore.fmt(e.amount) + " — " + category + (if (warn != null) "\n" + warn else "")
                read = false; amount = ""; note = ""
            }) { Text("💾 سجّل مصروف") }
        }
        Text("تتبعت الصورة لـ Gemini باش يقراها (مطلوب انترنت). الصور ما تتخزنش في التطبيق.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
    }
}
