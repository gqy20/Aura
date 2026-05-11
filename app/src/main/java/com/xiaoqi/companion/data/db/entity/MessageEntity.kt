package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaoqi.companion.data.db.converter.MessageMetadata
import com.xiaoqi.companion.data.db.converter.MessageRole

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["session_id", "timestamp"]),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(defaultValue = "'USER'") val role: MessageRole,
    val content: String,
    val imageBase64: String? = null,
    val timestamp: Long,
    val metadata: MessageMetadata? = null,
)
