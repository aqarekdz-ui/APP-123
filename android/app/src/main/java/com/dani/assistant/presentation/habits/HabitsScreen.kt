package com.dani.assistant.presentation.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.core.habits.Habit
import com.dani.assistant.core.habits.HabitStore
import java.util.Calendar

private val weekdayLetters = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س") // الأحد..السبت

private fun dayLetter(offset: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, offset)
    return weekdayLetters[c.get(Calendar.DAY_OF_WEEK) - 1]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(onBack: () -> Unit, onOpenStats: () -> Unit = {}) {
    val context = LocalContext.current
    var habits by remember { mutableStateOf(HabitStore.list(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Habit?>(null) }
    val today = HabitStore.dayKey(0)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("✅ العادات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        val doneToday = habits.count { it.days.contains(today) }
        Text("اليوم: " + doneToday + " / " + habits.size, color = MaterialTheme.colorScheme.outline)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }) { Text("➕ عادة جديدة") }
            if (habits.isNotEmpty()) Button(onClick = onOpenStats) { Text("📊 إحصائيات") }
        }
        if (habits.isEmpty()) Text("ما كاينش عادات بعد. زيد عادة (ماء، رياضة، قراءة...) وتابعها كل يوم.", color = MaterialTheme.colorScheme.outline)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(habits, key = { it.id }) { h ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.emoji + " " + h.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            val s = HabitStore.streak(h)
                            if (s > 0) Text("🔥 " + s, fontSize = 13.sp)
                            Checkbox(checked = h.days.contains(today), onCheckedChange = {
                                HabitStore.toggle(context, h.id, today)
                                habits = HabitStore.list(context)
                            })
                            TextButton(onClick = { del = h }) { Text("🗑") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (offset in -6..0) {
                                val key = HabitStore.dayKey(offset)
                                val done = h.days.contains(key)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            HabitStore.toggle(context, h.id, key)
                                            habits = HabitStore.list(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayLetter(offset), fontSize = 12.sp,
                                        color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf(HabitStore.emojis.first()) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("عادة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم العادة (مثال: نشرب 2 لتر ماء)") })
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HabitStore.emojis.forEach { e ->
                            FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    HabitStore.add(context, name, emoji)
                    habits = HabitStore.list(context)
                    showAdd = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } }
        )
    }
    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف العادة؟") },
            text = { Text(d.emoji + " " + d.name + " (مع كل سجلها)") },
            confirmButton = { Button(onClick = { HabitStore.delete(context, d.id); habits = HabitStore.list(context); del = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("إلغاء") } }
        )
    }
}
