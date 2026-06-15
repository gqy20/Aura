package com.xiaoqi.companion.core.presence.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DreamLoopInterval] 边界与默认值测试。
 *
 * 不依赖 Android / DataStore,可纯 JUnit 跑。
 */
class DreamLoopIntervalTest {

    @Test
    fun defaults_isH6_toMatchLegacyHardcodedBehavior() {
        assertEquals(DreamLoopInterval.H6, DreamLoopInterval.DEFAULT)
    }

    @Test
    fun minutes_mapping_coversAllSevenSlots() {
        assertEquals(0L, DreamLoopInterval.OFF.minutes)
        assertEquals(15L, DreamLoopInterval.M15.minutes)
        assertEquals(30L, DreamLoopInterval.M30.minutes)
        assertEquals(60L, DreamLoopInterval.H1.minutes)
        assertEquals(180L, DreamLoopInterval.H3.minutes)
        assertEquals(360L, DreamLoopInterval.H6.minutes)
        assertEquals(720L, DreamLoopInterval.H12.minutes)
    }

    @Test
    fun isEnabled_onlyOffIsFalse() {
        assertFalse(DreamLoopInterval.OFF.isEnabled)
        listOf(
            DreamLoopInterval.M15,
            DreamLoopInterval.M30,
            DreamLoopInterval.H1,
            DreamLoopInterval.H3,
            DreamLoopInterval.H6,
            DreamLoopInterval.H12,
        ).forEach { assertTrue("${it.name} should be enabled", it.isEnabled) }
    }

    @Test
    fun fromMinutesOrDefault_knownValues() {
        assertEquals(DreamLoopInterval.OFF, DreamLoopInterval.fromMinutesOrDefault(0L))
        assertEquals(DreamLoopInterval.M15, DreamLoopInterval.fromMinutesOrDefault(15L))
        assertEquals(DreamLoopInterval.M30, DreamLoopInterval.fromMinutesOrDefault(30L))
        assertEquals(DreamLoopInterval.H1, DreamLoopInterval.fromMinutesOrDefault(60L))
        assertEquals(DreamLoopInterval.H6, DreamLoopInterval.fromMinutesOrDefault(360L))
    }

    @Test
    fun fromMinutesOrDefault_unknownValue_fallsBackToH6() {
        // 历史脏数据 / 手工改 DataStore 时:未知 minutes 不抛异常,回退 H6
        assertEquals(DreamLoopInterval.H6, DreamLoopInterval.fromMinutesOrDefault(999L))
        assertEquals(DreamLoopInterval.H6, DreamLoopInterval.fromMinutesOrDefault(-1L))
    }

    @Test
    fun fromMinutesOrDefault_zero_isOff_notDefault() {
        // 0L 必须显式走 OFF,不能因为"无法识别"被回退到 H6
        // 防止首次装 App 时 null → ?: 0 → OFF 的回归
        assertEquals(DreamLoopInterval.OFF, DreamLoopInterval.fromMinutesOrDefault(0L))
    }
}
