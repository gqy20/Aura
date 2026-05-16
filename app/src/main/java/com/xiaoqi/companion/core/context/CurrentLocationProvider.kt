package com.xiaoqi.companion.core.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
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
        if (!hasLocationPermission) return null

        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        return locationManager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let {
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
