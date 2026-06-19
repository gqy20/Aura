package com.xiaoqi.companion.core.insight

/**
 * Insight 生成 prompt 字面量。
 *
 * M3 才真正通过 LocalQwenExecutor 调用,这里先把字面量定下来,
 * 避免 prompt 与 entity 字段不匹配导致 M3 整合时返工。
 *
 * 设计参考:`docs/plan/insight-driven-product.md` §3.2 "关键 Prompt 草图"。
 */
object InsightPrompts {

    /** 模式识别 — 找 1-2 个新发现 */
    val patternDetect: String = """
        你是 Aura 的内心观察者。请基于以下"近 7 天用户数据",找出 1-2 个用户**可能没意识到**但**确实存在**的模式或变化。

        要求:
        - 只基于给的数据,不要编造
        - 优先情绪、习惯、关系、节奏类发现
        - 每条发现必须能引用至少 2 条具体数据

        输出格式(严格遵守):
        - 必须且只能输出一个 JSON 数组,不要加任何其他文字
        - 用双引号,不用单引号
        - 不要用 markdown 代码块标记
        - 示例:[{"headline":"标题","body":"描述","evidence_ids":["id1","id2"],"confidence":0.75}]
        - 如果没发现值得说的,输出:[]
    """.trimIndent()

    /** 重要日期识别 — 从记忆中找时间敏感事件 */
    val anniversaryScan: String = """
        你是 Aura 的记忆管家。从以下"用户长期记忆"中,找出未来 14 天内的:
        - 重要日期(生日、纪念日、deadline)
        - 用户之前说"想做但没做"的事
        - 之前提过的"下次..." "等 X 之后..." 触发条件

        输出 JSON:[{ "headline": "...", "body": "...", "evidence_ids": [...], "confidence": 0.0~1.0 }]
        没找到就输出空数组。
    """.trimIndent()

    /** 关联发现 — 跨时间窗找联系 */
    val connectionDetect: String = """
        你是 Aura 的联想引擎。以下是"近 90 天用户数据"。
        请找出 1 个**两条不相关数据之间的潜在联系**,这种联系应该是用户没注意到的、但讲出来会让人"哦原来如此"的那种。

        严格:
        - 必须是真实联系,不是强行凑
        - 必须能引用 2 条或以上数据
        - confidence 必须 < 0.5,宁可丢弃不要硬编
    """.trimIndent()
}
