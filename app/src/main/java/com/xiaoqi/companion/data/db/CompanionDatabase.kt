package com.xiaoqi.companion.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaoqi.companion.data.db.converter.Converters
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.HealthSnapshotDao
import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MessageSearchDao
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
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CompanionDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun messageSearchDao(): MessageSearchDao
    abstract fun memoryDao(): MemoryDao
    abstract fun memorySummaryDao(): MemorySummaryDao
    abstract fun agentStateDao(): AgentStateDao
    abstract fun moodSnapshotDao(): MoodSnapshotDao
    abstract fun toolCallDao(): ToolCallDao
    abstract fun reminderDao(): ReminderDao
    abstract fun insightDao(): InsightDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao

    companion object {
        val MIGRATION_1_2 = object : DualMigration(1, 2) {
            override fun apply(executor: SqlExecutor) {
                executor.exec(
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
                executor.exec("CREATE INDEX IF NOT EXISTS `index_tool_calls_sessionId` ON `tool_calls` (`sessionId`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_tool_calls_toolName` ON `tool_calls` (`toolName`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_tool_calls_status` ON `tool_calls` (`status`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_tool_calls_createdAt` ON `tool_calls` (`createdAt`)")
            }
        }

        val MIGRATION_2_3 = object : DualMigration(2, 3) {
            override fun apply(executor: SqlExecutor) {
                executor.exec(
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
                executor.exec("CREATE INDEX IF NOT EXISTS `index_memory_summaries_type` ON `memory_summaries` (`type`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_memory_summaries_lastAccessed` ON `memory_summaries` (`lastAccessed`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_memory_summaries_startAt_endAt` ON `memory_summaries` (`startAt`, `endAt`)")
            }
        }

        val MIGRATION_3_4 = object : DualMigration(3, 4) {
            override fun apply(executor: SqlExecutor) {
                executor.exec("ALTER TABLE `memories` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.7")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `sourceMessageIds` TEXT NOT NULL DEFAULT '[]'")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `expiresAt` INTEGER")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `sensitivity` TEXT NOT NULL DEFAULT 'normal'")
                executor.exec("UPDATE `memories` SET `updatedAt` = `timestamp` WHERE `updatedAt` = 0")
            }
        }

        val MIGRATION_4_5 = object : DualMigration(4, 5) {
            override fun apply(executor: SqlExecutor) {
                executor.exec(
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
                executor.exec("CREATE INDEX IF NOT EXISTS `index_reminders_triggerAtMillis` ON `reminders` (`triggerAtMillis`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_reminders_createdAt` ON `reminders` (`createdAt`)")
            }
        }

        val MIGRATION_5_6 = object : DualMigration(5, 6) {
            override fun apply(executor: SqlExecutor) {
                executor.exec("ALTER TABLE `memories` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_memories_pinned` ON `memories` (`pinned`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_memories_archived` ON `memories` (`archived`)")
            }
        }

        val MIGRATION_6_7 = object : DualMigration(6, 7) {
            override fun apply(executor: SqlExecutor) {
                executor.exec(
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
                executor.exec("CREATE INDEX IF NOT EXISTS `index_insights_createdAt` ON `insights` (`createdAt`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_insights_category` ON `insights` (`category`)")
                executor.exec("CREATE INDEX IF NOT EXISTS `index_insights_status` ON `insights` (`status`)")
            }
        }

        val MIGRATION_7_8 = object : DualMigration(7, 8) {
            override fun apply(executor: SqlExecutor) {
                // M4 Vision 视觉入 memory。imageBase64 文本可空(base64 字符串);imageMediaType 兜底 image/jpeg
                executor.exec("ALTER TABLE `memories` ADD COLUMN `imageBase64` TEXT")
                executor.exec("ALTER TABLE `memories` ADD COLUMN `imageMediaType` TEXT NOT NULL DEFAULT 'image/jpeg'")
            }
        }

        val MIGRATION_8_9 = object : DualMigration(8, 9) {
            override fun apply(executor: SqlExecutor) {
                // Health Connect 接入:每日聚合健康快照(步数/心率/睡眠)
                executor.exec(
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
                executor.exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_health_snapshots_date` ON `health_snapshots` (`date`)")
            }
        }

        val MIGRATION_9_10 = object : DualMigration(9, 10) {
            override fun apply(executor: SqlExecutor) {
                createMessageSearchTables(executor)
                rebuildMessageSearchIndex(executor)
            }
        }

        internal fun createMessageSearchTables(executor: SqlExecutor) {
            executor.exec(
                """
                CREATE TABLE IF NOT EXISTS `message_search_docs` (
                    `search_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `message_id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `has_image` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            executor.exec(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_message_search_docs_message_id` " +
                    "ON `message_search_docs` (`message_id`)"
            )
            executor.exec(
                "CREATE INDEX IF NOT EXISTS `index_message_search_docs_session_timestamp` " +
                    "ON `message_search_docs` (`session_id`, `timestamp`)"
            )
            executor.exec(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `message_search_docs_fts`
                USING fts5(
                    `content`,
                    tokenize='trigram'
                )
                """.trimIndent()
            )
        }

        /** Room 走新 API(`SQLiteConnection`)时由 [RoomDatabase.Callback.onCreate] 调用 ——
         *  当前 builder 使用 `BundledSQLiteDriver`,只走这条。 */
        fun createMessageSearchTables(connection: SQLiteConnection) {
            createMessageSearchTables(SqliteConnectionExecutor(connection))
        }

        /** Room 走老 API(`SupportSQLiteDatabase`)时由 [RoomDatabase.Callback.onCreate] 调用 ——
         *  当前 builder 不走此路径,保留以防切回默认 driver。 */
        fun createMessageSearchTables(db: SupportSQLiteDatabase) {
            createMessageSearchTables(SupportDbExecutor(db))
        }

        internal fun rebuildMessageSearchIndex(executor: SqlExecutor) {
            executor.exec("DELETE FROM `message_search_docs_fts`")
            executor.exec("DELETE FROM `message_search_docs`")
            executor.exec(
                """
                INSERT INTO `message_search_docs`(
                    `message_id`,
                    `session_id`,
                    `role`,
                    `content`,
                    `has_image`,
                    `timestamp`
                )
                SELECT
                    `id`,
                    `session_id`,
                    `role`,
                    `content`,
                    CASE WHEN `imageBase64` IS NULL THEN 0 ELSE 1 END,
                    `timestamp`
                FROM `messages`
                """.trimIndent()
            )
            executor.exec(
                """
                INSERT INTO `message_search_docs_fts`(`rowid`, `content`)
                SELECT `search_id`, `content` FROM `message_search_docs`
                """.trimIndent()
            )
        }

        fun rebuildMessageSearchIndex(connection: SQLiteConnection) {
            rebuildMessageSearchIndex(SqliteConnectionExecutor(connection))
        }

        fun rebuildMessageSearchIndex(db: SupportSQLiteDatabase) {
            rebuildMessageSearchIndex(SupportDbExecutor(db))
        }
    }
}

