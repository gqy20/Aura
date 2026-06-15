package com.xiaoqi.companion.core.weather

import javax.inject.Inject
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenMeteoWeatherProvider @Inject constructor(
    private val client: OkHttpClient,
) : WeatherProvider {

    override suspend fun getByCity(city: String): WeatherReport =
        withContext(Dispatchers.IO) {
            val trimmedCity = city.trim()
            require(trimmedCity.isNotBlank()) { "City is required when coordinates are not provided." }
            val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", trimmedCity)
                .addQueryParameter("count", "1")
                .addQueryParameter("language", "zh")
                .addQueryParameter("format", "json")
                .build()
            val root = getJson(url.toString())
            val result = root["results"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("No weather location found for $trimmedCity.")
            val latitude = result["latitude"]!!.jsonPrimitive.double
            val longitude = result["longitude"]!!.jsonPrimitive.double
            val locationName = listOfNotNull(
                result["name"]?.jsonPrimitive?.contentOrNull,
                result["admin1"]?.jsonPrimitive?.contentOrNull,
                result["country"]?.jsonPrimitive?.contentOrNull,
            ).distinct().joinToString(", ")

            getByCoordinates(latitude = latitude, longitude = longitude, locationName = locationName)
        }

    override suspend fun getByCoordinates(latitude: Double, longitude: Double, locationName: String): WeatherReport =
        withContext(Dispatchers.IO) {
            val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", latitude.toString())
                .addQueryParameter("longitude", longitude.toString())
                .addQueryParameter(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m,is_day",
                )
                .addQueryParameter("timezone", "auto")
                .build()
            val current = getJson(url.toString())["current"]!!.jsonObject

            WeatherReport(
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                temperatureCelsius = current["temperature_2m"]!!.jsonPrimitive.double,
                apparentTemperatureCelsius = current["apparent_temperature"]!!.jsonPrimitive.double,
                humidityPercent = current["relative_humidity_2m"]!!.jsonPrimitive.int,
                precipitationMm = current["precipitation"]!!.jsonPrimitive.double,
                rainMm = current["rain"]!!.jsonPrimitive.double,
                windSpeedKmh = current["wind_speed_10m"]!!.jsonPrimitive.double,
                weatherCode = current["weather_code"]!!.jsonPrimitive.int,
                weatherLabel = weatherLabel(current["weather_code"]!!.jsonPrimitive.int),
                isDay = current["is_day"]!!.jsonPrimitive.int == 1,
                observedAt = current["time"]!!.jsonPrimitive.contentOrNull.orEmpty(),
            )
        }

    private fun getJson(url: String): JsonObject {
        val startedAt = System.currentTimeMillis()
        val host = url.substringAfter("://", url).substringBefore("/")
        AppLogger.info(LogTags.Tools, "weather_request_started", "host" to host)
        try {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    AppLogger.warn(
                        LogTags.Tools,
                        "weather_request_http_error",
                        "host" to host,
                        "statusCode" to it.code,
                        "bodyLength" to body.length,
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    )
                    error("Weather request failed: HTTP ${it.code}")
                }
                return json.parseToJsonElement(body).jsonObject.also {
                    AppLogger.info(
                        LogTags.Tools,
                        "weather_request_completed",
                        "host" to host,
                        "bodyLength" to body.length,
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.error(
                LogTags.Tools,
                e,
                "weather_request_failed",
                "host" to host,
                "durationMs" to (System.currentTimeMillis() - startedAt),
            )
            throw e
        }
    }

    private fun weatherLabel(code: Int): String =
        when (code) {
            0 -> "晴"
            1 -> "基本晴朗"
            2 -> "多云"
            3 -> "阴"
            45 -> "雾"
            48 -> "凇雾"
            51 -> "小毛毛雨"
            53 -> "中毛毛雨"
            55 -> "大毛毛雨"
            56 -> "小冻雨"
            57 -> "大冻雨"
            61 -> "小雨"
            63 -> "中雨"
            65 -> "大雨"
            66 -> "小冻雨"
            67 -> "大冻雨"
            71 -> "小雪"
            73 -> "中雪"
            75 -> "大雪"
            77 -> "雪粒"
            80 -> "小阵雨"
            81 -> "中阵雨"
            82 -> "暴阵雨"
            85 -> "小阵雪"
            86 -> "大阵雪"
            95 -> "雷暴"
            96 -> "雷暴伴小冰雹"
            99 -> "雷暴伴大冰雹"
            else -> "未知天气"
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
