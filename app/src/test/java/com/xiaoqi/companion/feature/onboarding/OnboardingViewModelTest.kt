package com.xiaoqi.companion.feature.onboarding

import com.xiaoqi.companion.core.insight.memory.AutoMemoryStore
import com.xiaoqi.companion.data.datastore.AppPreferences
import com.xiaoqi.companion.data.repository.MemoryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 验证 OnboardingViewModel.saveAnswers:
 * 1. 调 MemoryRepository.saveOnboardingMemories(主写入,LLM 后续能搜到)
 * 2. 调 AutoMemoryStore.saveOnboardingAnswers(DataStore 缓存,MainActivity 启动判断用)
 * 3. 调 onDone 回调通知 UI 跳转
 *
 * 不覆盖 isOnboardingCompleted(那是 StateFlow 派生的简单流,不值单测)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val memoryRepository: MemoryRepository = mockk(relaxed = true)
    private val autoMemoryStore: AutoMemoryStore = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true) {
        coEvery { onboardingCompletedAt } returns kotlinx.coroutines.flow.flowOf("")
    }
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { memoryRepository.saveOnboardingMemories(any(), any(), any(), any(), any()) } returns 5
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveAnswers_passesAllFiveAnswersToMemoryRepository() = runTest {
        val viewModel = OnboardingViewModel(autoMemoryStore, memoryRepository, appPreferences)

        viewModel.saveAnswers(
            concerns = "换工作面试",
            upcomingDates = "6/22 妈妈生日",
            addressStyle = "叫我小蓝",
            friends = listOf("旺财", "小米"),
            scheduleChoice = "晚睡晚起",
            onDone = {},
        )

        coVerify {
            memoryRepository.saveOnboardingMemories(
                concerns = "换工作面试",
                upcomingDates = "6/22 妈妈生日",
                addressStyle = "叫我小蓝",
                friends = listOf("旺财", "小米"),
                scheduleChoice = "晚睡晚起",
            )
        }
    }

    @Test
    fun saveAnswers_writesBackToAutoMemoryStore() = runTest {
        val viewModel = OnboardingViewModel(autoMemoryStore, memoryRepository, appPreferences)

        viewModel.saveAnswers(
            concerns = "换工作面试",
            upcomingDates = "6/22 妈妈生日",
            addressStyle = "叫我小蓝",
            friends = listOf("旺财", "小米"),
            scheduleChoice = "晚睡晚起",
            onDone = {},
        )

        coVerify {
            autoMemoryStore.saveOnboardingAnswers(
                userPatterns = match { it.contains("换工作面试") && it.contains("晚睡晚起") },
                recurringTopics = match {
                    it.contains("叫我小蓝") && it.contains("6/22 妈妈生日") &&
                        it.contains("旺财") && it.contains("小米")
                },
                now = any(),
            )
        }
    }

    @Test
    fun saveAnswers_invokesOnDoneAfterPersistence() = runTest {
        val viewModel = OnboardingViewModel(autoMemoryStore, memoryRepository, appPreferences)
        var done = false

        viewModel.saveAnswers(
            concerns = "",
            upcomingDates = "",
            addressStyle = "叫我小蓝",
            friends = listOf("旺财"),
            scheduleChoice = "",
            onDone = { done = true },
        )

        // runTest + UnconfinedTestDispatcher:viewModelScope.launch 同步跑完
        assert(done)
    }

    @Test
    fun saveAnswers_memoryFailure_doesNotBlockAutoMemoryStore() = runTest {
        coEvery { memoryRepository.saveOnboardingMemories(any(), any(), any(), any(), any()) } throws
            RuntimeException("db locked")
        val viewModel = OnboardingViewModel(autoMemoryStore, memoryRepository, appPreferences)

        // 不应崩 — memory 失败被 saveOnboardingMemories 内部 runCatching 吞了,
        // 但本测试验证 VM 视角不抛到外层
        viewModel.saveAnswers(
            concerns = "",
            upcomingDates = "",
            addressStyle = "叫我小蓝",
            friends = listOf("旺财"),
            scheduleChoice = "",
            onDone = {},
        )

        // 双写顺序:memory 先,DataStore 后(主写入失败不能阻断缓存)
        coVerifyOrder {
            memoryRepository.saveOnboardingMemories(any(), any(), any(), any(), any())
            autoMemoryStore.saveOnboardingAnswers(any(), any(), now = any())
        }
    }
}
