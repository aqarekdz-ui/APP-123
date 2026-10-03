package com.dani.assistant.presentation.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.dani.assistant.core.areas.AreaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
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
import com.dani.assistant.domain.model.Recurrence
import com.dani.assistant.domain.model.Subtask
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    var filterArea by remember { mutableStateOf<String?>(null) }
    val areaCtx = LocalContext.current
    val areaTick by viewModel.areaTick.collectAsState()
    val areaMap = remember(tasks, areaTick) { AreaStore.all(areaCtx) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المهام", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }) { Text("إضافة مهمة") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "High", "Medium", "Low").forEach { priority ->
                FilterChip(selected = filterPriority == priority, onClick = { filterPriority = priority }, label = { Text(prioAr(priority)) })
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AreaStore.areas.forEach { a ->
                FilterChip(
                    selected = filterArea == a.key,
                    onClick = { filterArea = if (filterArea == a.key) null else a.key },
                    label = { Text(a.emoji + " " + a.label, fontSize = 11.sp) }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            placeholder = { Text("🔍 بحث في المهام") },
            singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") } }
        )

        val q = query.trim()
        val filteredTasks = tasks.filter { t ->
            (filterPriority == "All" || priorityLabel(t.priority) == filterPriority) &&
                (filterArea == null || areaMap[t.id] == filterArea) &&
                (q.isEmpty() || t.title.contains(q, ignoreCase = true) || (t.description ?: "").contains(q, ignoreCase = true))
        }
        if (filteredTasks.isEmpty() && (q.isNotEmpty() || tasks.isNotEmpty())) {
            Text("ما لقيت حتى مهمة", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(8.dp))
        }
        Text("← سحب لليمين: إنجاز | سحب لليسار: حذف", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 4.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredTasks, key = { it.id }) { task ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        when (value) {
                            SwipeToDismissBoxValue.StartToEnd -> { viewModel.toggleTask(task); false }
                            SwipeToDismissBoxValue.EndToStart -> { pendingDelete = task; false }
                            else -> false
                        }
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        val dir = dismissState.dismissDirection
                        val bg = when (dir) {
                            SwipeToDismissBoxValue.StartToEnd -> Color(0xFF2E7D32)
                            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                            else -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().background(bg).padding(horizontal = 20.dp),
                            contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                        ) {
                            Text(if (dir == SwipeToDismissBoxValue.StartToEnd) "✔" else if (dir == SwipeToDismissBoxValue.EndToStart) "🗑" else "", fontSize = 22.sp, color = Color.White)
                        }
                    }
                ) {
                    TaskCard(task = task, onToggle = { viewModel.toggleTask(task) }, onDelete = { viewModel.deleteTask(task) }, onEdit = { editingTask = task }, area = areaMap[task.id], onSubtaskToggle = { i -> viewModel.toggleSubtask(task, i) })
                }
            }
        }

        val toDelete = pendingDelete
        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("حذف المهمة؟") },
                text = { Text(toDelete.title) },
                confirmButton = { Button(onClick = { viewModel.deleteTask(toDelete); pendingDelete = null }) { Text("حذف") } },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
            )
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
                onAdd = { title, desc, priority, dueDate, rec, subs, area ->
                    viewModel.updateTask(editing, title, desc, priority, dueDate, rec, subs, area)
                    editingTask = null
                },
                initial = editing
            )
        }

        if (showAddDialog) {
            AddTaskDialog(onDismiss = { showAddDialog = false }, onAdd = { title, desc, priority, dueDate, rec, subs, area ->
                viewModel.addTask(title, desc, priority, dueDate, rec, subs, area)
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

private fun prioAr(p: String): String = when (p) {
    "All" -> "الكل"
    "High" -> "عالية"
    "Medium" -> "متوسطة"
    "Low" -> "منخفضة"
    else -> p
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}, onSubtaskToggle: (Int) -> Unit = {}, area: String? = null) {
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
                    Text(prioAr(priorityName), fontSize = 10.sp, color = priorityColor, fontWeight = FontWeight.Bold)
                    AreaStore.find(area)?.let { a -> Text(a.emoji + " " + a.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
                    if (task.recurrence != Recurrence.NONE) Text("🔁 " + task.recurrence.arabic, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    if (task.dueDate != null) Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.dueDate)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    if (task.subtasks.isNotEmpty()) Text("☑ " + task.subtasks.count { it.done } + "/" + task.subtasks.size, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
                task.subtasks.forEachIndexed { i, st ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSubtaskToggle(i) }) {
                        Checkbox(checked = st.done, onCheckedChange = { onSubtaskToggle(i) })
                        Text(st.text, fontSize = 13.sp, style = if (st.done) MaterialTheme.typography.bodySmall.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodySmall)
                    }
                }
            }
            IconButton(onClick = onDelete) { Text("🗑", fontSize = 18.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String, String, Long?, Recurrence, List<Subtask>, String?) -> Unit, initial: Task? = null) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var priority by remember { mutableStateOf(if (initial != null) priorityLabel(initial.priority) else "Medium") }
    var dueDate by remember { mutableStateOf(initial?.dueDate) }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: Recurrence.NONE) }
    var subs by remember { mutableStateOf(initial?.subtasks ?: emptyList<Subtask>()) }
    var newSub by remember { mutableStateOf("") }
    val context = LocalContext.current
    var area by remember { mutableStateOf<String?>(if (initial != null) AreaStore.get(context, initial.id) else null) }
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

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "إضافة مهمة" else "تعديل المهمة") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TextField(value = title, onValueChange = { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            TextField(value = description, onValueChange = { description = it }, label = { Text("الوصف") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("High", "Medium", "Low").forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(prioAr(p)) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickDateTime() }) { Text(if (dueDate == null) "تذكير" else "تغيير") }
                val d = dueDate
                if (d != null) {
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(d)), fontSize = 12.sp)
                    TextButton(onClick = { dueDate = null; recurrence = Recurrence.NONE }) { Text("✕") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔁", fontSize = 14.sp)
                Recurrence.values().forEach { r ->
                    FilterChip(selected = recurrence == r, enabled = r == Recurrence.NONE || dueDate != null, onClick = { recurrence = r }, label = { Text(r.arabic, fontSize = 11.sp) })
                }
            }
            if (dueDate == null) Text("اختر وقت التذكير باش تفعّل التكرار", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))
            Text("🧭 المجال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = area == null, onClick = { area = null }, label = { Text("بدون", fontSize = 11.sp) })
                AreaStore.areas.forEach { a ->
                    FilterChip(selected = area == a.key, onClick = { area = a.key }, label = { Text(a.emoji + " " + a.label, fontSize = 11.sp) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("☑ خطوات فرعية", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            subs.forEachIndexed { i, st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { subs = subs.filterIndexed { j, _ -> j != i } }) { Text("✕") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(value = newSub, onValueChange = { newSub = it }, placeholder = { Text("زيد خطوة") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (newSub.isNotBlank()) { subs = subs + Subtask(newSub.trim()); newSub = "" }
                }) { Text("＋") }
            }
        }
    }, confirmButton = { Button(onClick = { if (title.isNotBlank()) onAdd(title, description, priority, dueDate, recurrence, if (newSub.isNotBlank()) subs + Subtask(newSub.trim()) else subs, area) }) { Text(if (initial == null) "Add" else "Save") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}
