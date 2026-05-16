package com.xiaoqi.companion.core.weather

data class WeatherReport(
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val humidityPercent: Int,
    val precipitationMm: Double,
    val rainMm: Double,
    val windSpeedKmh: Double,
    val weatherCode: Int,
    val weatherLabel: String,
    val isDay: Boolean,
    val observedAt: String,
)

interface WeatherProvider {
    suspend fun getByCity(city: String): WeatherReport
    suspend fun getByCoordinates(latitude: Double, longitude: Double, locationName: String = ""): WeatherReport
}
