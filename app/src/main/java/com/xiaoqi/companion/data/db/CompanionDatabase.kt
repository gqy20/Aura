package com.xiaoqi.companion.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaoqi.companion.data.db.converter.Converters
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import com.xiaoqi.companion.data.db.entity.ToolCallEntity

@Database(
    entities = [
        MessageEntity::class,
        MemoryEntity::class,
        AgentStateEntity::class,
        MoodSnapshotEntity::class,
        ToolCallEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentStateDao(): AgentStateDao
    abstract fun moodSnapshotDao(): MoodSnapshotDao
    abstract fun toolCallDao(): ToolCallDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tool_calls` (
                        `id` TEXT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `toolName` TEXT NOT NULL,
                        `argumentsJson` TEXT NOT NULL,
                        `resultJson` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `errorMessage` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_calls_sessionId` ON `tool_calls` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_calls_toolName` ON `tool_calls` (`toolName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_calls_status` ON `tool_calls` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_calls_createdAt` ON `tool_calls` (`createdAt`)")
            }
        }
    }
}
