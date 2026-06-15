package com.xiaoqi.companion.core.insight

/**
 * LLM 生成的"原始" insight(待 [InsightValidator] 校验)。
 *
 * 三个 evidence 列表可独立为空;只要其中任一非空就视为"有依据"。
 * 校验失败 → null,成功 → 同一个 draft 流转给 Repository 写入 DB。
 *
 * 设计参考:`docs/plan/insight-driven-product.md` §2.2 "Insight 五个必须要素"。
 */
data class InsightDraft(
    /** 触发器类别:DREAM_SUMMARY / PATTERN_DETECT / MOOD_TREND / ANNIVERSARY / EXTERNAL_WRITE_BACK */
    val triggerType: String,
    /** 业务类别:工作 / 关系 / 健康 / 情绪 / 重要日期 / 习惯 / 信息回写 */
    val category: String,
    /** ≤ 30 字的一句话标题 */
    val headline: String,
    /** ≤ 200 字的详细说明(可空) */
    val bodyMarkdown: String,
    /** 时间窗口:近 7 天 / 近 30 天 / 近 90 天 */
    val relevanceWindow: String,
    /** 0.0~1.0 */
    val confidence: Float,
    val evidenceMessageIds: List<String> = emptyList(),
    val evidenceMemoryIds: List<String> = emptyList(),
    val evidenceMoodSnapshotIds: List<String> = emptyList(),
)
