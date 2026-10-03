package com.dani.assistant.data.repository

import com.dani.assistant.DaniApplication
import com.dani.assistant.widget.TaskWidgetProvider
import com.dani.assistant.core.alarm.AlarmScheduler
import com.dani.assistant.core.alarm.ScheduleResult
import com.dani.assistant.data.local.dao.ReminderDao
import com.dani.assistant.data.local.dao.TaskDao
import com.dani.assistant.data.local.entity.ReminderEntity
import com.dani.assistant.data.local.entity.TaskEntity
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Recurrence
import com.dani.assistant.domain.model.SubtaskCodec
import kotlinx.coroutines.flow.first
import com.dani.assistant.domain.model.Reminder
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import com.dani.assistant.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

interface TaskRepository {
    fun getAllTasks(): Flow<List<Task>>
    fun getTodayTasks(): Flow<List<Task>>
    fun getTaskById(id: Long): Flow<Task?>
    suspend fun getTaskByIdSync(id: Long): Task?
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(task: Task)
    suspend fun deleteTaskById(id: Long)
    suspend fun toggleTaskCompleted(task: Task)

    // Reminder operations
    fun getReminderForTask(taskId: Long): Flow<Reminder?>
    suspend fun setTaskReminder(taskId: Long, title: String, triggerTime: Long, type: ReminderType): ScheduleResult
    suspend fun cancelTaskReminder(taskId: Long)
}

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) : TaskRepository {

    private fun notifyWidget() {
        try { TaskWidgetProvider.refresh(DaniApplication.instance) } catch (e: Exception) { }
    }

    override fun getAllTasks(): Flow<List<Task>> {
        return taskDao.getAllTasks().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTodayTasks(): Flow<List<Task>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return taskDao.getTodayTasks(startOfDay, endOfDay).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getTaskById(id: Long): Flow<Task?> {
        return taskDao.getTaskById(id).map { it?.toDomainModel() }
    }

    override suspend fun getTaskByIdSync(id: Long): Task? {
        val entity = taskDao.getTaskByIdSync(id)
        return entity?.toDomainModel()
    }

    override suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task.toEntity()).also { notifyWidget() }
    }

    override suspend fun updateTask(task: Task) {
        taskDao.updateTask(task.toEntity())
        notifyWidget()
    }

    override suspend fun deleteTask(task: Task) {
        // Cancel alarm if exists
        cancelTaskReminder(task.id)
        taskDao.deleteTask(task.toEntity())
        notifyWidget()
    }

    override suspend fun deleteTaskById(id: Long) {
        cancelTaskReminder(id)
        taskDao.deleteTaskById(id)
        notifyWidget()
    }

    override suspend fun toggleTaskCompleted(task: Task) {
        val isNowCompleted = !task.isCompleted
        val updatedTask = task.copy(
            status = if (isNowCompleted) TaskStatus.COMPLETED else TaskStatus.NEW,
            completedAt = if (isNowCompleted) System.currentTimeMillis() else null
        )
        taskDao.updateTask(updatedTask.toEntity())

        // If completed, cancel any scheduled reminder for this task
        if (isNowCompleted) {
            cancelTaskReminder(task.id)
            if (task.recurrence != Recurrence.NONE) spawnNextOccurrence(task)
        }
        notifyWidget()
    }

    /** مهمة متكررة: عند الإنجاز ننشئ النسخة الجاية بموعد مستقبلي ونجدول تذكيرها. */
    private suspend fun spawnNextOccurrence(task: Task) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = task.dueDate ?: now
        fun step() {
            when (task.recurrence) {
                Recurrence.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                Recurrence.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                Recurrence.MONTHLY -> cal.add(Calendar.MONTH, 1)
                Recurrence.NONE -> {}
            }
        }
        step()
        while (cal.timeInMillis <= now) step()
        val next = cal.timeInMillis

        // منع التكرار إذا الإنجاز تلغى وتعاود (toggle)
        val exists = taskDao.getAllTasks().first().any {
            it.title == task.title && it.recurrence == task.recurrence.name &&
                it.dueDate == next && it.status != TaskStatus.COMPLETED.name
        }
        if (exists) return

        val newId = taskDao.insertTask(
            task.copy(
                id = 0,
                status = TaskStatus.NEW,
                dueDate = next,
                createdAt = now,
                completedAt = null,
                subtasks = task.subtasks.map { it.copy(done = false) }
            ).toEntity()
        )
        setTaskReminder(newId, task.title, next, ReminderType.NOTIFICATION)
    }

    override fun getReminderForTask(taskId: Long): Flow<Reminder?> {
        return reminderDao.getReminderByTaskId(taskId).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun setTaskReminder(
        taskId: Long,
        title: String,
        triggerTime: Long,
        type: ReminderType
    ): ScheduleResult {
        // Remove existing reminder first if any
        val existing = reminderDao.getReminderEntityByTaskIdSync(taskId)
        if (existing != null) {
            alarmScheduler.cancel(existing.id)
            reminderDao.deleteReminderById(existing.id)
        }

        // Insert new reminder into Room
        val reminderEntity = ReminderEntity(
            taskId = taskId,
            title = title,
            triggerTime = triggerTime,
            reminderType = type.name,
            isActive = true
        )
        val newReminderId = reminderDao.insertReminder(reminderEntity)

        // Schedule with AlarmManager
        val reminder = Reminder(
            id = newReminderId,
            taskId = taskId,
            title = title,
            triggerTime = triggerTime,
            reminderType = type,
            isActive = true
        )

        val scheduleResult = alarmScheduler.schedule(reminder)
        if (scheduleResult is ScheduleResult.ExactAlarmPermissionRequired) {
            // Note: Reminder is saved in Room, but system exact alarm permission is needed
            return scheduleResult
        }
        return scheduleResult
    }

    override suspend fun cancelTaskReminder(taskId: Long) {
        val existing = reminderDao.getReminderEntityByTaskIdSync(taskId)
        if (existing != null) {
            alarmScheduler.cancel(existing.id)
            reminderDao.deleteReminderById(existing.id)
        }
    }

    // Mapper functions
    private fun TaskEntity.toDomainModel(): Task {
        return Task(
            id = id,
            projectId = projectId,
            title = title,
            description = description,
            priority = PriorityLevel.fromLevel(priority),
            status = try { TaskStatus.valueOf(status) } catch (e: Exception) { TaskStatus.NEW },
            estimatedMinutes = estimatedMinutes,
            dueDate = dueDate,
            createdAt = createdAt,
            completedAt = completedAt,
            recurrence = try { Recurrence.valueOf(recurrence) } catch (e: Exception) { Recurrence.NONE },
            subtasks = SubtaskCodec.decode(subtasks)
        )
    }

    private fun Task.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            projectId = projectId,
            title = title,
            description = description,
            priority = priority.level,
            status = status.name,
            estimatedMinutes = estimatedMinutes,
            dueDate = dueDate,
            createdAt = createdAt,
            completedAt = completedAt,
            recurrence = recurrence.name,
            subtasks = SubtaskCodec.encode(subtasks)
        )
    }

    private fun ReminderEntity.toDomainModel(): Reminder {
        return Reminder(
            id = id,
            taskId = taskId,
            title = title,
            triggerTime = triggerTime,
            reminderType = try { ReminderType.valueOf(reminderType) } catch (e: Exception) { ReminderType.NOTIFICATION },
            isActive = isActive
        )
    }
}
