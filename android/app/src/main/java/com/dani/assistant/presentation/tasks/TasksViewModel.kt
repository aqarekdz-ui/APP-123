package com.dani.assistant.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.data.repository.TaskRepository
import com.dani.assistant.domain.model.PriorityLevel
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

    fun addTask(title: String, description: String, priority: String, dueDate: Long?) {
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
                status = TaskStatus.NEW
            )
            
            taskRepository.insertTask(newTask)
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch {
            taskRepository.toggleTaskCompleted(task)
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
