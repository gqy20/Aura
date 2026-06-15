package com.xiaoqi.companion.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaoqi.companion.data.db.converter.Converters
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.dao.ReminderDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import com.xiaoqi.companion.data.db.entity.AgentStateEntity
import com.xiaoqi.companion.data.db.entity.HealthSnapshotEntity
import com.xiaoqi.companion.data.db.entity.InsightEntity
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
        InsightEntity::class,
        HealthSnapshotEntity::class,
    ],
    version = 9,
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
    abstract fun insightDao(): InsightDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `insights` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `createdAt` INTEGER NOT NULL,
                        `triggerType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `headline` TEXT NOT NULL,
                        `bodyMarkdown` TEXT NOT NULL,
                        `evidence` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `relevanceWindow` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `mutedUntil` INTEGER,
                        `deliveredAt` INTEGER,
                        `userClickedAt` INTEGER,
                        `userFeedback` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_createdAt` ON `insights` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_category` ON `insights` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_insights_status` ON `insights` (`status`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // M4 Vision 视觉入 memory。imageBase64 文本可空(base64 字符串);imageMediaType 兜底 image/jpeg
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `imageBase64` TEXT")
                db.execSQL("ALTER TABLE `memories` ADD COLUMN `imageMediaType` TEXT NOT NULL DEFAULT 'image/jpeg'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Health Connect 接入:每日聚合健康快照(步数/心率/睡眠)
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_snapshots` (
                        `date` INTEGER NOT NULL,
                        `steps` INTEGER NOT NULL,
                        `distance_meters` REAL NOT NULL,
                        `calories_kcal` REAL NOT NULL,
                        `avg_heart_rate` INTEGER,
                        `resting_heart_rate` INTEGER,
                        `min_heart_rate` INTEGER,
                        `max_heart_rate` INTEGER,
                        `sleep_duration_minutes` INTEGER,
                        `sleep_stages_json` TEXT NOT NULL DEFAULT '[]',
                        `source_packages` TEXT NOT NULL DEFAULT '[]',
                        `fetched_at` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_snapshots_date` ON `health_snapshots` (`date`)")
            }
        }
    }
}
