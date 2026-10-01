package com.dani.assistant.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TaskItem(
    val id: Int,
    val title: String,
    val description: String = "",
    val priority: String = "Normal",
    val dueDate: Long? = null,
    val isCompleted: Boolean = false
)

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    init { loadTasks() }

    private fun loadTasks() {
        viewModelScope.launch {
            _tasks.value = taskRepository.getAllTasks()
        }
    }

    fun addTask(title: String, description: String, priority: String, dueDate: Long?) {
        viewModelScope.launch {
            val task = TaskItem(
                id = System.currentTimeMillis().toInt(),
                title = title,
                description = description,
                priority = priority,
                dueDate = dueDate,
                isCompleted = false
            )
            taskRepository.addTask(task)
            if (dueDate != null) {
                alarmScheduler.scheduleAlarm(task.id, dueDate, title)
            }
            loadTasks()
        }
    }

    fun toggleTask(id: Int) {
        viewModelScope.launch {
            taskRepository.toggleTask(id)
            loadTasks()
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            taskRepository.deleteTask(id)
            loadTasks()
        }
    }
}
