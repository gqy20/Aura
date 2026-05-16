package com.xiaoqi.companion.core.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class DeviceStatus(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val powerSaveMode: Boolean,
    val isOnline: Boolean,
    val networkType: String,
)

interface DeviceStatusProvider {
    fun getStatus(): DeviceStatus
}

class AndroidDeviceStatusProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceStatusProvider {

    override fun getStatus(): DeviceStatus {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val powerManager = context.getSystemService(PowerManager::class.java)
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        return DeviceStatus(
            batteryPercent = percent,
            isCharging = isCharging,
            powerSaveMode = powerManager?.isPowerSaveMode == true,
            isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            networkType = capabilities.toNetworkType(),
        )
    }

    private fun NetworkCapabilities?.toNetworkType(): String =
        when {
            this == null -> "none"
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
}
