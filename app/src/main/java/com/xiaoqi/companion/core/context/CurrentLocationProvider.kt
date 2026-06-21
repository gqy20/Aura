package com.xiaoqi.companion.core.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

data class CurrentLocation(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracyMeters: Float?,
    val timestamp: Long,
)

interface CurrentLocationProvider {
    fun getLastKnownLocation(): CurrentLocation?

    /**
     * 先 last known（快，可能够用）；空则主动请求一次当前定位（API 30+ getCurrentLocation），
     * timeout 兜底。用于用户明确需要当前位置的场景（如查天气），弥补 last known 为空的情况。
     */
    suspend fun requestCurrentLocation(timeoutMs: Long = 10_000L): CurrentLocation?
}

class AndroidCurrentLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CurrentLocationProvider {

    override fun getLastKnownLocation(): CurrentLocation? {
        if (!hasLocationPermission()) {
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
            ?.let { location ->
                AppLogger.info(
                    LogTags.Tools,
                    "location_read_completed",
                    "provider" to (location.provider ?: "unknown"),
                    "hasAccuracy" to location.hasAccuracy(),
                    "ageMs" to (System.currentTimeMillis() - location.time),
                )
                location.toCurrentLocation()
            }
    }

    override suspend fun requestCurrentLocation(timeoutMs: Long): CurrentLocation? {
        // 1. 先用 last known（快，可能够用）
        getLastKnownLocation()?.let { return it }
        // 2. last known 空 → 主动请求一次（getCurrentLocation API 30+）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            AppLogger.info(LogTags.Tools, "location_request_skipped", "reason" to "api_below_30")
            return null
        }
        if (!hasLocationPermission()) return null
        val locationManager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = bestProviderForCurrentLocation(locationManager) ?: run {
            AppLogger.warn(LogTags.Tools, "location_request_skipped", "reason" to "no_provider")
            return null
        }

        val startedAt = System.currentTimeMillis()
        val location = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<CurrentLocation?> { cont ->
                locationManager.getCurrentLocation(provider, null, context.mainExecutor) { loc ->
                    AppLogger.info(
                        LogTags.Tools,
                        "location_current_request_result",
                        "provider" to provider,
                        "hasLocation" to (loc != null),
                        "durationMs" to (System.currentTimeMillis() - startedAt),
                    )
                    cont.resume(loc?.toCurrentLocation())
                }
            }
        }
        if (location == null) {
            AppLogger.warn(
                LogTags.Tools,
                "location_request_timeout",
                "provider" to provider,
                "timeoutMs" to timeoutMs,
            )
        }
        return location
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 选最优的主动定位 provider：FUSED(31+,融合) > NETWORK(室内快) > GPS(户外准)。 */
    private fun bestProviderForCurrentLocation(lm: LocationManager): String? {
        val all = lm.allProviders
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && all.contains(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            all.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            all.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }
}

private fun Location.toCurrentLocation(): CurrentLocation = CurrentLocation(
    latitude = latitude,
    longitude = longitude,
    provider = provider ?: "unknown",
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    timestamp = time,
)

// 标注缓存时效: getLastKnownLocation 可能是旧缓存, 让模型知道坐标可能不精准, 不过度自信
fun CurrentLocation.toPromptContext(nowMs: Long = System.currentTimeMillis()): String {
    val acc = accuracyMeters?.let { "约 ${it.toInt()} 米" } ?: "未知"
    return "纬度 $latitude, 经度 $longitude(精度 $acc, ${ageDescription(nowMs - timestamp)}的定位缓存)"
}

private fun ageDescription(ageMs: Long): String {
    val minutes = ageMs / 60_000
    return when {
        minutes >= 24 * 60 -> "${minutes / (24 * 60)} 天前"
        minutes >= 60 -> "${minutes / 60} 小时前"
        minutes >= 1 -> "$minutes 分钟前"
        else -> "刚刚"
    }
}
