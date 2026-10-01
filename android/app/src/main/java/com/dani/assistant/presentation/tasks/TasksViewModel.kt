package com.dani.assistant.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.core.alarm.ScheduleResult
import com.dani.assistant.data.repository.TaskRepository
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TaskFilter(val arabicTitle: String) {
    TODAY("اليوم"),
    ALL("الكل")
}

data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val isBottomSheetOpen: Boolean = false,
    val taskToEdit: Task? = null,
    val existingReminderToEdit: Reminder? = null,
    val taskToDelete: Task? = null,
    val showExactAlarmPermissionWarning: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    private val _isBottomSheetOpen = MutableStateFlow(false)
    private val _taskToEdit = MutableStateFlow<Task?>(null)
    private val _existingReminder = MutableStateFlow<Reminder?>(null)
    private val _taskToDelete = MutableStateFlow<Task?>(null)
    private val _showExactAlarmWarning = MutableStateFlow(false)

    val uiState: StateFlow<TasksUiState> = _filter
        .flatMapLatest { filter ->
            val tasksFlow = when (filter) {
                TaskFilter.TODAY -> taskRepository.getTodayTasks()
                TaskFilter.ALL -> taskRepository.getAllTasks()
            }
            combine(
                tasksFlow,
                _isBottomSheetOpen,
                _taskToEdit,
                _existingReminder,
                _taskToDelete
            ) { tasks, isSheetOpen, editTask, existingRem, delTask ->
                TasksUiState(
                    tasks = tasks,
                    filter = filter,
                    isBottomSheetOpen = isSheetOpen,
                    taskToEdit = editTask,
                    existingReminderToEdit = existingRem,
                    taskToDelete = delTask,
                    showExactAlarmPermissionWarning = _showExactAlarmWarning.value
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TasksUiState()
        )

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun openAddBottomSheet() {
        _taskToEdit.value = null
        _existingReminder.value = null
        _isBottomSheetOpen.value = true
    }

    fun openEditBottomSheet(task: Task) {
        _taskToEdit.value = task
        viewModelScope.launch {
            val reminder = taskRepository.getReminderForTask(task.id).firstOrNull()
            _existingReminder.value = reminder
            _isBottomSheetOpen.value = true
        }
    }

    fun closeBottomSheet() {
        _isBottomSheetOpen.value = false
        _taskToEdit.value = null
        _existingReminder.value = null
    }

    fun requestDeleteConfirmation(task: Task) {
        _taskToDelete.value = task
    }

    fun dismissDeleteConfirmation() {
        _taskToDelete.value = null
    }

    fun confirmDelete() {
        val task = _taskToDelete.value ?: return
        viewModelScope.launch {
            taskRepository.deleteTask(task)
            _taskToDelete.value = null
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompleted(task)
        }
    }

    fun dismissExactAlarmWarning() {
        _showExactAlarmWarning.value = false
    }

    fun saveTaskWithReminder(
        title: String,
        description: String?,
        priority: PriorityLevel,
        estimatedMinutes: Int,
        dueDate: Long?,
        reminderEnabled: Boolean,
        reminderTime: Long?,
        reminderType: ReminderType
    ) {
        if (title.isBlank()) return

        viewModelScope.launch {
            val currentEdit = _taskToEdit.value
            val savedTaskId: Long

            if (currentEdit == null) {
                // New task
                val newTask = Task(
                    title = title.trim(),
                    description = description?.trim(),
                    priority = priority,
                    estimatedMinutes = estimatedMinutes,
                    dueDate = dueDate
                )
                savedTaskId = taskRepository.insertTask(newTask)
            } else {
                // Update task
                val updatedTask = currentEdit.copy(
                    title = title.trim(),
                    description = description?.trim(),
                    priority = priority,
                    estimatedMinutes = estimatedMinutes,
                    dueDate = dueDate
                )
                taskRepository.updateTask(updatedTask)
                savedTaskId = currentEdit.id
            }

            // Handle Reminder scheduling / cancellation
            if (reminderEnabled && reminderTime != null && reminderTime > System.currentTimeMillis()) {
                val scheduleResult = taskRepository.setTaskReminder(
                    taskId = savedTaskId,
                    title = title.trim(),
                    triggerTime = reminderTime,
                    type = reminderType
                )
                if (scheduleResult is ScheduleResult.ExactAlarmPermissionRequired) {
                    _showExactAlarmWarning.value = true
                }
            } else {
                taskRepository.cancelTaskReminder(savedTaskId)
            }

            closeBottomSheet()
        }
    }

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            alarmScheduler: AlarmScheduler
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TasksViewModel(taskRepository, alarmScheduler) as T
                }
            }
    }
}
