package com.dani.assistant.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Task
import com.dani.assistant.presentation.tasks.TasksViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: TasksViewModel, onOpenSettings: () -> Unit = {}) {
    val tasks by viewModel.tasks.collectAsState()
    val pending = tasks.filter { !it.isCompleted }
    val done = tasks.count { it.isCompleted }
    val urgent = pending.count { it.priority == PriorityLevel.URGENT_CRITICAL || it.priority == PriorityLevel.IMPORTANT }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DANI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenSettings) { Text("⚙ الإعدادات") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("متبقية", pending.size.toString(), Modifier.weight(1f))
            StatCard("مهمة", urgent.toString(), Modifier.weight(1f))
            StatCard("منجزة", done.toString(), Modifier.weight(1f))
        }
        WeeklyStats(tasks)
        Text("المهام القادمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (pending.isEmpty()) {
            Text("لا توجد مهام. أضف مهمة من تبويب المهام أو من الشات.", color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending.take(8)) { task -> UpcomingTask(task) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UpcomingTask(task: Task) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            val due = task.dueDate
            if (due != null) {
                Text(
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(due)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun WeeklyStats(tasks: List<Task>) {
    val days = (6 downTo 0).map { off ->
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -off)
        c.timeInMillis
    }
    val dayMs = 24L * 60L * 60L * 1000L
    val counts = days.map { start ->
        tasks.count { t ->
            val ca = t.completedAt
            ca != null && ca >= start && ca < start + dayMs
        }
    }
    val total = counts.sum()
    val created = tasks.count { it.createdAt >= days.first() }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val fmt = SimpleDateFormat("EEE", Locale("ar"))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("آخر 7 أيام", fontWeight = FontWeight.Bold)
            Text("أنجزت " + total + " ، وأضفت " + created, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(counts[i].toString(), fontSize = 10.sp)
                        Box(modifier = Modifier.width(22.dp).height((8 + 48 * counts[i] / max).dp).background(MaterialTheme.colorScheme.primary))
                        Text(fmt.format(Date(d)), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
