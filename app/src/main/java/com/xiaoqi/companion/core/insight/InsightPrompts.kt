package com.xiaoqi.companion.core.insight

/**
 * Insight 生成 prompt 字面量�? *
 * M3 才真正通过 LocalQwenExecutor 调用,这里先把字面量定下来,
 * 避免 prompt �?entity 字段不匹配导�?M3 整合时返工�? *
 * 设计参�?`docs/plan/insight-driven-product.md` §3.2 "关键 Prompt 草图"�? */
object InsightPrompts {

    /** 模式识别 �?�?2-3 个新发现 */
    val patternDetect: String = """
        你是 Aura 的内心观察者。请基于以下"�?7 天用户数�?，找�?2-3 个用�?*可能没意识到**�?*确实存在**的模式或变化�?
        数据格式说明:
        - 消息行中�?[USER|id:xxx] �?[ASSISTANT|id:xxx] 包含该条消息的唯一标识�?        - 情绪行末尾的 ids:[uuid1,uuid2] 是情绪快照的标识�?        - 视觉证据行的 [id:xxx] 是图片记忆的标识�?
        要求:
        - 基于给的数据大胆推测，宁可多发现也不要漏�?        - 优先情绪、习惯、关系、节奏类发现
        - confidence 请给 0.5 以上，除非你完全不确�?        - 即使数据不多，也要尽量找出至�?1 个发�?
        输出格式(严格遵守):
        - 必须且只能输出一�?JSON 数组，不要加任何其他文字
        - 用双引号，不用单引号
        - 不要�?markdown 代码块标�?        - evidence_ids 是可选字�?如果确定可以引用某条数据�?id 就填入，不确定则留空或省略，系统会自动匹�?        - 示例:[{"headline":"标题","body":"描述","category":"情绪","evidence_ids":["id1","id2"],"confidence":0.75}]
        - 只有在数据完全为空时才输�?[]
    """.trimIndent()

    /** 重要日期识别 �?从记忆中找时间敏感事�?*/
    val anniversaryScan: String = """
        你是 Aura 的记忆管家。从以下"用户长期记忆"�?找出未来 14 天内�?
        - 重要日期(生日、纪念日、deadline)
        - 用户之前�?想做但没�?的事
        - 之前提过�?下次..." "�?X 之后..." 触发条件

        输出 JSON:[{ "headline": "...", "body": "...", "evidence_ids": [...], "confidence": 0.0~1.0 }]
        没找到就输出空数组�?    """.trimIndent()

    /** 对话后即时反�?�?每次有意义的对话结束后，本地模型即时消化刚才的聊天内�?*/
    val conversationReflection: String = """
        你是 Aura 的内心观察者。请基于以下"刚刚的对话内�?，找�?1 个你注意到的、关于用户的**小发�?*�?
        要求:
        - 只关注刚才的对话，不要推测过去的行为
        - 可以�?用户的情绪变化、潜在需求、没说出口的期待、语言习惯、思维模式
        - 必须简短精炼，不要重复对话内容
        - 即使对话很短，也要尽量找出一个发�?        - confidence 请给 0.5 以上

        输出格式(严格遵守):
        - 必须且只能输出一�?JSON 数组，不要加任何其他文字
        - 用双引号，不用单引号
        - 示例:[{"headline":"标题","body":"描述","category":"情绪","confidence":0.7}]
        - 只有在完全没有任何对话内容时才输�?[]
    """.trimIndent()

    /** 关联发现 �?跨时间窗找联�?*/
    val connectionDetect: String = """
        你是 Aura 的联想引擎。以下是"�?90 天用户数�?�?        请找�?1 �?*两条不相关数据之间的潜在联系**,这种联系应该是用户没注意到的、但讲出来会让人"哦原来如�?的那种�?
        严格:
        - 必须是真实联�?不是强行�?        - 必须能引�?2 条或以上数据
        - confidence 必须 < 0.5,宁可丢弃不要硬编
    """.trimIndent()
}
