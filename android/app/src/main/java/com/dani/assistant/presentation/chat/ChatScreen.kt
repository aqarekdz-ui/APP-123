package com.dani.assistant.presentation.chat

import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dani.assistant.core.ai.GeminiAI
import kotlinx.coroutines.launch
import java.util.Locale

data class Message(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var userInput by remember { mutableStateOf("") }
    val ai = remember { GeminiAI() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val tts = remember { TextToSpeech(context) { } }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!text.isNullOrBlank()) userInput = text
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item { Text("مرحباً! أنا DANI. اضغط 🎤 للحديث أو اكتب هنا.", modifier = Modifier.padding(16.dp)) }
            }
            items(messages) { msg ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start) {
                    if (!msg.isUser) {
                        Button(onClick = { tts.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, null) }) { Text("") }
                    }
                    Surface(color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                        Text(msg.text, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
                })
            }) { Text("🎤") }
            TextField(value = userInput, onValueChange = { userInput = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp))
            Button(onClick = {
                if (userInput.isNotBlank()) {
                    val msg = userInput
                    messages = messages + Message(msg, true)
                    userInput = ""
                    scope.launch { messages = messages + Message(ai.sendMessage(msg), false) }
                }
            }) { Text("إرسال") }
        }
    }
}
