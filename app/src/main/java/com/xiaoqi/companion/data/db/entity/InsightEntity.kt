package com.xiaoqi.companion.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * "第二大脑" 核心数据表。
 *
 * 每条记录代表 Aura 注意到的一件"关于用户的事"。
 * 来源(DREAM_SUMMARY / PATTERN_DETECT / MOOD_TREND / ANNIVERSARY / EXTERNAL_WRITE_BACK)
 * 决定它由哪条流水线生成;状态机驱动 UI 呈现。
 *
 * 设计参考:`docs/plan/insight-driven-product.md` §2.1。
 */
@Entity(
    tableName = "insights",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["category"]),
        Index(value = ["status"]),
    ],
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 何时生成(系统时间,毫秒) */
    val createdAt: Long,
    /** 生成触发器:DREAM_SUMMARY / PATTERN_DETECT / MOOD_TREND / ANNIVERSARY / EXTERNAL_WRITE_BACK */
    val triggerType: String,
    /** 类别:工作 / 关系 / 健康 / 情绪 / 重要日期 / 习惯 / 信息回写 */
    val category: String,
    /** ≤ 30 字的一句话标题 */
    val headline: String,
    /** ≤ 200 字的详细说明(可空) */
    val bodyMarkdown: String,
    /** JSON 字符串:`{messageIds, moodSnapshotIds, memoryIds, reasoning}` */
    val evidence: String,
    /** 0.0~1.0;< 0.6 默认不展示 */
    val confidence: Float,
    /** 时间窗口:近 7 天 / 近 30 天 / 近 90 天 */
    val relevanceWindow: String,
    /** 状态:DRAFT / VISIBLE / DISMISSED / ARCHIVED / MUTED_CATEGORY */
    val status: String,
    /** 类别静音到期时间(可空) */
    val mutedUntil: Long? = null,
    /** 推送时间(可空) */
    val deliveredAt: Long? = null,
    /** 用户点击进入对话时间(可空) */
    val userClickedAt: Long? = null,
    /** 用户反馈:👍 / 👎 / 文字(可空) */
    val userFeedback: String? = null,
)
