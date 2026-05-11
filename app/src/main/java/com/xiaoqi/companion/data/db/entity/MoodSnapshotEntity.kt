package com.xiaoqi.companion.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = AgentStateEntity::class,
            parentColumns = ["companion_id"],
            childColumns = ["companion_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["companion_id", "timestamp"]),
    ],
)
data class MoodSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "companion_id") val companionId: String,
    val mood: String = "",
    val trigger: String? = null,
    @ColumnInfo(defaultValue = "0.5") val intensity: Float = 0.5f,
    val timestamp: Long,
)
