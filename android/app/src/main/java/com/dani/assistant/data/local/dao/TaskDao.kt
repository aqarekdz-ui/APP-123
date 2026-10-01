package com.dani.assistant.data.local.dao

import androidx.room.*
import com.dani.assistant.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY priority ASC, due_date ASC, created_at DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("""
        SELECT * FROM tasks 
        WHERE due_date >= :startOfDay AND due_date <= :endOfDay 
        AND status NOT IN ('COMPLETED', 'CANCELLED')
        ORDER BY priority ASC, due_date ASC
    """)
    fun getTodayTasks(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getTaskById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskByIdSync(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)
}
