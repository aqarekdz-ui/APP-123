package com.dani.assistant.presentation.dashboard

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
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: TasksViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val pending = tasks.filter { !it.isCompleted }
    val done = tasks.count { it.isCompleted }
    val urgent = pending.count { it.priority == PriorityLevel.URGENT_CRITICAL || it.priority == PriorityLevel.IMPORTANT }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DANI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("متبقية", pending.size.toString(), Modifier.weight(1f))
            StatCard("مهمة", urgent.toString(), Modifier.weight(1f))
            StatCard("منجزة", done.toString(), Modifier.weight(1f))
        }
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
