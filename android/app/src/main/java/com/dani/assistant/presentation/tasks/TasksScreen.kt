package com.dani.assistant.presentation.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Task
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    var filterPriority by remember { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Tasks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }) { Text("Add Task") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "High", "Medium", "Low").forEach { priority ->
                FilterChip(selected = filterPriority == priority, onClick = { filterPriority = priority }, label = { Text(priority) })
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val filteredTasks = if (filterPriority == "All") tasks else tasks.filter { 
                priorityLabel(it.priority) == filterPriority
            }
            items(filteredTasks) { task ->
                TaskCard(task = task, onToggle = { viewModel.toggleTask(task) }, onDelete = { viewModel.deleteTask(task) }, onEdit = { editingTask = task })
            }
        }

        val needsPerm by viewModel.needsExactAlarmPermission.collectAsState()
        val ctx = LocalContext.current
        if (needsPerm) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissPermissionPrompt() },
                title = { Text("إذن المنبهات") },
                text = { Text("لتفعيل التذكير في الوقت بالضبط، فعّل إذن \"المنبهات والتذكيرات\" للتطبيق.") },
                confirmButton = {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        viewModel.dismissPermissionPrompt()
                    }) { Text("فتح الإعدادات") }
                },
                dismissButton = { TextButton(onClick = { viewModel.dismissPermissionPrompt() }) { Text("لاحقاً") } }
            )
        }

        val editing = editingTask
        if (editing != null) {
            AddTaskDialog(
                onDismiss = { editingTask = null },
                onAdd = { title, desc, priority, dueDate ->
                    viewModel.updateTask(editing, title, desc, priority, dueDate)
                    editingTask = null
                },
                initial = editing
            )
        }

        if (showAddDialog) {
            AddTaskDialog(onDismiss = { showAddDialog = false }, onAdd = { title, desc, priority, dueDate ->
                viewModel.addTask(title, desc, priority, dueDate)
                showAddDialog = false
            })
        }
    }
}

internal fun priorityLabel(p: PriorityLevel): String = when (p) {
    PriorityLevel.URGENT_CRITICAL, PriorityLevel.IMPORTANT -> "High"
    PriorityLevel.LOW, PriorityLevel.SOMEDAY -> "Low"
    PriorityLevel.MEDIUM -> "Medium"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}) {
    val priorityName = priorityLabel(task.priority)
    val priorityColor = when (priorityName) {
        "High" -> MaterialTheme.colorScheme.error
        "Low" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp).clickable { onEdit() }) {
                Text(text = task.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, style = if (task.isCompleted) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge)
                if (!task.description.isNullOrEmpty()) Text(task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(priorityName, fontSize = 10.sp, color = priorityColor, fontWeight = FontWeight.Bold)
                    if (task.dueDate != null) Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.dueDate)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = onDelete) { Text("🗑", fontSize = 18.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Long?) -> Unit, initial: Task? = null) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var priority by remember { mutableStateOf(if (initial != null) priorityLabel(initial.priority) else "Medium") }
    var dueDate by remember { mutableStateOf(initial?.dueDate) }
    val context = LocalContext.current
    fun pickDateTime() {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val c = Calendar.getInstance()
                c.set(y, m, d, h, min, 0)
                c.set(Calendar.MILLISECOND, 0)
                dueDate = c.timeInMillis
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add Task" else "Edit Task") }, text = {
        Column {
            TextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            TextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("High", "Medium", "Low").forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickDateTime() }) { Text(if (dueDate == null) "تذكير" else "تغيير") }
                val d = dueDate
                if (d != null) {
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(d)), fontSize = 12.sp)
                    TextButton(onClick = { dueDate = null }) { Text("✕") }
                }
            }
        }
    }, confirmButton = { Button(onClick = { if (title.isNotBlank()) onAdd(title, description, priority, dueDate) }) { Text(if (initial == null) "Add" else "Save") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}
