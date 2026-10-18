package com.xiaoqi.companion.data.record

import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** Open Food Facts 商品条目:采购行预填 + 展示用营养参考(每 100g,可空)。 */
data class OpenFoodFactsProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
)

/**
 * Open Food Facts v2 产品查询(https://world.openfoodfacts.org)。开放数据、无需 key。
 * 采购确认页"扫条码"路径的数据源:名称/品牌直接预填,营养仅作参考展示(中文商品覆盖率有限)。
 */
class OpenFoodFactsClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    // Dagger 不认 String 默认参数,注入走这个不含 baseUrl 的构造
    @Inject
    constructor(httpClient: OkHttpClient) : this(httpClient, DEFAULT_BASE_URL)

    suspend fun lookup(barcode: String): OpenFoodFactsProduct? = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() } || barcode.length !in 6..18) return@withContext null
        val request = Request.Builder()
            .url("$baseUrl/$barcode.json")
            .header("User-Agent", "Aura-Companion/0.1 (personal meal logging app)")
            .get()
            .build()
        val product = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.info(LogTags.Record, "off_lookup_http_error", "status" to response.code)
                    return@runCatching null
                }
                val body = response.body?.string().orEmpty()
                parseProduct(barcode, body)
            }
        }.onFailure { error ->
            AppLogger.warn(
                LogTags.Record,
                "off_lookup_failed",
                "error" to (error.message ?: error::class.simpleName.orEmpty()),
            )
        }.getOrNull()
        AppLogger.info(LogTags.Record, "off_lookup_completed", "found" to (product != null))
        product
    }

    private fun parseProduct(barcode: String, body: String): OpenFoodFactsProduct? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        // status: 0 = 未收录;1 = 命中
        if (root["status"]?.jsonPrimitive?.contentOrNull?.toInt() != 1) return null
        val product = root["product"] as? JsonObject ?: return null
        val name = product.str("product_name")?.takeIf { it.isNotBlank() } ?: return null
        val nutriments = product["nutriments"] as? JsonObject
        return OpenFoodFactsProduct(
            barcode = barcode,
            name = name,
            brand = product.str("brands")?.takeIf { it.isNotBlank() },
            kcalPer100g = nutriments?.dbl("energy-kcal_100g"),
            proteinPer100g = nutriments?.dbl("proteins_100g"),
            carbsPer100g = nutriments?.dbl("carbohydrates_100g"),
            fatPer100g = nutriments?.dbl("fat_100g"),
        )
    }

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    private fun JsonObject.dbl(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

    private companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
        val json = Json { ignoreUnknownKeys = true }
    }
}
