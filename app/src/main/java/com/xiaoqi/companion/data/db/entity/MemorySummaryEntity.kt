package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaoqi.companion.data.db.converter.SummaryType

@Entity(
    tableName = "memory_summaries",
    indices = [
        Index(value = ["type"]),
        Index(value = ["lastAccessed"]),
        Index(value = ["startAt", "endAt"]),
    ],
)
data class MemorySummaryEntity(
    @PrimaryKey val id: String,
    val type: SummaryType,
    val title: String,
    val summary: String,
    val keywords: String = "[]",
    val sourceMessageIds: String = "[]",
    val startAt: Long? = null,
    val endAt: Long? = null,
    @ColumnInfo(defaultValue = "0.5") val importance: Float = 0.5f,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val lastAccessed: Long = updatedAt,
)
