package com.dani.assistant.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks

    init {
        loadTasks()
    }

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
                dueDate = dueDate
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

    companion object {
        fun provideFactory(
            taskRepository: TaskRepository,
            alarmScheduler: AlarmScheduler
        ): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TasksViewModel(taskRepository, alarmScheduler) as T
                }
            }
        }
    }
}

interface TaskRepository {
    suspend fun getAllTasks(): List<TaskItem>
    suspend fun addTask(task: TaskItem)
    suspend fun toggleTask(id: Int)
    suspend fun deleteTask(id: Int)
}

interface AlarmScheduler {
    fun scheduleAlarm(taskId: Int, time: Long, title: String)
}
