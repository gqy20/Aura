package com.xiaoqi.companion.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ChatDayLabelTest {
    private fun at(hourOfDay: Int, minute: Int, dayOffsetFromNow: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, dayOffsetFromNow)
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }

    @Test
    fun sameDay_showsToday() {
        val now = at(12, 0, 0)
        assertEquals("今天", formatChatDayLabel(at(0, 1, 0), now))
        assertEquals("今天", formatChatDayLabel(at(23, 59, 0), now))
    }

    @Test
    fun previousDay_showsYesterday_evenAcrossMidnightBoundary() {
        val now = at(12, 0, 0)
        assertEquals("昨天", formatChatDayLabel(at(23, 59, -1), now))
        assertEquals("昨天", formatChatDayLabel(at(0, 1, -1), now))
    }

    @Test
    fun olderDays_showsMonthDay() {
        val now = at(12, 0, 0)
        val label = formatChatDayLabel(at(9, 0, -15), now)
        assert(label != "今天" && label != "昨天") { "unexpected: $label" }
        assert(Regex("""^\d+月\d+日$""").matches(label)) { "unexpected: $label" }
    }

    @Test
    fun dayStart_truncatesToMidnight() {
        val timestamp = at(18, 45, 0)
        val truncated = Calendar.getInstance().apply { timeInMillis = timestamp.chatDayStart() }
        assertEquals(0, truncated.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, truncated.get(Calendar.MINUTE))
        assertEquals(0, truncated.get(Calendar.SECOND))
        assertEquals(0, truncated.get(Calendar.MILLISECOND))
    }
}
