package com.dani.assistant.presentation.goals

import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.dani.assistant.core.goals.Goal
import com.dani.assistant.core.goals.GoalPlanner
import com.dani.assistant.core.goals.GoalStore
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = DaniApplication.instance.taskRepository
    val tasks by repo.getAllTasks().collectAsState(initial = emptyList<Task>())
    var goals by remember { mutableStateOf(GoalStore.list(context)) }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var horizon by remember { mutableStateOf(30) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var delGoal by remember { mutableStateOf<Goal?>(null) }
    val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🎯 الأهداف", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Button(onClick = { adding = true }) { Text("➕ هدف جديد (الذكاء الاصطناعي يقسّمو لمهام)") }
        if (goals.isEmpty()) Text("ما عندكش أهداف. مثال: \"نتعلم الإنجليزية\"، \"نوفر 200000 دج\"، \"نطلق مشروعي\".", color = MaterialTheme.colorScheme.outline)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(goals, key = { it.id }) { g ->
                val mine = tasks.filter { g.taskIds.contains(it.id) }.sortedBy { it.dueDate ?: Long.MAX_VALUE }
                val done = mine.count { it.isCompleted }
                val total = mine.size
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🎯 " + g.title, fontWeight = FontWeight.Bold)
                        if (total > 0) {
                            LinearProgressIndicator(progress = { done.toFloat() / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text(done.toString() + " / " + total + " مهمة" + (if (done == total) "  🎉 الهدف تحقق!" else ""), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        } else {
                            Text("ما بقاتش مهام مرتبطة بهذا الهدف.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        if (expanded == g.id) {
                            mine.forEach { t ->
                                Text(
                                    (if (t.isCompleted) "✅ " else "⬜ ") + t.title + (t.dueDate?.let { "  (" + fmt.format(Date(it)) + ")" } ?: ""),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = { expanded = if (expanded == g.id) null else g.id }) { Text(if (expanded == g.id) "▲ خبّي المهام" else "▼ المهام") }
                            TextButton(onClick = { delGoal = g }) { Text("🗑") }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { if (!busy) adding = false },
            title = { Text("هدف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("وش هو هدفك؟") }, enabled = !busy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = horizon == 7, onClick = { horizon = 7 }, label = { Text("أسبوع") })
                        FilterChip(selected = horizon == 30, onClick = { horizon = 30 }, label = { Text("شهر") })
                        FilterChip(selected = horizon == 90, onClick = { horizon = 90 }, label = { Text("3 أشهر") })
                    }
                    if (busy) Text("⏳ نقسّمو لمهام...", fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val (g, msg) = GoalPlanner.create(context, title.trim(), horizon)
                        busy = false
                        if (g != null) {
                            goals = GoalStore.list(context)
                            expanded = g.id
                            title = ""
                            adding = false
                            Toast.makeText(context, "✅ تقسّم لـ " + g.taskIds.size + " مهام", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("قسّم") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { adding = false }) { Text("إلغاء") } }
        )
    }

    delGoal?.let { dg ->
        AlertDialog(
            onDismissRequest = { delGoal = null },
            title = { Text("حذف الهدف؟") },
            text = { Text(dg.title) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            val done = tasks.filter { dg.taskIds.contains(it.id) && !it.isCompleted }
                            done.forEach { try { repo.deleteTaskById(it.id) } catch (e: Exception) { } }
                            GoalStore.delete(context, dg.id)
                            goals = GoalStore.list(context)
                        }
                        delGoal = null
                    }) { Text("مع المهام غير المنجزة") }
                    Button(onClick = {
                        GoalStore.delete(context, dg.id)
                        goals = GoalStore.list(context)
                        delGoal = null
                    }) { Text("الهدف فقط") }
                }
            },
            dismissButton = { TextButton(onClick = { delGoal = null }) { Text("إلغاء") } }
        )
    }
}
