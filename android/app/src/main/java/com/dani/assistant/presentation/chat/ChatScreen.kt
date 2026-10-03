package com.dani.assistant.presentation.chat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.memory.SecretStore
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis(), val mood: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var userInput by remember { mutableStateOf("") }
    var suggestedTask by remember { mutableStateOf<String?>(null) }
    var currentMood by remember { mutableStateOf("Neutral") }
    val ai = remember { GeminiAI() }
    val scope = rememberCoroutineScope()
    val prefs: SharedPreferences = context.getSharedPreferences("chat", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val saved = prefs.getString("msg", "")
        if (saved != null && saved.isNotEmpty()) {
            val list = mutableListOf<Message>()
            saved.split("|||").forEach { part ->
                val parts = part.split(":::")
                if (parts.size >= 2) {
                    val ts = if (parts.size >= 3) parts[2].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
                    val md = if (parts.size >= 4) parts[3] else ""
                    list.add(Message(parts[0], parts[1] == "true", ts, md))
                }
            }
            if (list.isNotEmpty()) messages = list
        }
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            val saved = messages.joinToString("|||") { it.text + ":::" + it.isUser + ":::" + it.timestamp + ":::" + it.mood }
            prefs.edit().putString("msg", saved).apply()
        }
    }

    val tts = remember { TextToSpeech(context, null) }
    LaunchedEffect(Unit) { tts.language = Locale("ar") }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    val speechLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!text.isNullOrBlank()) userInput = text
        }
    }

    fun analyzeMessage(text: String): Pair<String, String?> {
        val lower = text.lowercase()
        var mood = "Neutral"
        var task: String? = null
        if (lower.contains("happy") || lower.contains("good") || lower.contains("great") || lower.contains("farhan") || lower.contains("mabsoot")) mood = "Happy"
        else if (lower.contains("sad") || lower.contains("tired") || lower.contains("hazin") || lower.contains("taaban")) mood = "Sad"
        else if (lower.contains("angry") || lower.contains("mad") || lower.contains("ghadban") || lower.contains("3asab")) mood = "Angry"
        else if (lower.contains("worried") || lower.contains("scared") || lower.contains("khayef")) mood = "Worried"
        else if (lower.contains("love") || lower.contains("hub") || lower.contains("nhebbek")) mood = "Loved"
        if (lower.contains("remind") || lower.contains("tadhkir") || lower.contains("dhakker")) task = "Reminder: " + text
        else if (lower.contains("task") || lower.contains("muhimma") || lower.contains("mohim")) task = "Task: " + text
        else if (lower.contains("meeting") || lower.contains("maw3id") || lower.contains("rendez")) task = "Meeting: " + text
        else if (lower.contains("buy") || lower.contains("shop") || lower.contains("shri") || lower.contains("nshri")) task = "Shopping: " + text
        else if (lower.contains("call") || lower.contains("wasil") || lower.contains("nwasel")) task = "Call: " + text
        return Pair(mood, task)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mood:", style = MaterialTheme.typography.bodyMedium)
                Text(currentMood, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Hello! I am DANI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Press mic to speak")
                            Text("Press speaker to hear replies")
                            Text("Chat saves automatically")
                            Text("I suggest tasks from your words")
                        }
                    }
                }
            }
            items(messages) { msg ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                        if (!msg.isUser) {
                            Button(onClick = { tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, null) }, modifier = Modifier.padding(end = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("speak", fontSize = 14.sp) }
                        }
                        Surface(color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = msg.text, color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (msg.mood.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(msg.mood, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
                            }
                        }
                    }
                    Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            if (suggestedTask != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text("Task suggestion:", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(suggestedTask!!, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            Button(onClick = { /* TODO: Add to tasks */ }) { Text("Add to Tasks", fontSize = 10.sp) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { suggestedTask = null }) { Text("Ignore") }
                                Button(onClick = {
                                    val t = suggestedTask
                                    if (t != null) scope.launch { DaniApplication.instance.taskRepository.insertTask(Task(title = t)) }
                                    suggestedTask = null
                                }) { Text("Add") }
                            }
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ") }; speechLauncher.launch(intent) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("mic", fontSize = 18.sp) }
            TextField(value = userInput, onValueChange = { userInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type or speak...") }, shape = RoundedCornerShape(24.dp))
            Button(onClick = { if (userInput.isNotBlank()) { val inputText = userInput; if (inputText.startsWith("تذكر") || inputText.lowercase().startsWith("remember")) { val fact = inputText.substringAfter(" ", "").trim(); if (fact.isNotEmpty()) { if (SecretStore.looksSensitive(fact)) SecretStore.add(context, fact) else MemoryStore.add(context, fact) } }; val result = analyzeMessage(inputText); currentMood = result.first; suggestedTask = result.second; val msg = Message(inputText, true, mood = result.first); messages = messages + msg; userInput = ""; scope.launch {
                val idx = messages.size
                messages = messages + Message("", false)
                fun setReply(t: String) { if (idx < messages.size) messages = messages.toMutableList().also { it[idx] = Message(t, false) } }
                val sb = StringBuilder()
                try {
                    ai.sendMessageStream(inputText, MemoryStore.getAll(context) + (if (SecretStore.looksSensitive(inputText)) SecretStore.getAll(context) else emptyList())).collect { chunk -> sb.append(chunk); setReply(sb.toString()) }
                } catch (e: Exception) { if (sb.isEmpty()) setReply("Error") }
                if (sb.isEmpty()) setReply("...")
                val known = MemoryStore.getAll(context)
                val (t, facts, secrets) = ai.analyze(inputText, known)
                if (t != null) suggestedTask = t
                facts.forEach { MemoryStore.add(context, it) }
                secrets.forEach { SecretStore.add(context, it) }
                val all = MemoryStore.getAll(context)
                if (all.size > 40) ai.consolidate(all)?.let { MemoryStore.replaceAll(context, it) }
            } } }, shape = RoundedCornerShape(24.dp)) { Text("Send") }
        }
        TextButton(onClick = { messages = emptyList(); prefs.edit().clear().apply() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Clear chat", fontSize = 12.sp) }
    }
}
