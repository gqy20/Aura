package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["triggerAtMillis"]),
        Index(value = ["status"]),
        Index(value = ["createdAt"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val triggerAtMillis: Long,
    val delayMillis: Long,
    val exact: Boolean,
    val status: String,
    val source: String = "tool:create_local_reminder",
    val createdAt: Long,
    val updatedAt: Long,
    val firedAt: Long? = null,
    val canceledAt: Long? = null,
)
