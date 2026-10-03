package com.dani.assistant.presentation.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var filterPriority by remember { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }) { Text("Add Task") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "High", "Normal", "Low").forEach { priority ->
                FilterChip(selected = filterPriority == priority, onClick = { filterPriority = priority }, label = { Text(priority) })
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val filteredTasks = if (filterPriority == "All") tasks else tasks.filter { it.priority == filterPriority }
            items(filteredTasks) { task ->
                TaskCard(task = task, onToggle = { viewModel.toggleTask(task.id) }, onDelete = { viewModel.deleteTask(task.id) })
            }
        }

        if (showAddDialog) {
            AddTaskDialog(onDismiss = { showAddDialog = false }, onAdd = { title, desc, priority, dueDate ->
                viewModel.addTask(title, desc, priority, dueDate)
                showAddDialog = false
            })
        }
    }
}

@Composable
fun TaskCard(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val priorityColor = when (task.priority) {
        "High" -> MaterialTheme.colorScheme.error
        "Low" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(text = task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, style = if (task.isCompleted) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge)
                if (task.description.isNotEmpty()) Text(task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.priority, fontSize = 10.sp, color = priorityColor, fontWeight = FontWeight.Bold)
                    if (task.dueDate != null) Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.dueDate)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onDelete) { Text("", fontSize = 18.sp) }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var dueDate by remember { mutableStateOf<Long?>(null) }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Task") }, text = {
        Column {
            TextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("High", "Normal", "Low").forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (title.isNotBlank()) onAdd(title, description, priority, dueDate) }) { Text("Add") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}
