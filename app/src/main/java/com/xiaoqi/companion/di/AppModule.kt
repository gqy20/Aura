package com.xiaoqi.companion.di

import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.EmotionStateMachineImpl
import com.xiaoqi.companion.core.companion.KoogAgentFactory
import com.xiaoqi.companion.core.companion.KoogAgentFactoryImpl
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.companion.RelationshipModelImpl
import com.xiaoqi.companion.data.repository.ConfigRepository
import com.xiaoqi.companion.data.repository.ConfigRepositoryImpl
import com.xiaoqi.companion.data.repository.MessageRepository
import com.xiaoqi.companion.data.repository.MessageRepositoryImpl
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
    abstract fun bindConfigRepository(repository: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(repository: MessageRepositoryImpl): MessageRepository
}
