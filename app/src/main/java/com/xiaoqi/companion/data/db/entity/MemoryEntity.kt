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
        Index(value = ["pinned"]),
        Index(value = ["archived"]),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val type: MemoryType,
    val content: String,
    val source: String = "",
    @ColumnInfo(defaultValue = "0.5") val importance: Float = 0.5f,
    @ColumnInfo(defaultValue = "0.7") val confidence: Float = 0.7f,
    @ColumnInfo(defaultValue = "[]") val sourceMessageIds: String = "[]",
    val timestamp: Long,
    val updatedAt: Long = timestamp,
    val expiresAt: Long? = null,
    @ColumnInfo(defaultValue = "normal") val sensitivity: String = "normal",
    val lastAccessed: Long = timestamp,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    // M4 视觉入 memory:base64 字符串(可空,大多数 memory 无图;有图时典型 50-200KB)
    val imageBase64: String? = null,
    @ColumnInfo(defaultValue = "image/jpeg") val imageMediaType: String = "image/jpeg",
)
