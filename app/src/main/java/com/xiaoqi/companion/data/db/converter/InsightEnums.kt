package com.xiaoqi.companion.data.db.converter

/**
 * Insight 触发器类别(plan §2.1 五个值)。
 * 字符串常量直接持久化到 DB,保持可读可迁移。
 */
object InsightTriggerType {
    const val DREAM_SUMMARY = "DREAM_SUMMARY"
    const val PATTERN_DETECT = "PATTERN_DETECT"
    const val MOOD_TREND = "MOOD_TREND"
    const val ANNIVERSARY = "ANNIVERSARY"
    const val EXTERNAL_WRITE_BACK = "EXTERNAL_WRITE_BACK"
}

/**
 * Insight 业务类别(plan §2.1 七类)。
 */
object InsightCategory {
    const val WORK = "工作"
    const val RELATIONSHIP = "关系"
    const val HEALTH = "健康"
    const val MOOD = "情绪"
    const val ANNIVERSARY_CATEGORY = "重要日期"
    const val HABIT = "习惯"
    const val EXTERNAL_WRITE_BACK_CATEGORY = "信息回写"
}

/**
 * Insight 状态机(plan §2.1 五态)。
 * UI 展示过滤:`status = VISIBLE` 才进主页卡片 / 聊天页侧边提示。
 */
object InsightStatus {
    const val DRAFT = "DRAFT"
    const val VISIBLE = "VISIBLE"
    const val DISMISSED = "DISMISSED"
    const val ARCHIVED = "ARCHIVED"
    const val MUTED_CATEGORY = "MUTED_CATEGORY"
}
