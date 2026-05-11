package com.xiaoqi.companion.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
