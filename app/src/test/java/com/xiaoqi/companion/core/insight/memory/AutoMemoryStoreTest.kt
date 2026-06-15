package com.xiaoqi.companion.core.insight.memory

import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AutoMemoryStoreTest {

    private val appPreferences: AppPreferences = mockk(relaxed = true)
    private val store = AutoMemoryStore(appPreferences)

    @Test
    fun saveOnboardingAnswers_writesAllThreeKeys() = runTest {
        store.saveOnboardingAnswers(
            userPatterns = listOf("挂心事 1", "作息: 晚睡"),
            recurringTopics = listOf("就叫我 A 吧", "妈妈生日", "朋友 1"),
            now = 1_700_000_000_000L,
        )

        coVerify { appPreferences.setUserPatternsJson("""["挂心事 1","作息: 晚睡"]""") }
        coVerify { appPreferences.setRecurringTopicsJson("""["就叫我 A 吧","妈妈生日","朋友 1"]""") }
        coVerify { appPreferences.setOnboardingCompletedAt("1700000000000") }
    }

    @Test
    fun saveOnboardingAnswers_emptyLists_writesEmptyArrays() = runTest {
        store.saveOnboardingAnswers(userPatterns = emptyList(), recurringTopics = emptyList())

        coVerify { appPreferences.setUserPatternsJson("[]") }
        coVerify { appPreferences.setRecurringTopicsJson("[]") }
    }

    @Test
    fun onboardingCompletedAt_parsesValidLong() = runTest {
        coEvery { appPreferences.onboardingCompletedAt } returns kotlinx.coroutines.flow.flowOf("1700000000000")

        val result = store.onboardingCompletedAt()

        assert(result == 1_700_000_000_000L)
    }

    @Test
    fun onboardingCompletedAt_returnsNullForBlank() = runTest {
        coEvery { appPreferences.onboardingCompletedAt } returns kotlinx.coroutines.flow.flowOf("")

        val result = store.onboardingCompletedAt()

        assert(result == null)
    }
}
