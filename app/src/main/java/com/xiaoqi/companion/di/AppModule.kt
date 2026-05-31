package com.xiaoqi.companion.di

import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.EmotionStateMachineImpl
import com.xiaoqi.companion.core.companion.ConversationReflection
import com.xiaoqi.companion.core.companion.KoogAgentFactory
import com.xiaoqi.companion.core.companion.KoogAgentFactoryImpl
import com.xiaoqi.companion.core.companion.LlmConversationReflection
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.companion.RelationshipModelImpl
import com.xiaoqi.companion.core.context.AndroidContextPermissionReader
import com.xiaoqi.companion.core.context.AndroidCurrentLocationProvider
import com.xiaoqi.companion.core.context.AndroidDeviceStatusProvider
import com.xiaoqi.companion.core.context.ContextPermissionReader
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.context.DeviceStatusProvider
import com.xiaoqi.companion.core.llm.DefaultKoogPromptExecutorFactory
import com.xiaoqi.companion.core.llm.KoogPromptExecutorFactory
import com.xiaoqi.companion.core.local.LocalQwenEngine
import com.xiaoqi.companion.core.local.MnnLocalQwenEngine
import com.xiaoqi.companion.core.mcp.McpHttpClient
import com.xiaoqi.companion.core.mcp.RemoteMcpClient
import com.xiaoqi.companion.core.reminder.AndroidReminderScheduler
import com.xiaoqi.companion.core.reminder.ReminderScheduler
import com.xiaoqi.companion.core.tools.AgentToolRegistry
import com.xiaoqi.companion.core.tools.CompanionToolRegistry
import com.xiaoqi.companion.core.weather.OpenMeteoWeatherProvider
import com.xiaoqi.companion.core.weather.WeatherProvider
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.ConfigRepositoryImpl
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.MessageRepositoryImpl
import com.xiaoqi.companion.data.repository.ReminderRepository
import com.xiaoqi.companion.data.repository.ReminderRepositoryImpl
import com.xiaoqi.companion.data.repository.ToolCallRepository
import com.xiaoqi.companion.data.repository.ToolCallRepositoryImpl
import com.xiaoqi.companion.feature.chat.AndroidChatImageProcessor
import com.xiaoqi.companion.feature.chat.ChatImageProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindEmotionMachine(machine: EmotionStateMachineImpl): EmotionStateMachine

    @Binds
    @Singleton
    abstract fun bindRelationshipModel(model: RelationshipModelImpl): RelationshipModel

    @Binds
    @Singleton
    abstract fun bindKoogAgentFactory(factory: KoogAgentFactoryImpl): KoogAgentFactory

    @Binds
    @Singleton
    abstract fun bindKoogPromptExecutorFactory(factory: DefaultKoogPromptExecutorFactory): KoogPromptExecutorFactory

    @Binds
    @Singleton
    abstract fun bindLocalQwenEngine(engine: MnnLocalQwenEngine): LocalQwenEngine

    @Binds
    @Singleton
    abstract fun bindConversationReflection(reflection: LlmConversationReflection): ConversationReflection

    @Binds
    @Singleton
    abstract fun bindAgentToolRegistry(registry: CompanionToolRegistry): AgentToolRegistry

    @Binds
    @Singleton
    abstract fun bindRemoteMcpClient(client: McpHttpClient): RemoteMcpClient

    @Binds
    @Singleton
    abstract fun bindConfigRepository(repository: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(repository: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindToolCallRepository(repository: ToolCallRepositoryImpl): ToolCallRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(repository: ReminderRepositoryImpl): ReminderRepository

    @Binds
    @Singleton
    abstract fun bindChatImageProcessor(processor: AndroidChatImageProcessor): ChatImageProcessor

    @Binds
    @Singleton
    abstract fun bindContextPermissionReader(reader: AndroidContextPermissionReader): ContextPermissionReader

    @Binds
    @Singleton
    abstract fun bindDeviceStatusProvider(provider: AndroidDeviceStatusProvider): DeviceStatusProvider

    @Binds
    @Singleton
    abstract fun bindCurrentLocationProvider(provider: AndroidCurrentLocationProvider): CurrentLocationProvider

    @Binds
    @Singleton
    abstract fun bindWeatherProvider(provider: OpenMeteoWeatherProvider): WeatherProvider

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(scheduler: AndroidReminderScheduler): ReminderScheduler
}
