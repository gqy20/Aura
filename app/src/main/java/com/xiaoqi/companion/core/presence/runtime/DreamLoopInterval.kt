package com.xiaoqi.companion.core.presence.runtime

import androidx.annotation.StringRes
import com.xiaoqi.companion.R

/**
 * Dream Loop 后台觉察周期档位(7 档)。
 *
 * 单位:分钟。存储时落库为 Long minutes 整数。
 * WorkManager PeriodicWorkRequest 最小周期 15min(对应 [M15] 边界档),
 * 实际触发间隔仍受 Android Doze/AppStandby 约束,WorkManager 内部会按需延后。
 *
 * 选档语义:
 * - [OFF] — 完全停用 DreamLoopScheduler(`WorkManager.cancelUniqueWork`)
 * - [M15] / [M30] — 频繁档(适合观察期),UI 会显示耗电警告
 * - [H1] / [H3] — 日常档
 * - [H6] — 默认档,匹配 PoC 阶段硬编码行为,向后兼容
 * - [H12] — 节能档
 */
enum class DreamLoopInterval(
    val minutes: Long,
    val isEnabled: Boolean,
    @StringRes val labelRes: Int,
) {
    OFF(minutes = 0L, isEnabled = false, labelRes = R.string.dream_loop_off),
    M15(minutes = 15L, isEnabled = true, labelRes = R.string.dream_loop_15min),
    M30(minutes = 30L, isEnabled = true, labelRes = R.string.dream_loop_30min),
    H1(minutes = 60L, isEnabled = true, labelRes = R.string.dream_loop_1h),
    H3(minutes = 180L, isEnabled = true, labelRes = R.string.dream_loop_3h),
    H6(minutes = 360L, isEnabled = true, labelRes = R.string.dream_loop_6h),
    H12(minutes = 720L, isEnabled = true, labelRes = R.string.dream_loop_12h),
    ;

    companion object {
        /** 首次装 App、key 缺失时的默认值,匹配旧硬编码 6h。 */
        val DEFAULT: DreamLoopInterval = H6

        /**
         * 从落库的 minutes 反解档位。无法识别的值(例如历史脏数据)回退到 [DEFAULT]。
         * OFF 走 minutes == 0L 显式匹配,不会被反解为默认值。
         */
        fun fromMinutesOrDefault(minutes: Long): DreamLoopInterval =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
