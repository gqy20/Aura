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
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MessageDao
import com.xiaoqi.companion.data.db.dao.ToolCallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
            .addMigrations(CompanionDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideMessageDao(database: CompanionDatabase): MessageDao =
        database.messageDao()

    @Provides
    fun provideMemoryDao(database: CompanionDatabase): MemoryDao =
        database.memoryDao()

    @Provides
    fun provideToolCallDao(database: CompanionDatabase): ToolCallDao =
        database.toolCallDao()
}
