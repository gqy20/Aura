package com.xiaoqi.companion.di

import com.xiaoqi.companion.core.companion.CompanionRuntime
import com.xiaoqi.companion.core.companion.EmotionStateMachine
import com.xiaoqi.companion.core.companion.KoogAgentFactory
import com.xiaoqi.companion.core.companion.OutputParser
import com.xiaoqi.companion.core.companion.RelationshipModel
import com.xiaoqi.companion.core.prompt.PromptBuilder
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
    abstract fun bindOutputParser(parser: OutputParser): OutputParser

    @Binds
    @Singleton
    abstract fun bindPromptBuilder(builder: PromptBuilder): PromptBuilder

    @Binds
    @Singleton
    abstract fun bindEmotionMachine(machine: EmotionStateMachine): EmotionStateMachine

    @Binds
    @Singleton
    abstract fun bindRelationshipModel(model: RelationshipModel): RelationshipModel

    @Binds
    @Singleton
    abstract fun bindKoogAgentFactory(factory: KoogAgentFactory): KoogAgentFactory
}
