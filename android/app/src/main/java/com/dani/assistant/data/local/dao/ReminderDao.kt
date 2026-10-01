package com.dani.assistant.data.local.dao

import androidx.room.*
import com.dani.assistant.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE trigger_time > :now AND is_active = 1 ORDER BY trigger_time ASC")
    suspend fun getActiveFutureReminders(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE trigger_time > :now AND is_active = 1 ORDER BY trigger_time ASC")
    fun getActiveFutureRemindersFlow(now: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE task_id = :taskId AND is_active = 1 LIMIT 1")
    fun getReminderByTaskId(taskId: Long): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE task_id = :taskId LIMIT 1")
    suspend fun getReminderEntityByTaskIdSync(taskId: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    suspend fun deleteReminderByTaskId(taskId: Long)

    @Query("UPDATE reminders SET is_active = 0 WHERE id = :id")
    suspend fun deactivateReminder(id: Long)

    @Query("UPDATE reminders SET is_active = 0 WHERE task_id = :taskId")
    suspend fun deactivateReminderByTaskId(taskId: Long)
}
