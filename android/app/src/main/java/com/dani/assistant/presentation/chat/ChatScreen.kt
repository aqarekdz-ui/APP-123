package com.dani.assistant.presentation.chat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.core.ai.GeminiAI
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val mood: String = ""
)

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
    val listState = rememberLazyListState()

    val prefs: SharedPreferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val saved = prefs.getString("messages", null)
        if (saved != null) {
            try {
                val array = JSONArray(saved)
                val loaded = mutableListOf<Message>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loaded.add(Message(
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        timestamp = obj.getLong("timestamp"),
                        mood = obj.optString("mood", "")
                    ))
                }
                messages = loaded
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(messages) {
        val array = JSONArray()
        messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("text", msg.text)
            obj.put("isUser", msg.isUser)
            obj.put("timestamp", msg.timestamp)
            obj.put("mood", msg.mood)
            array.put(obj)
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale("ar")
        }
    }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!text.isNullOrBlank()) userInput = text
        }
    }

    fun analyzeMessage(text: String): Pair<String, String?> {
        val lower = text.lowercase()
        var mood = "Neutral"
        var task: String? = null

        when {
            lower.contains("happy") || lower.contains("good") || lower.contains("great") -> mood = "Happy"
            lower.contains("sad") || lower.contains("tired") -> mood = "Sad"
            lower.contains("angry") || lower.contains("mad") -> mood = "Angry"
            lower.contains("worried") || lower.contains("scared") -> mood = "Worried"
            lower.contains("love") -> mood = "Loved"
        }

        when {
            lower.contains("remind") || lower.contains("reminder") -> task = "Reminder: $text"
            lower.contains("task") || lower.contains("todo") -> task = "Task: $text"
            lower.contains("meeting") || lower.contains("appointment") -> task = "Meeting: $text"
            lower.contains("buy") || lower.contains("shop") -> task = "Shopping: $text"
            lower.contains("call") -> task = "Call: $text"
        }

        return Pair(mood, task)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mood:", style = MaterialTheme.typography.bodyMedium)
                Text(currentMood, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!msg.isUser) {
                            Button(
                                onClick = { tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, null) },
                                modifier = Modifier.padding(end = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) { Text("speak", fontSize = 16.sp) }
                        }
                        Surface(
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (msg.mood.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(msg.mood, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (suggestedTask != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Task suggestion:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(suggestedTask!!, fontSize = 14.sp)
                            }
                            Button(onClick = { suggestedTask = null }) { Text("OK") }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
                    }
                    speechLauncher.launch(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) { Text("mic", fontSize = 20.sp) }

            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type or speak...") },
                shape = RoundedCornerShape(24.dp)
            )

            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        val (mood, task) = analyzeMessage(userInput)
                        currentMood = mood
                        suggestedTask = task
                        val msg = Message(userInput, true, mood = mood)
                        messages = messages + msg
                        val input = userInput
                        userInput = ""
                        scope.launch {
                            val response = ai.sendMessage(input)
                            messages = messages + Message(response, false)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp)
            ) { Text("Send") }
        }

        TextButton(
            onClick = {
                messages = emptyList()
                prefs.edit().clear().apply()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Clear chat", fontSize = 12.sp)
        }
    }
}
