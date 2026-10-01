package com.dani.assistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["trigger_time", "is_active"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "task_id")
    val taskId: Long,
    val title: String,
    @ColumnInfo(name = "trigger_time")
    val triggerTime: Long, // Epoch milliseconds
    @ColumnInfo(name = "reminder_type")
    val reminderType: String = "NOTIFICATION", // ALARM_CLOCK or NOTIFICATION
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)
