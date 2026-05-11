package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_state",
    indices = [
        Index(value = ["companion_id"], unique = true),
    ],
)
data class AgentStateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "companion_id") val companionId: String,
    val mood: String = "",
    @ColumnInfo(name = "emotion_vector") val emotionVector: String = "{}",
    @ColumnInfo(name = "relationship_level") val relationshipLevel: Float = 0f,
    @ColumnInfo(name = "last_interaction_at") val lastInteractionAt: Long = 0L,
    val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
