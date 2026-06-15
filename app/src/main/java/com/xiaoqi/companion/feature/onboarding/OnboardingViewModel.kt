package com.xiaoqi.companion.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.insight.memory.AutoMemoryStore
import com.xiaoqi.companion.data.datastore.AppPreferences
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
 * 隔离 AutoMemoryStore + AppPreferences,让 OnboardingScreen 通过 `hiltViewModel()`
 * 拿,避免 MainActivity 把 Hilt 注入往外漏。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val autoMemoryStore: AutoMemoryStore,
    appPreferences: AppPreferences,
) : ViewModel() {

    /** 是否已完成 onboarding(给 MainActivity 启动判断用) */
    val isOnboardingCompleted: StateFlow<Boolean> = appPreferences.onboardingCompletedAt
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    fun saveAnswers(
        userPatterns: List<String>,
        recurringTopics: List<String>,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            autoMemoryStore.saveOnboardingAnswers(
                userPatterns = userPatterns,
                recurringTopics = recurringTopics,
            )
            onDone()
        }
    }
}
