package com.dani.assistant.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val status: String = "ACTIVE", // ACTIVE, ON_HOLD, COMPLETED, ARCHIVED
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#2DD4BF",
    @ColumnInfo(name = "target_date")
    val targetDate: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
