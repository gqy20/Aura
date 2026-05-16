package com.xiaoqi.companion.core.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.weather.WeatherProvider
import com.xiaoqi.companion.data.datastore.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetWeatherTool @Inject constructor(
    private val appPreferences: AppPreferences,
    private val weatherProvider: WeatherProvider,
    private val locationProvider: CurrentLocationProvider,
) : SimpleTool<GetWeatherTool.Args>(
    typeToken<Args>(),
    name = "get_weather",
    description = "Get current weather by city, explicit coordinates, or the device's last known location when allowed.",
) {

    @Serializable
    data class Args(
        @param:LLMDescription("City name to look up. Prefer this when the user names a city.")
        val city: String = "",
        @param:LLMDescription("Optional latitude. Use together with longitude when available.")
        val latitude: Double? = null,
        @param:LLMDescription("Optional longitude. Use together with latitude when available.")
        val longitude: Double? = null,
    )

    override suspend fun execute(args: Args): String =
        withContext(Dispatchers.IO) {
            if (!appPreferences.weatherContextEnabled.first()) {
                return@withContext disabled("weather_context_disabled")
            }

            val report = when {
                args.city.isNotBlank() -> weatherProvider.getByCity(args.city)
                args.latitude != null && args.longitude != null -> weatherProvider.getByCoordinates(args.latitude, args.longitude)
                appPreferences.locationContextEnabled.first() -> {
                    val location = locationProvider.getLastKnownLocation()
                        ?: return@withContext disabled("last_known_location_unavailable")
                    weatherProvider.getByCoordinates(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        locationName = "current_location",
                    )
                }
                else -> return@withContext disabled("location_context_disabled_and_no_city")
            }

            buildJsonObject {
                put("status", "ok")
                put("locationName", report.locationName)
                put("latitude", report.latitude)
                put("longitude", report.longitude)
                put("temperatureCelsius", report.temperatureCelsius)
                put("apparentTemperatureCelsius", report.apparentTemperatureCelsius)
                put("humidityPercent", report.humidityPercent)
                put("precipitationMm", report.precipitationMm)
                put("rainMm", report.rainMm)
                put("windSpeedKmh", report.windSpeedKmh)
                put("weatherCode", report.weatherCode)
                put("weatherLabel", report.weatherLabel)
                put("isDay", report.isDay)
                put("observedAt", report.observedAt)
            }.toString()
        }

    private fun disabled(reason: String): String =
        buildJsonObject {
            put("status", "disabled")
            put("reason", reason)
        }.toString()
}
