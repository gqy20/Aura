package com.xiaoqi.companion.data.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 一份 SQL 同时支持老 API `migrate(SupportSQLiteDatabase)` 和新 API `migrate(SQLiteConnection)`。
 *
 * 背景：Room 2.7+ 在 builder 配了非默认的 `SQLiteDriver`(例如 `BundledSQLiteDriver()`)之后,
 * 走新 API 路径,只调用 `migrate(SQLiteConnection)`;不实现该方法会直接抛 `NotImplementedError`。
 * 如果某天 builder 切回默认 driver,又会只调 `migrate(SupportSQLiteDatabase)`。
 * 写两个重载 SQL 双份,改起来容易漏一处。DualMigration 把"声明 SQL"和"调用 API"解耦——
 * 子类只重写 `apply(executor)`,executor 自己抹平老/新 API 差异。
 *
 * 用法:
 * ```
 * val M = object : DualMigration(1, 2) {
 *     override fun apply(executor: SqlExecutor) {
 *         executor.exec("CREATE TABLE ...")
 *     }
 * }
 * ```
 */
abstract class DualMigration(from: Int, to: Int) : Migration(from, to) {

    /** 子类只写一次 SQL,executor 自适配老/新 API。 */
    abstract fun apply(executor: SqlExecutor)

    /** 新 API — Room 在 builder 设了非默认 SQLiteDriver 时走这条。 */
    override fun migrate(connection: SQLiteConnection) {
        apply(SqliteConnectionExecutor(connection))
    }

    /** 老 API — Room 走默认 driver 时走这条(目前 builder 用 BundledSQLiteDriver,此路径实际不会触发,保留以防切回默认 driver)。 */
    override fun migrate(db: SupportSQLiteDatabase) {
        apply(SupportDbExecutor(db))
    }
}

/** 抹平老/新 API exec 调用的最小抽象。 */
interface SqlExecutor {
    fun exec(sql: String)
}

class SupportDbExecutor(private val db: SupportSQLiteDatabase) : SqlExecutor {
    override fun exec(sql: String) {
        db.execSQL(sql)
    }
}

class SqliteConnectionExecutor(private val connection: SQLiteConnection) : SqlExecutor {
    override fun exec(sql: String) {
        connection.prepare(sql).use { statement ->
            statement.step()
        }
    }
}
