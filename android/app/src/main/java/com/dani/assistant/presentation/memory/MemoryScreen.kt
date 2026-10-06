package com.dani.assistant.presentation.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dani.assistant.core.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dani.assistant.core.knowledge.KnowledgeBase
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.memory.SecretStore

@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    var facts by remember { mutableStateOf(MemoryStore.getAll(context)) }
    var secrets by remember { mutableStateOf(SecretStore.getAll(context)) }
    var learned by remember { mutableStateOf(KnowledgeBase.getAll(context)) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var input by remember { mutableStateOf("") }

    // ---- النسخ الاحتياطي ----
    val scope = rememberCoroutineScope()
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var importJson by remember { mutableStateOf<String?>(null) }
    var importPass by remember { mutableStateOf("") }

    fun refreshAll() {
        facts = MemoryStore.getAll(context)
        secrets = SecretStore.getAll(context)
        learned = KnowledgeBase.getAll(context)
    }

    var pendingExport by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = BackupManager.export(context, pendingExport)
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    com.dani.assistant.core.backup.BackupReminder.markDone(context)
                    backupMsg = "تم حفظ النسخة الاحتياطية ✅"
                } catch (e: Exception) {
                    backupMsg = "فشل التصدير: " + (e.message ?: "")
                }
            }
        }
    }

    fun doImport(json: String, pass: String) {
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { BackupManager.import(context, json, pass) }
                refreshAll()
                backupMsg = "تم الاستيراد: " + r.tasks + " مهمة، " + r.facts + " معلومة، " + r.knowledge + " معرفة، " + r.chat + " رسالة شات، " + r.realestate + " عميل/عقار، " + r.habits + " عادة، " + r.money + " عملية مالية، " + r.recurring + " مصروف ثابت، " + r.goals + " هدف، " + r.focus + " جلسة تركيز، " + r.meds + " دواء، " + r.events + " مناسبة، " + r.notes + " ملاحظة، " + r.secrets + " سر" +
                    (if (r.secretsSkipped) " (الأسرار تخطّيناها: كلمة سر النسخة ناقصة أو غلط)" else "")
            } catch (e: Exception) {
                backupMsg = "فشل الاستيراد: " + (e.message ?: "")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                    }
                } catch (e: Exception) { null }
                if (text == null || !BackupManager.isValid(text)) {
                    backupMsg = "الملف ماشي نسخة DANI صحيحة"
                } else if (BackupManager.hasSecrets(text)) {
                    importPass = ""
                    importJson = text
                } else {
                    doImport(text, "")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الذاكرة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "DANI يتعلم منك تلقائياً ويسجل كل شيء في ملف خاص على هاتفك. يبحث فيه أولاً، ولا يسأل Gemini إلا إذا ما لقاش الجواب. احذف أي شيء ما تحبهش.",
            color = MaterialTheme.colorScheme.outline
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { exportPass = ""; showExportDialog = true }) { Text("تصدير نسخة") }
            Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("استيراد نسخة") }
        }
        val bm = backupMsg
        if (bm != null) Text(bm, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("تصدير نسخة احتياطية") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("المهام والمعلومات والمعرفة ورسائل الشات تتصدّر دايماً (الشات يتحفظ غير مشفّر في الملف). الأسرار تتضاف مشفّرة فقط إذا كتبت كلمة سر للنسخة (تحتاجها وقت الاستيراد).", fontSize = 12.sp)
                        TextField(
                            value = exportPass,
                            onValueChange = { exportPass = it },
                            label = { Text("كلمة سر النسخة (اختياري)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        pendingExport = exportPass
                        showExportDialog = false
                        exportLauncher.launch("dani_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".json")
                    }) { Text("تصدير") }
                },
                dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("إلغاء") } }
            )
        }

        val ij = importJson
        if (ij != null) {
            AlertDialog(
                onDismissRequest = { importJson = null },
                title = { Text("النسخة فيها أسرار") },
                text = {
                    TextField(
                        value = importPass,
                        onValueChange = { importPass = it },
                        label = { Text("كلمة سر النسخة") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = { importJson = null; doImport(ij, importPass) }) { Text("استيراد") }
                },
                dismissButton = {
                    TextButton(onClick = { importJson = null; doImport(ij, "") }) { Text("بدون الأسرار") }
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("معلومة عنك...") },
                shape = RoundedCornerShape(24.dp)
            )
            Button(onClick = {
                if (input.isNotBlank()) {
                    if (SecretStore.looksSensitive(input)) SecretStore.add(context, input) else MemoryStore.add(context, input)
                    facts = MemoryStore.getAll(context)
                    secrets = SecretStore.getAll(context)
                    input = ""
                }
            }) { Text("حفظ") }
        }

        val focused = remember { com.dani.assistant.core.search.SearchFocus.takeText() }
        var showFocused by remember { mutableStateOf(focused != null) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (focused != null && showFocused) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🔎 " + focused.second + " من البحث", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(focused.first, modifier = Modifier.padding(vertical = 6.dp))
                            Row {
                                if (focused.second == "معلومة") TextButton(onClick = { MemoryStore.remove(context, focused.first); facts = MemoryStore.getAll(context); showFocused = false }) { Text("حذف") }
                                TextButton(onClick = { showFocused = false }) { Text("سكّر") }
                            }
                        }
                    }
                }
            }
            if (secrets.isNotEmpty()) {
                item { Text("معلومات سرية (مشفّرة)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(secrets) { sec ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (revealed.contains(sec)) sec else "••••••••", modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(onClick = { revealed = if (revealed.contains(sec)) revealed - sec else revealed + sec }) {
                                Text(if (revealed.contains(sec)) "إخفاء" else "إظهار")
                            }
                            TextButton(onClick = { SecretStore.remove(context, sec); secrets = SecretStore.getAll(context) }) { Text("حذف") }
                        }
                    }
                }
            }

            item { Text("معلومات عنك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (facts.isEmpty()) item { Text("لا توجد معلومات محفوظة بعد.", color = MaterialTheme.colorScheme.outline) }
            items(facts) { fact ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fact, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        TextButton(onClick = { MemoryStore.remove(context, fact); facts = MemoryStore.getAll(context) }) { Text("حذف") }
                    }
                }
            }

            item {
                Text(
                    "ما تعلّمه DANI (" + learned.size + ")",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (learned.isEmpty()) item { Text("لم يتعلم أجوبة بعد. كل سؤال يجاوب عليه Gemini يتسجل هنا.", color = MaterialTheme.colorScheme.outline) }
            items(learned, key = { it.id }) { e ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(e.question, fontWeight = FontWeight.Bold)
                        Text(e.answer, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("استُعمل محلياً " + e.hits + " مرة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                            TextButton(onClick = { KnowledgeBase.remove(context, e.id); learned = KnowledgeBase.getAll(context) }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
