package com.xiaoqi.companion.core.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracyMeters: Float?,
    val timestamp: Long,
)

interface CurrentLocationProvider {
    fun getLastKnownLocation(): CurrentLocation?
}

class AndroidCurrentLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CurrentLocationProvider {

    override fun getLastKnownLocation(): CurrentLocation? {
        val hasLocationPermission =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) {
            AppLogger.info(LogTags.Tools, "location_read_skipped", "reason" to "permission_missing")
            return null
        }

        val locationManager = context.getSystemService(LocationManager::class.java) ?: run {
            AppLogger.warn(LogTags.Tools, "location_read_skipped", "reason" to "service_unavailable")
            return null
        }
        return locationManager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }
                    .onFailure { error ->
                        AppLogger.warn(
                            LogTags.Tools,
                            "location_provider_read_failed",
                            "provider" to provider,
                            "message" to (error.message ?: error::class.simpleName.orEmpty()),
                        )
                    }
                    .getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let {
                AppLogger.info(
                    LogTags.Tools,
                    "location_read_completed",
                    "provider" to (it.provider ?: "unknown"),
                    "hasAccuracy" to it.hasAccuracy(),
                    "ageMs" to (System.currentTimeMillis() - it.time),
                )
                CurrentLocation(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    provider = it.provider ?: "unknown",
                    accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                    timestamp = it.time,
                )
            }
    }
}
