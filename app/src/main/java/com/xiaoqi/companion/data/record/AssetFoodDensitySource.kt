package com.xiaoqi.companion.data.record

import android.content.Context
import com.xiaoqi.companion.core.record.FoodDensityEntry
import com.xiaoqi.companion.core.record.FoodDensitySource
import com.xiaoqi.companion.core.record.FoodDensityTable
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 assets/food_density.json 加载密度表(由 scripts/build_food_density.py 生成)。
 * 首次加载后进程内缓存;加载失败返回 null,提取器降级回模型估算,不阻塞记录。
 */
@Singleton
class AssetFoodDensitySource @Inject constructor(
    @ApplicationContext private val context: Context,
) : FoodDensitySource {

    private val mutex = Mutex()
    private var cached: FoodDensityTable? = null
    private var loaded = false

    override suspend fun load(): FoodDensityTable? = mutex.withLock {
        if (loaded) return@withLock cached
        cached = withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
                FoodDensityTable(json.decodeFromString<List<FoodDensityEntry>>(raw))
            }.onFailure { error ->
                AppLogger.warn(
                    LogTags.Record,
                    "food_density_load_failed",
                    "error" to (error.message ?: error::class.simpleName.orEmpty()),
                )
            }.getOrNull()
        }
        loaded = true
        cached
    }

    private companion object {
        const val ASSET = "food_density.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
