package com.xiaoqi.companion.core.weather

import javax.inject.Inject
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
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        response.use {
            if (!it.isSuccessful) error("Weather request failed: HTTP ${it.code}")
            return json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject
        }
    }

    private fun weatherLabel(code: Int): String =
        when (code) {
            0 -> "clear"
            1, 2, 3 -> "partly_cloudy"
            45, 48 -> "fog"
            51, 53, 55, 56, 57 -> "drizzle"
            61, 63, 65, 66, 67 -> "rain"
            71, 73, 75, 77 -> "snow"
            80, 81, 82 -> "rain_showers"
            85, 86 -> "snow_showers"
            95, 96, 99 -> "thunderstorm"
            else -> "unknown"
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
