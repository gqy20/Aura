package com.xiaoqi.companion.core.tools

import com.xiaoqi.companion.core.context.CurrentLocation
import com.xiaoqi.companion.core.context.CurrentLocationProvider
import com.xiaoqi.companion.core.weather.WeatherProvider
import com.xiaoqi.companion.core.weather.WeatherReport
import com.xiaoqi.companion.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetWeatherToolTest {

    @Test
    fun execute_returnsWeatherByCity() = runTest {
        val tool = GetWeatherTool(
            appPreferences = preferences(weather = true, location = false),
            weatherProvider = weatherProvider(),
            locationProvider = locationProvider(null),
        )

        val result = tool.execute(GetWeatherTool.Args(city = "Shanghai"))

        assertTrue(result.contains(""""status":"ok""""))
        assertTrue(result.contains(""""locationName":"Shanghai""""))
        assertTrue(result.contains(""""temperatureCelsius":25.5"""))
        assertTrue(result.contains(""""weatherLabel":"晴""""))
    }

    @Test
    fun execute_usesLocationWhenNoCityAndLocationEnabled() = runTest {
        val tool = GetWeatherTool(
            appPreferences = preferences(weather = true, location = true),
            weatherProvider = weatherProvider(),
            locationProvider = locationProvider(
                CurrentLocation(
                    latitude = 31.2,
                    longitude = 121.5,
                    provider = "gps",
                    accuracyMeters = 20f,
                    timestamp = 1000L,
                )
            ),
        )

        val result = tool.execute(GetWeatherTool.Args())

        assertTrue(result.contains(""""locationName":"current_location""""))
    }

    @Test
    fun execute_returnsDisabledWhenWeatherOff() = runTest {
        val tool = GetWeatherTool(
            appPreferences = preferences(weather = false, location = true),
            weatherProvider = weatherProvider(),
            locationProvider = locationProvider(null),
        )

        val result = tool.execute(GetWeatherTool.Args(city = "Shanghai"))

        assertTrue(result.contains(""""status":"disabled""""))
        assertTrue(result.contains("weather_context_disabled"))
    }

    @Test
    fun descriptor_exposesKoogToolMetadata() {
        val tool = GetWeatherTool(preferences(weather = true, location = false), weatherProvider(), locationProvider(null))

        assertEquals("get_weather", tool.name)
        assertTrue(tool.descriptor.description.contains("weather", ignoreCase = true))
    }

    private fun preferences(weather: Boolean, location: Boolean): AppPreferences =
        mockk {
            every { weatherContextEnabled } returns flowOf(weather)
            every { locationContextEnabled } returns flowOf(location)
        }

    private fun weatherProvider(): WeatherProvider =
        object : WeatherProvider {
            override suspend fun getByCity(city: String): WeatherReport = report(locationName = city)
            override suspend fun getByCoordinates(latitude: Double, longitude: Double, locationName: String): WeatherReport =
                report(locationName = locationName)
        }

    private fun locationProvider(location: CurrentLocation?): CurrentLocationProvider =
        object : CurrentLocationProvider {
            override fun getLastKnownLocation() = location
        }

    private fun report(locationName: String) = WeatherReport(
        latitude = 31.2,
        longitude = 121.5,
        locationName = locationName,
        temperatureCelsius = 25.5,
        apparentTemperatureCelsius = 27.0,
        humidityPercent = 68,
        precipitationMm = 0.0,
        rainMm = 0.0,
        windSpeedKmh = 12.0,
        weatherCode = 0,
        weatherLabel = "晴",
        isDay = true,
        observedAt = "2026-05-16T12:00",
    )
}
