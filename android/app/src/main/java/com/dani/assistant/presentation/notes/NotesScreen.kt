package com.dani.assistant.presentation.notes

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.dani.assistant.core.notes.Note
import com.dani.assistant.core.notes.NoteStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(NoteStore.list(context)) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var del by remember { mutableStateOf<Note?>(null) }
    var msg by remember { mutableStateOf("") }

    fun refresh() { notes = NoteStore.search(context, query) }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) { draft = if (draft.isBlank()) t else draft.trimEnd() + " " + t; msg = "" }
        } else msg = "ما تسجّل حتى صوت."
    }

    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تكلّم، نكتب ملاحظتك")
        }
        try { speech.launch(i) } catch (e: Exception) { msg = "التعرف على الصوت غير متوفر في هذا الجهاز." }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📝 الملاحظات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
            label = { Text(if (editing != null) "تعديل الملاحظة" else "ملاحظة جديدة (اكتب ولا 🎙)") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick = { startVoice() }) { Text("🎙 تكلّم") }
            Button(enabled = draft.isNotBlank(), onClick = {
                val e = editing
                if (e != null) NoteStore.save(context, e.copy(text = draft.trim(), updatedAt = System.currentTimeMillis()))
                else NoteStore.add(context, draft)
                draft = ""; editing = null; refresh()
            }) { Text(if (editing != null) "💾 حفظ التعديل" else "💾 حفظ") }
            if (editing != null || draft.isNotBlank()) TextButton(onClick = { draft = ""; editing = null }) { Text("مسح") }
        }
        if (msg.isNotBlank()) Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value = query, onValueChange = { query = it; notes = NoteStore.search(context, it) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("🔎 بحث في الملاحظات") }
        )
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (notes.isEmpty()) item { Text(if (query.isBlank()) "ما عندكش ملاحظات." else "ما لقيت والو.", color = MaterialTheme.colorScheme.outline) }
            items(notes, key = { it.id }) { n: Note ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text((if (n.pinned) "📌 " else "") + n.text, maxLines = 8)
                        Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(n.updatedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { NoteStore.save(context, n.copy(pinned = !n.pinned)); refresh() }) { Text(if (n.pinned) "إلغاء التثبيت" else "📌 ثبّت") }
                            TextButton(onClick = { editing = n; draft = n.text }) { Text("✏️") }
                            TextButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, n.text) }, "شارك"))
                            }) { Text("📤") }
                            TextButton(onClick = { del = n }) { Text("🗑") }
                        }
                    }
                }
            }
        }
    }

    del?.let { dn ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف الملاحظة؟") },
            text = { Text(dn.text.take(80)) },
            confirmButton = { Button(onClick = { NoteStore.delete(context, dn.id); if (editing?.id == dn.id) { editing = null; draft = "" }; refresh(); del = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("إلغاء") } }
        )
    }
}
