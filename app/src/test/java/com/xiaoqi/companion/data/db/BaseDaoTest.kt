package com.xiaoqi.companion.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * JVM 单测基类，承载 `messages` 主表 DAO 行为测试。
 *
 * **不验证 FTS5 真行为**：Robolectric host 缺 `sqliteJni.dll`，无法加载 FTS5 native 扩展。
 * `MessageSearchDao`（含 `ON CONFLICT DO UPDATE` UPSERT + `bm25()` / `MATCH`）的真行为由 androidTest 验证。
 *
 * 这里 callback 不创建 search_docs/fts 表——MessageDao 拆分后只依赖 messages 表，
 * 测试基类也只承载 messages CRUD 验证。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
abstract class BaseDaoTest {

    private lateinit var _db: CompanionDatabase
    protected val db: CompanionDatabase get() = _db

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        _db = Room.inMemoryDatabaseBuilder(context, CompanionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        initDaos()
    }

    @After
    fun closeDb() = _db.close()

    protected abstract fun initDaos()
}
