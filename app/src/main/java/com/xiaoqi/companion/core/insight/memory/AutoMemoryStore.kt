package com.xiaoqi.companion.core.insight.memory

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Onboarding 5 问的 DataStore 雏形(plan §5.2)。
 *
 * 为什么不直接用文件系统:DataStore 已经是 Hilt 单例,接 M3 真实解析时
 * 再考虑升级到 `auto_memory/{user_patterns,recurring_topics}.md` 文件。
 *
 * MVP 阶段只把每条 answer 当 String 存到 JSON 数组,不做 LLM 解析。
 */
@Singleton
class AutoMemoryStore @Inject constructor(
    private val appPreferences: AppPreferences,
) {

    suspend fun saveOnboardingAnswers(
        userPatterns: List<String>,
        recurringTopics: List<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        appPreferences.setUserPatternsJson(json.encodeToString(stringListSerializer, userPatterns))
        appPreferences.setRecurringTopicsJson(json.encodeToString(stringListSerializer, recurringTopics))
        appPreferences.setOnboardingCompletedAt(now.toString())
    }

    suspend fun userPatterns(): List<String> =
        decodeList(appPreferences.userPatternsJson.first())

    suspend fun recurringTopics(): List<String> =
        decodeList(appPreferences.recurringTopicsJson.first())

    suspend fun onboardingCompletedAt(): Long? =
        appPreferences.onboardingCompletedAt.first().toLongOrNull()

    private fun decodeList(raw: String): List<String> = runCatching {
        json.decodeFromString(stringListSerializer, raw)
    }.onFailure {
        AppLogger.warn(
            LogTags.Repo,
            "auto_memory_list_decode_failed",
            "rawLength" to raw.length,
            "error" to (it.message ?: it::class.simpleName.orEmpty()),
        )
    }.getOrDefault(emptyList())

    private val stringListSerializer = ListSerializer(String.serializer())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
}
