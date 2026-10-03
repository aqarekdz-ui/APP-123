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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الذاكرة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "DANI يتعلم منك تلقائياً ويسجل كل شيء في ملف خاص على هاتفك. يبحث فيه أولاً، ولا يسأل Gemini إلا إذا ما لقاش الجواب. احذف أي شيء ما تحبهش.",
            color = MaterialTheme.colorScheme.outline
        )
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

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
