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
import androidx.compose.ui.unit.dp
import com.dani.assistant.core.memory.MemoryStore

@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    var facts by remember { mutableStateOf(MemoryStore.getAll(context)) }
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الذاكرة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("DANI يتعلم منك تلقائياً من المحادثات ويستعمل هذه المعلومات في ردوده. احذف أي معلومة ما تحبهاش، أو اكتب في الشات \"تذكر ...\".", color = MaterialTheme.colorScheme.outline)
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
                    MemoryStore.add(context, input)
                    facts = MemoryStore.getAll(context)
                    input = ""
                }
            }) { Text("حفظ") }
        }
        if (facts.isEmpty()) {
            Text("لا توجد معلومات محفوظة بعد.", color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(facts) { fact ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fact, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(onClick = {
                                MemoryStore.remove(context, fact)
                                facts = MemoryStore.getAll(context)
                            }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
