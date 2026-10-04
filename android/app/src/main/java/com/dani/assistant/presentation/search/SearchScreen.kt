package com.dani.assistant.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.dani.assistant.core.search.GlobalSearch
import com.dani.assistant.core.search.Hit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { hits = emptyList(); searched = false; return@LaunchedEffect }
        delay(250)
        hits = withContext(Dispatchers.IO) { try { GlobalSearch.search(context, query) } catch (e: Exception) { emptyList() } }
        searched = true
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🔎 بحث شامل", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("مهام، ملاحظات، مال، عملاء، مناسبات، ذاكرة...") }
        )
        if (searched) Text(hits.size.toString() + " نتيجة", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (searched && hits.isEmpty()) item { Text("ما لقيت والو.", color = MaterialTheme.colorScheme.outline) }
            itemsIndexed(hits) { _, h: Hit ->
                Card(modifier = Modifier.fillMaxWidth().clickable { com.dani.assistant.core.search.SearchFocus.remember(h); onOpen(h.route) }) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(h.emoji + " " + h.title, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(h.kind + (if (h.sub.isNotBlank()) " • " + h.sub else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                    }
                }
            }
        }
    }
}
