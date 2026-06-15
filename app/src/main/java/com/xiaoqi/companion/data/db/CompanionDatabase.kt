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
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.dao.ReminderDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.MessageEntity
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import com.xiaoqi.companion.data.db.entity.MoodSnapshotEntity
import com.xiaoqi.companion.data.db.entity.ReminderEntity
import com.xiaoqi.companion.data.db.entity.ToolCallEntity

@Database(
    entities = [
        MessageEntity::class,
        MemoryEntity::class,
        MemorySummaryEntity::class,
        AgentStateEntity::class,
        MoodSnapshotEntity::class,
        ToolCallEntity::class,
        ReminderEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memorySummaryDao(): MemorySummaryDao
    abstract fun agentStateDao(): AgentStateDao
    abstract fun moodSnapshotDao(): MoodSnapshotDao
    abstract fun toolCallDao(): ToolCallDao
    abstract fun reminderDao(): ReminderDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memory_summaries` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `keywords` TEXT NOT NULL DEFAULT '[]',
                        `sourceMessageIds` TEXT NOT NULL DEFAULT '[]',
                        `startAt` INTEGER,
                        `endAt` INTEGER,
                        `importance` REAL NOT NULL DEFAULT 0.5,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastAccessed` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_summaries_type` ON `memory_summaries` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_summaries_lastAccessed` ON `memory_summaries` (`lastAccessed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_summaries_startAt_endAt` ON `memory_summaries` (`startAt`, `endAt`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.7")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `sourceMessageIds` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `expiresAt` INTEGER")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `sensitivity` TEXT NOT NULL DEFAULT 'normal'")
                db.execSQL("UPDATE `memories` SET `updatedAt` = `timestamp` WHERE `updatedAt` = 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `triggerAtMillis` INTEGER NOT NULL,
                        `delayMillis` INTEGER NOT NULL,
                        `exact` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'tool:create_local_reminder',
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `firedAt` INTEGER,
                        `canceledAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtMillis` ON `reminders` (`triggerAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_createdAt` ON `reminders` (`createdAt`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_pinned` ON `memories` (`pinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_archived` ON `memories` (`archived`)")
            }
        }
    }
}
