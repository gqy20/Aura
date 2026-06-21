package com.xiaoqi.companion.feature.chat

import androidx.annotation.VisibleForTesting

/**
 * 首字到达前的安抚文案轮播。
 *
 * 用途：当 ASSISTANT 消息已插入、`isStreaming && content.isBlank()` 时，气泡里显示
 * 一行随时间升级语气的小字，告诉用户"还在想、没卡死"。首个 token 到达后由
 * MessageBubble 分支自然退出，文案不写入 [ChatMessage.content]、不持久化。
 *
 * 设计要点：
 * - **几何分桶**：阈值随时间放大（2 / 5 / 12 / 25 / 50s），匹配用户对数时间感知。
 *   1→2s 和 25→50s 体感差不多，所以档位间隔也近似翻倍。
 * - **不承诺时间**：全程没有"还有 X 秒"，本地首字延迟不可预测。
 * - **>12s 后每条都强调"还在/没走开"**：用户最怕的是"是不是卡死了"，
 *   承认在场比假装快更重要。
 */
object ThinkingHints {

    /**
     * 几何分桶阈值（毫秒，闭区间起界）。每档大致翻倍：2s / 5s / 12s / 25s / 50s。
     * [hintFor] 用 [elapsedMs] 落在哪个区间来选档。
     */
    @VisibleForTesting
    val stageThresholdsMs: LongArray = longArrayOf(
        2_000L,
        5_000L,
        12_000L,
        25_000L,
        50_000L,
    )

    /**
     * 每档文案池，下标对应档位：
     * 0 即将开口 / 1 在想 / 2 费劲中 / 3 比较慢 / 4 真的慢 / 5 异常兜底。
     *
     * 档0/1/2 每档 3 条保证高频段随机起手不重；档4 给 4 条是因为它在 25–50s 会被
     * 反复刷到，少了立刻露馅；档5 只有 2 条，提示"可能卡了"但不报错。
     */
    private val pools: List<List<String>> = listOf(
        listOf(
            "嗯，我在听…",
            "Aura 看到啦…",
            "让我想想…",
        ),
        listOf(
            "组织一下语言…",
            "嗯…怎么说呢…",
            "我捋一下思路…",
        ),
        listOf(
            "这块得多想想…",
            "有点拿不准，再琢磨下…",
            "在认真想呢，别急…",
        ),
        listOf(
            "有点慢，但我还在…",
            "让 Aura 再想想，我不会敷衍你…",
            "这个问题确实要琢磨…",
        ),
        listOf(
            "还在想，没走开…",
            "本地有点慢，但我一直在…",
            "快了快了，再等我一下…",
            "嗯…这个值得好好想…",
        ),
        listOf(
            "好像有点卡住了…",
            "再等等，或者重试也行…",
        ),
    )

    /**
     * 根据已等待时长返回当前档位的文案。
     *
     * [indexInStage] = 进入当前档位后已切的次数（从 0 起），用于在档内轮播不重复。
     * 超出池子长度时取模，保证长等待也能持续输出。
     */
    fun hintFor(elapsedMs: Long, indexInStage: Int = 0): String {
        val stage = stageIndexFor(elapsedMs)
        val pool = pools[stage]
        val safeIndex = if (pool.isEmpty()) 0 else ((indexInStage % pool.size) + pool.size) % pool.size
        return pool[safeIndex]
    }

    /** 返回当前等待时长对应的档位下标（0..5）。公开给 Carousel 用于检测换档。 */
    fun stageIndexFor(elapsedMs: Long): Int {
        var idx = 0
        for (i in stageThresholdsMs.indices) {
            if (elapsedMs >= stageThresholdsMs[i]) idx = i + 1
        }
        return idx
    }
}
