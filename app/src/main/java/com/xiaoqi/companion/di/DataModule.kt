package com.xiaoqi.companion.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.xiaoqi.companion.core.companion.OutputParser
import com.xiaoqi.companion.core.prompt.PromptBuilder
import com.xiaoqi.companion.data.db.CompanionDatabase
import com.xiaoqi.companion.data.db.dao.AgentStateDao
import com.xiaoqi.companion.data.db.dao.InsightDao
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.MoodSnapshotDao
import com.xiaoqi.companion.data.db.dao.ReminderDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    @Provides
    @Singleton
    fun provideOutputParser(): OutputParser = OutputParser()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("companion_settings") },
        )

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CompanionDatabase =
        Room.databaseBuilder(
            context,
            CompanionDatabase::class.java,
            "companion.db",
        )
            .addMigrations(
                CompanionDatabase.MIGRATION_1_2,
                CompanionDatabase.MIGRATION_2_3,
                CompanionDatabase.MIGRATION_3_4,
                CompanionDatabase.MIGRATION_4_5,
                CompanionDatabase.MIGRATION_5_6,
                CompanionDatabase.MIGRATION_6_7,
                CompanionDatabase.MIGRATION_7_8,
            )
            .build()

    @Provides
    fun provideMessageDao(database: CompanionDatabase): MessageDao =
        database.messageDao()

    @Provides
    fun provideToolCallDao(database: CompanionDatabase): ToolCallDao =
        database.toolCallDao()

    @Provides
    fun provideMemoryDao(database: CompanionDatabase): MemoryDao =
        database.memoryDao()

    @Provides
    fun provideMemorySummaryDao(database: CompanionDatabase): MemorySummaryDao =
        database.memorySummaryDao()

    @Provides
    fun provideMoodSnapshotDao(database: CompanionDatabase): MoodSnapshotDao =
        database.moodSnapshotDao()

    @Provides
    fun provideAgentStateDao(database: CompanionDatabase): AgentStateDao =
        database.agentStateDao()

    @Provides
    fun provideReminderDao(database: CompanionDatabase): ReminderDao =
        database.reminderDao()

    @Provides
    fun provideInsightDao(database: CompanionDatabase): InsightDao =
        database.insightDao()
}
