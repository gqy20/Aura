package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_calls",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["toolName"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class ToolCallEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String = "",
    val status: String,
    val createdAt: Long,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
)
