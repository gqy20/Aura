package com.xiaoqi.companion.core.presence.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 简易电量判断(plan §10.1 风险缓解:`< 20% 不跑`)。
 *
 * 走 `BatteryManager.BATTERY_PROPERTY_CAPACITY` API 29+;旧版本走 sticky broadcast 兜底。
 */
internal object BatteryHelper {

    private const val LOW_BATTERY_THRESHOLD = 0.20f

    fun isLow(context: Context): Boolean {
        val level = readLevel(context)
        return level in 0f..LOW_BATTERY_THRESHOLD
    }

    private fun readLevel(context: Context): Float {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (capacity >= 0) {
                capacity / 100f
            } else {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level < 0 || scale <= 0) 1f else level.toFloat() / scale.toFloat()
            }
        } catch (e: Exception) {
            1f
        }
    }
}
