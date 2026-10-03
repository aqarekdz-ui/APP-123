package com.dani.assistant.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.core.areas.AreaStore
import com.dani.assistant.core.alarm.ScheduleResult
import com.dani.assistant.domain.model.ReminderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dani.assistant.data.repository.TaskRepository
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Recurrence
import com.dani.assistant.domain.model.Subtask
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = taskRepository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _needsExactAlarmPermission = MutableStateFlow(false)
    val needsExactAlarmPermission: StateFlow<Boolean> = _needsExactAlarmPermission.asStateFlow()

    fun dismissPermissionPrompt() { _needsExactAlarmPermission.value = false }

    // يزيد كل ما تبدّل مجال مهمة (باش الواجهة تعيد قراءة المجالات)
    private val _areaTick = MutableStateFlow(0)
    val areaTick: StateFlow<Int> = _areaTick.asStateFlow()

    fun addTask(title: String, description: String, priority: String, dueDate: Long?, recurrence: Recurrence = Recurrence.NONE, subtasks: List<Subtask> = emptyList(), area: String? = null) {
        viewModelScope.launch {
            val priorityLevel = when (priority) {
                "High" -> PriorityLevel.IMPORTANT
                "Low" -> PriorityLevel.LOW
                else -> PriorityLevel.MEDIUM
            }
            
            val newTask = Task(
                title = title,
                description = description.ifEmpty { null },
                priority = priorityLevel,
                dueDate = dueDate,
                status = TaskStatus.NEW,
                recurrence = if (dueDate != null) recurrence else Recurrence.NONE,
                subtasks = subtasks
            )
            
            val id = taskRepository.insertTask(newTask)
            AreaStore.set(DaniApplication.instance, id, area)
            _areaTick.value = _areaTick.value + 1
            if (dueDate != null && dueDate > System.currentTimeMillis()) {
                val result = taskRepository.setTaskReminder(id, title, dueDate, ReminderType.NOTIFICATION)
                if (result is ScheduleResult.ExactAlarmPermissionRequired) {
                    _needsExactAlarmPermission.value = true
                }
            }
        }
    }

    fun updateTask(task: Task, title: String, description: String, priority: String, dueDate: Long?, recurrence: Recurrence = task.recurrence, subtasks: List<Subtask> = task.subtasks, area: String? = AreaStore.get(DaniApplication.instance, task.id)) {
        viewModelScope.launch {
            val newPriority = if (priorityLabel(task.priority) == priority) task.priority else when (priority) {
                "High" -> PriorityLevel.IMPORTANT
                "Low" -> PriorityLevel.LOW
                else -> PriorityLevel.MEDIUM
            }
            val updated = task.copy(
                title = title,
                description = description.ifEmpty { null },
                priority = newPriority,
                dueDate = dueDate,
                recurrence = if (dueDate != null) recurrence else Recurrence.NONE,
                subtasks = subtasks
            )
            taskRepository.updateTask(updated)
            AreaStore.set(DaniApplication.instance, task.id, area)
            _areaTick.value = _areaTick.value + 1

            if (dueDate != task.dueDate && !task.isCompleted) {
                if (dueDate != null && dueDate > System.currentTimeMillis()) {
                    val result = taskRepository.setTaskReminder(task.id, title, dueDate, ReminderType.NOTIFICATION)
                    if (result is ScheduleResult.ExactAlarmPermissionRequired) {
                        _needsExactAlarmPermission.value = true
                    }
                } else {
                    taskRepository.cancelTaskReminder(task.id)
                }
            } else if (title != task.title && dueDate != null && dueDate > System.currentTimeMillis() && !task.isCompleted) {
                // العنوان تبدّل: حدّث نص التذكير
                taskRepository.setTaskReminder(task.id, title, dueDate, ReminderType.NOTIFICATION)
            }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompleted(task)
        }
    }

    fun toggleSubtask(task: Task, index: Int) {
        if (index !in task.subtasks.indices) return
        viewModelScope.launch {
            val list = task.subtasks.mapIndexed { i, st -> if (i == index) st.copy(done = !st.done) else st }
            taskRepository.updateTask(task.copy(subtasks = list))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
        }
    }

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            alarmScheduler: AlarmScheduler
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TasksViewModel(taskRepository, alarmScheduler) as T
                }
            }
        }
    }
}
