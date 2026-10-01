package com.dani.assistant.presentation.tasks

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddBottomSheet() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة مهمة جديدة"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Text(
                text = "المهام",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Exact Alarm Permission Warning Card (if needed on Android 12+)
            if (uiState.showExactAlarmPermissionWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "مطلوب إذن المنبهات الدقيقة",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Text(
                            text = "لكي يوقظك DANI في الوقت المحدد بالثانية، يرجى تفعيل إذن المنبهات في إعدادات النظام.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.dismissExactAlarmWarning() }) {
                                Text("تجاهل", color = MaterialTheme.colorScheme.onErrorContainer)
                            }

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                    viewModel.dismissExactAlarmWarning()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("فتح الإعدادات")
                            }
                        }
                    }
                }
            }

            // Filter Row ("الكل" / "اليوم")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskFilter.entries.forEach { filter ->
                    val isSelected = uiState.filter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                text = filter.arabicTitle,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Task List or Empty State
            if (uiState.tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (uiState.filter == TaskFilter.TODAY)
                                "لا توجد مهام مجدولة لليوم."
                            else
                                "لا توجد مهام مسجلة حالياً.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "اضغط على زر + في الأسفل لإضافة مهمة جديدة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.tasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                            onClick = { viewModel.openEditBottomSheet(task) },
                            onDelete = { viewModel.requestDeleteConfirmation(task) }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Modal Bottom Sheet (with Reminder section)
    if (uiState.isBottomSheetOpen) {
        TaskAddEditBottomSheet(
            taskToEdit = uiState.taskToEdit,
            existingReminder = uiState.existingReminderToEdit,
            onDismiss = { viewModel.closeBottomSheet() },
            onSave = { title, desc, priority, minutes, dueDate, remEnabled, remTime, remType ->
                viewModel.saveTaskWithReminder(
                    title = title,
                    description = desc,
                    priority = priority,
                    estimatedMinutes = minutes,
                    dueDate = dueDate,
                    reminderEnabled = remEnabled,
                    reminderTime = remTime,
                    reminderType = remType
                )
            }
        )
    }

    // Delete Confirmation Dialog
    uiState.taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = {
                Text(
                    text = "حذف المهمة",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف المهمة: \"${task.title}\"؟ سيتم إلغاء أي منبه مرتبط بها تلقائياً.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priorityColor = Color(task.priority.colorHex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Indicator Strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Checkbox
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Title and Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )

                if (!task.description.isNullOrBlank()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Badges (Priority + Duration)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority Badge
                    Surface(
                        color = priorityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.priority.arabicTitle,
                            color = priorityColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Duration Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${task.estimatedMinutes} دقيقة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Delete Action
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف المهمة",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskAddEditBottomSheet(
    taskToEdit: Task?,
    existingReminder: Reminder?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        desc: String?,
        priority: PriorityLevel,
        minutes: Int,
        dueDate: Long?,
        reminderEnabled: Boolean,
        reminderTime: Long?,
        reminderType: ReminderType
    ) -> Unit
) {
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var selectedPriority by remember { mutableStateOf(taskToEdit?.priority ?: PriorityLevel.MEDIUM) }
    var estimatedMinutes by remember { mutableStateOf(taskToEdit?.estimatedMinutes ?: 30) }
    var dueDate by remember { mutableStateOf(taskToEdit?.dueDate) }

    // Reminder state
    var reminderEnabled by remember { mutableStateOf(existingReminder != null) }
    var reminderType by remember { mutableStateOf(existingReminder?.reminderType ?: ReminderType.ALARM_CLOCK) }
    var reminderTime by remember {
        mutableStateOf(
            existingReminder?.triggerTime ?: (System.currentTimeMillis() + 3 * 60 * 1000L) // Default +3 mins
        )
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (taskToEdit == null) "إضافة مهمة جديدة" else "تعديل المهمة",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان المهمة *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Description input
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("تفاصيل أو ملاحظات (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // Priority Selector
            Text(
                text = "مستوى الأولوية:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PriorityLevel.entries.forEach { p ->
                    val isSelected = selectedPriority == p
                    val pColor = Color(p.colorHex)

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPriority = p },
                        label = {
                            Text(
                                text = p.arabicTitle,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = pColor.copy(alpha = 0.25f),
                            selectedLabelColor = pColor
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ==========================================
            // REMINDER SECTION (Phase 3a)
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (reminderType == ReminderType.ALARM_CLOCK)
                                    Icons.Default.Alarm
                                else
                                    Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "تفعيل التذكير المحلي",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it }
                        )
                    }

                    if (reminderEnabled) {
                        // Reminder Type Toggle (منبه / إشعار)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReminderType.entries.forEach { type ->
                                val isTypeSelected = reminderType == type
                                FilterChip(
                                    selected = isTypeSelected,
                                    onClick = { reminderType = type },
                                    label = { Text(type.arabicTitle) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (type == ReminderType.ALARM_CLOCK)
                                                Icons.Default.Alarm
                                            else
                                                Icons.Default.Notifications,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Quick Reminder Time Presets (Ideal for testing!)
                        Text(
                            text = "موعد التنبيه: ${timeFormatter.format(Date(reminderTime))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // +3 mins (Real-device test)
                            OutlinedButton(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + 3 * 60 * 1000L
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("بعد 3 دقائق (فحص)", fontSize = 10.sp)
                            }

                            // +30 mins
                            OutlinedButton(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + 30 * 60 * 1000L
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("بعد 30 دقيقة", fontSize = 10.sp)
                            }

                            // +1 hour
                            OutlinedButton(
                                onClick = {
                                    reminderTime = System.currentTimeMillis() + 60 * 60 * 1000L
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("بعد ساعة", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            title,
                            description,
                            selectedPriority,
                            estimatedMinutes,
                            dueDate,
                            reminderEnabled,
                            if (reminderEnabled) reminderTime else null,
                            reminderType
                        )
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (taskToEdit == null) "حفظ المهمة والتنبيه" else "تحديث المهمة والتنبيه",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
