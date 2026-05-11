package com.xiaoqi.companion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xiaoqi.companion.data.db.converter.Converters
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity

@Database(
    entities = [
        MessageEntity::class,
        MemoryEntity::class,
        AgentStateEntity::class,
        MoodSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentStateDao(): AgentStateDao
    abstract fun moodSnapshotDao(): MoodSnapshotDao
}
