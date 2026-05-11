package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaoqi.companion.data.db.converter.MemoryType

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["type"]),
        Index(value = ["lastAccessed"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: MemoryType,
    val content: String,
    val source: String = "",
    @ColumnInfo(defaultValue = "0.5") val importance: Float = 0.5f,
    val timestamp: Long,
    val lastAccessed: Long = timestamp,
)
