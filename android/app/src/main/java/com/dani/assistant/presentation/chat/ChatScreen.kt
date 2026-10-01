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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // 1. ميزة القراءة الصوتية (Text-to-Speech)
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("ar")
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { tts.stop(); tts.shutdown() }
    }

    // 2. ميزة الإملاء الصوتي (Speech-to-Text)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) {
                userInput = spokenText
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "مرحباً! أنا DANI. تقدر تكتب لي أو تضغط على زر الميكروفون وتتحدث بالدارجة.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // زر السماعة للردود
                    if (!message.isUser) {
                        Button(
                            onClick = { tts.speak(message.text, TextToSpeech.QUEUE_FLUSH, null, null) },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text("🔊", fontSize = 18.sp) // إيموجي مضمون بدلاً من أيقونة معقدة
                        }
                    }
                    Surface(
                        color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(12.dp),
                            color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // حقل الإدخال والأزرار
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر الميكروفون
            Button(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن...")
                    }
                    speechLauncher.launch(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text("🎤", fontSize = 20.sp) // إيموجي مضمون بدلاً من أيقونة معقدة
            }

            TextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب أو تحدث...") },
                shape = RoundedCornerShape(24.dp)
            )

            // زر الإرسال
            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        val newMsg = userInput
                        messages = messages + Message(newMsg, true)
                        userInput = ""
                        scope.launch {
                            val response = ai.sendMessage(newMsg)
                            messages = messages + Message(response, false)
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "إرسال") // هذه الأيقونة أساسية ومضمونة
            }
        }
    }
}
