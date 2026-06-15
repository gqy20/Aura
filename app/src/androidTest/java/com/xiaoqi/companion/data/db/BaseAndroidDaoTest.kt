package com.xiaoqi.companion.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Instrumented Room DAO 测试基类。
 *
 * **必须用 [androidx.sqlite.driver.bundled.BundledSQLiteDriver]** 替换默认 framework SQLite，
 * 否则 `message_search_docs_fts`（FTS5 虚拟表 + `tokenize='trigram'`）建不出来。
 * BundledSQLiteDriver 走 Android 系统自带的 libsqliteX.so（SQLite ≥ 3.24），原生气 FTS5 支持。
 *
 * schema 改写 [SupportSQLiteDatabase] 形态 → 必须再回调一次
 * [CompanionDatabase.createMessageSearchTables]，因为 Room `@Database` 注解只声明 `messages`
 * 等主表，FTS5 影子表是 `MIGRATION_9_10` / `createMessageSearchTables` 手工建的。
 *
 * @see MessageSearchDaoFts5Test 真 FTS5 行为验证
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseAndroidDaoTest {

    protected lateinit var db: CompanionDatabase
    protected lateinit var context: Context

    @Before
    open fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 复用生产暴露的建表工具,让 tests 跟 prod schema 完全一致
                    CompanionDatabase.createMessageSearchTables(db)
                }
            })
            .build()
        initDaos()
    }

    @After
    fun closeDb() {
        if (::db.isInitialized) db.close()
    }

    /** 子类声明 dao 字段并在此赋值。 */
    abstract fun initDaos()
}
