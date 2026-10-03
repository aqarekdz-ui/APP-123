package com.dani.assistant.domain.model

data class Task(
    val id: Long = 0,
    val projectId: Long? = null,
    val title: String,
    val description: String? = null,
    val priority: PriorityLevel = PriorityLevel.MEDIUM,
    val status: TaskStatus = TaskStatus.NEW,
    val estimatedMinutes: Int = 30,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE
) {
    val isCompleted: Boolean
        get() = status == TaskStatus.COMPLETED
}

enum class TaskStatus {
    NEW,
    IN_PROGRESS,
    COMPLETED,
    POSTPONED,
    CANCELLED
}

enum class Recurrence(val arabic: String) {
    NONE("بدون"),
    DAILY("يومي"),
    WEEKLY("أسبوعي"),
    MONTHLY("شهري")
}
