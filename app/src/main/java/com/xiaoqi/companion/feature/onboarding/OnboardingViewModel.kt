package com.xiaoqi.companion.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.insight.memory.AutoMemoryStore
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Onboarding 流程的 ViewModel。
 *
 * 隔离 AutoMemoryStore + MemoryRepository + AppPreferences,让 OnboardingScreen 通过
 * `hiltViewModel()` 拿,避免 MainActivity 把 Hilt 注入往外漏。
 *
 * **双写策略**:
 * 1. `MemoryRepository.saveOnboardingMemories` —— 主写入,5 问落入 LTM,后续被
 *    `selectPromptContext` / `search_memory` 消费,LLM 知道用户的名字、朋友、作息。
 * 2. `AutoMemoryStore.saveOnboardingAnswers` —— DataStore 缓存,保留原 `userPatternsJson`
 *    / `recurringTopicsJson` 字段,MainActivity 启动判断用 `onboardingCompletedAt`。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val autoMemoryStore: AutoMemoryStore,
    private val memoryRepository: MemoryRepository,
    appPreferences: AppPreferences,
) : ViewModel() {

    /** 是否已完成 onboarding(给 MainActivity 启动判断用) */
    val isOnboardingCompleted: StateFlow<Boolean> = appPreferences.onboardingCompletedAt
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    /**
     * 一次性把 5 问答案同时落 LTM + DataStore。
     *
     * @param concerns q1 挂心事(EPISODE)
     * @param upcomingDates q2 重要日期(EPISODE)
     * @param addressStyle q3 称呼 + 说话风格(FACT)
     * @param friends q4 高频联系人,每人一条 FACT
     * @param scheduleChoice q5 作息节奏(FACT)
     */
    fun saveAnswers(
        concerns: String,
        upcomingDates: String,
        addressStyle: String,
        friends: List<String>,
        scheduleChoice: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            // 主写入:5 问进 LTM,LLM 后续能 search_memory 召回。
            // 防御性 runCatching:MemoryRepository 内部已经 runCatching 单条失败,
            // 但整个方法被 Mock 替身或外部异常替换时不能阻断 DataStore 写入。
            runCatching {
                memoryRepository.saveOnboardingMemories(
                    concerns = concerns,
                    upcomingDates = upcomingDates,
                    addressStyle = addressStyle,
                    friends = friends,
                    scheduleChoice = scheduleChoice,
                )
            }
            // 缓存:DataStore 保留 userPatterns/recurringTopics JSON,只给 MainActivity
            // 启动判断用,LTM 已落地后,这段缓存可后续废弃
            autoMemoryStore.saveOnboardingAnswers(
                userPatterns = listOfNotNull(
                    concerns.takeIf { it.isNotBlank() },
                    scheduleChoice.takeIf { it.isNotBlank() },
                ),
                recurringTopics = buildList {
                    addressStyle.takeIf { it.isNotBlank() }?.let { add(it) }
                    upcomingDates.takeIf { it.isNotBlank() }?.let { add(it) }
                    friends.filter { it.isNotBlank() }.forEach { add(it) }
                },
            )
            onDone()
        }
    }
}
