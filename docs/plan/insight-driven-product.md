# Aura Insight 驱动产品方案

> Last updated: 2026-06-15
>
> Scope: Aura 核心叙事"第二大脑 / 数字孪生"的产品化落地方案 —— 让"长期认识用户"从架构目标变成可感知的日常体验。
>
> 关联文档：[`dual-mind-architecture.md`](./dual-mind-architecture.md) · [`roadmap.md`](../roadmap.md) · [`architecture.md`](../architecture.md) · [`agent-capability-server-plan.md`](./agent-capability-server-plan.md)

---

## 0. 一句话定位

> **Insight 是 Aura 给用户的"关于他自己的纸条"。**

Aura 不是"被动回应的聊天机器人"，而是**长期观察、积累、提炼、提醒**用户关于自己生活的 AI。Insight 是这个过程的可感知产物 —— 它不是一个通知、不是一句问候、不是一句总结，而是**"Aura 看见了一件关于你的事，现在告诉你"**。

Insight 的目标不是让用户惊艳于"AI 真懂我"，而是让用户在第 30 天、第 90 天、第 365 天回头看时，意识到"这个 App 真的比任何人都更了解我最近的状态"。

---

## 1. 核心原则

### 1.1 Insight 必须可被遗忘

每个 insight 都要明确包含 **confidence**（信心度）和 **discardable**（可丢弃）两个元数据。用户应能：

- 看到 insight 时一键删除
- 把同类 insight 一键静音（"不要再说工作压力了"）
- 查看为什么 Aura 觉得这件事重要（"基于过去 14 天里你提到这个 7 次"）

**Insight 不该是"AI 在炫技"，而该是"我替你记着的某件事，现在告诉你"**。如果它打扰到了你，丢弃成本必须接近 0。

### 1.2 Insight 必须基于本机数据

> **这是 Aura 唯一不可替代的护城河。**

所有 insight 必须**只基于本机持久化数据**（messages、memories、mood_snapshots、presence_marks），不允许云端 LLM 主动生成。用户对话可以上云（对话体职责），但 **insight 永远是本机 LLM 看见的归纳**。

理由：

- 隐私：用户对话可能包含不能给第三方的内容；insight 只看已经持久化的 mongodb-like 字段
- 成本：insight 数量大、频率高，**不能靠云端账单支撑**
- 时延：insight 在锁屏 / 充电 / 闲时跑，**必须能离线工作**
- 信任：insight 是"Aura 长期认识我"的证据，**用户必须能打开 insight 的依据**（一条 message ID、一个 mood snapshot）

### 1.3 Insight 必须克制

> **一周不超过 3 条 insight 是默认上限。**

宁可少、不可滥。每条 insight 都要回答：

- 它对用户有用吗？还是只是 Aura 在显示自己"很智能"？
- 它现在说合适吗？还是攒到周报里更合适？
- 它能产生具体行动吗？还是只是一句"我知道你很忙"？

如果答不上来，**就丢弃**。Insight 的目标不是刷屏，而是建立"周回顾时我确实想看看 Aura 注意到什么"的期待。

### 1.4 Insight 不该取代对话

> **Insight 是对话的"召唤物"，不是对话本身。**

每条 insight 都应该以"要不要聊聊这个？"作为可点击的入口，但**默认不展开**。用户点进去才进入对话上下文（prefill 一段 prompt），然后由对话体接管。

这避免了两个反模式：

- 反模式 1：insight 越长越像"自动周报" → 用户失去打开欲
- 反模式 2：insight 触发后 Aura 自己接着说 → 把观察变成主动说教

---

## 2. Insight 的结构

### 2.1 数据模型

```kotlin
// data/db/entity/InsightEntity.kt

@Entity(
    tableName = "insights",
    foreignKeys = [],
    indices = [Index("createdAt"), Index("category"), Index("status")]
)
data class InsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,                    // 何时生成
    val triggerType: String,                // DREAM_SUMMARY / PATTERN_DETECT / MOOD_TREND / ANNIVERSARY / EXTERNAL_WRITE_BACK
    val category: String,                   // 工作 / 关系 / 健康 / 情绪 / 重要日期 / 习惯 / 信息回写
    val headline: String,                   // ≤ 30 字的一句话标题
    val bodyMarkdown: String,               // ≤ 200 字的详细说明（可空）
    val evidence: String,                   // JSON: { messageIds, moodSnapshotIds, memoryIds, reasoning }
    val confidence: Float,                  // 0.0 ~ 1.0，< 0.6 默认不展示
    val relevanceWindow: String,            // "近 7 天" / "近 30 天" / "近 90 天"
    val status: String,                     // DRAFT / VISIBLE / DISMISSED / ARCHIVED / MUTED_CATEGORY
    val mutedUntil: Long? = null,           // 静音至（可空）
    val deliveredAt: Long? = null,          // 推送时间
    val userClickedAt: Long? = null,        // 用户点击进入对话
    val userFeedback: String? = null,       // 👍 / 👎 / 文字
)
```

### 2.2 Insight 的五个必须要素

每条 insight 在生成时**必须包含**：

1. **Headline（标题）** —— 一句话讲清"Aura 看见了一件什么事"
2. **Evidence（依据）** —— 明确指出基于哪条 message / mood snapshot / memory；**没有依据的 insight 直接丢弃**
3. **Confidence（信心度）** —— 0~1；< 0.6 不展示
4. **Time Window（时间窗口）** —— 明确"这是关于近 X 天的观察"；避免把单次事件说成"长期规律"
5. **Action Prompt（行动召唤）** —— 至少 1 个"可以做什么"的具体动作（聊、查、设提醒、看详情）

### 2.3 三类 Insight

| 类型 | 触发 | 频率上限 | 示例 |
|---|---|---|---|
| **Pattern**（模式识别） | Dream Loop 跑出稳定规律 | 每周 ≤ 1 条 | "你过去 3 周都周日下午 3-5 点情绪偏低" |
| **Reminder**（重要日期/回访） | 记忆里有时间敏感事件 | 每周 ≤ 1 条 | "你之前提过你妈下个月生日，要不要我帮你查餐厅？" |
| **Connection**（关联发现） | Dream Loop 发现两条不相关 memory 有联系 | 每月 ≤ 1 条 | "你之前收藏的《暗黑 4》和昨天你说想玩游戏，是同一件事吗？" |

**默认节奏**：每周 1-2 条 Pattern + 每月 1-2 条 Reminder + 每月 0-1 条 Connection。

---

## 3. Insight 的生成流水线

### 3.1 总览

```
                 ┌─────────────────────────────────────────────┐
                 │              触发器 (Trigger)                │
                 │  - DreamLoopWorker（每天 1-2 次）           │
                 │  - AnniversaryScannerWorker（每天）         │
                 │  - ExternalWriteBackWorker（云端 push 时）  │
                 └─────────────────┬───────────────────────────┘
                                   │
                                   ▼
                 ┌─────────────────────────────────────────────┐
                 │        Stage 1: 数据收集（纯 SQL）          │
                 │  - 近 7/30/90 天的 messages/mood/memories   │
                 │  - 上次 dream run 后的新数据                │
                 │  - 用户静音的 category 直接跳过             │
                 └─────────────────┬───────────────────────────┘
                                   │
                                   ▼
                 ┌─────────────────────────────────────────────┐
                 │   Stage 2: 本地 LLM 归纳（LocalQwenExecutor）│
                 │  - 模式识别 prompt（"找 1-2 个新发现"）    │
                 │  - maxTokens=300，温度 0.3                 │
                 │  - 输入仅限 Stage 1 收集的数据              │
                 └─────────────────┬───────────────────────────┘
                                   │
                                   ▼
                 ┌─────────────────────────────────────────────┐
                 │    Stage 3: Insight 提取（结构化解析）      │
                 │  - 从 LLM 输出解析 InsightDraft             │
                 │  - 校验：必须有 evidence，否则丢弃           │
                 │  - 校验：confidence < 0.6 丢弃              │
                 │  - 校验：与已展示的 insight 去重（相似度）   │
                 └─────────────────┬───────────────────────────┘
                                   │
                                   ▼
                 ┌─────────────────────────────────────────────┐
                 │   Stage 4: 限流 / 调度（InsightScheduler）   │
                 │  - 同 category 周上限校验                   │
                 │  - 选合适时间（不在深夜、周一早上不推）     │
                 │  - 写入 insights 表 status=VISIBLE          │
                 └─────────────────┬───────────────────────────┘
                                   │
                                   ▼
                 ┌─────────────────────────────────────────────┐
                 │   Stage 5: 呈现（InsightCard UI）            │
                 │  - 主页 / 聊天页 / 通知三路呈现             │
                 │  - 用户 dismiss 后 status=DISMISSED         │
                 │  - 用户反馈后写回 userFeedback              │
                 └─────────────────────────────────────────────┘
```

### 3.2 关键 Prompt 草图

```kotlin
// core/insight/InsightPrompts.kt

object InsightPrompts {

    /** 模式识别 — 找 1-2 个新发现 */
    val patternDetect = """
        你是 Aura 的内心观察者。请基于以下"近 7 天用户数据"，找出 1-2 个用户**可能没意识到**但**确实存在**的模式或变化。

        要求：
        - 只基于给的数据，不要编造
        - 优先情绪、习惯、关系、节奏类发现
        - 每条发现必须能引用至少 2 条具体数据
        - 用 JSON 输出：[{ "headline": "...", "body": "...", "evidence_ids": [...], "confidence": 0.0~1.0 }]
        - 如果没发现值得说的，输出空数组
    """.trimIndent()

    /** 重要日期识别 — 从记忆中找时间敏感事件 */
    val anniversaryScan = """
        你是 Aura 的记忆管家。从以下"用户长期记忆"中，找出未来 14 天内的：
        - 重要日期（生日、纪念日、deadline）
        - 用户之前说"想做但没做"的事
        - 之前提过的"下次..." "等 X 之后..." 触发条件

        输出 JSON：[{ "headline": "...", "body": "...", "evidence_ids": [...], "confidence": 0.0~1.0 }]
        没找到就输出空数组。
    """.trimIndent()

    /** 关联发现 — 跨时间窗找联系 */
    val connectionDetect = """
        你是 Aura 的联想引擎。以下是"近 90 天用户数据"。
        请找出 1 个**两条不相关数据之间的潜在联系**，这种联系应该是用户没注意到的、但讲出来会让人"哦原来如此"的那种。

        严格：
        - 必须是真实联系，不是强行凑
        - 必须能引用 2 条或以上数据
        - confidence 必须 < 0.5，宁可丢弃不要硬编
    """.trimIndent()
}
```

### 3.3 关键校验

```kotlin
// core/insight/InsightValidator.kt

class InsightValidator @Inject constructor(
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val moodSnapshotDao: MoodSnapshotDao,
) {
    /**
     * 验证 evidence 真实存在。
     * 防止 LLM 幻觉：headline 说"你提到 5 次工作压力"，evidence_ids 必须能查到对应数据。
     */
    suspend fun validate(draft: InsightDraft): InsightDraft? {
        if (draft.evidenceIds.isEmpty()) return null  // 缺依据直接丢

        // 至少 50% 的 evidence_ids 必须真实存在
        val realCount = draft.evidenceIds.count { id ->
            messageDao.existsById(id) ||
            memoryDao.existsById(id) ||
            moodSnapshotDao.existsById(id)
        }
        if (realCount.toDouble() / draft.evidenceIds.size < 0.5) return null

        if (draft.confidence < 0.6f) return null

        // 与已展示的 insight 做相似度去重（heading + 简单向量）
        // ...

        return draft
    }
}
```

---

## 4. Insight 的呈现

### 4.1 三种呈现渠道

| 渠道 | 触发时机 | 适合类型 | 频率 |
|---|---|---|---|
| **主页 Insight 卡片** | 用户打开 App 时按需展示 1-2 条 | Pattern、Connection | 每天 ≤ 1 次打开时展示 |
| **聊天页侧边提示** | 长期未活跃（>3 天）后回来 | Reminder | 回访时 1 条 |
| **通知（Pulse）** | Dream Loop 跑出高价值 insight | 全部 | 每周 ≤ 1 条 |

**默认不主动推通知**。所有 insight 默认进入"主页卡片 + 聊天页可见"队列，**只有 confidence > 0.8 且是 Reminder 类型**才推通知。

### 4.2 Insight 卡片设计原则

```kotlin
// feature/insight/InsightCard.kt

@Composable
fun InsightCard(insight: InsightEntity, onClick: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = InsightSurface),
    ) {
        Column(Modifier.padding(16.dp)) {
            // 类别图标 + 时间窗口
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(getCategoryIcon(insight.category), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${getCategoryLabel(insight.category)} · ${insight.relevanceWindow}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(8.dp))

            // 标题（核心）
            Text(insight.headline, style = MaterialTheme.typography.titleMedium)

            // 详情（可展开）
            if (insight.bodyMarkdown.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(insight.bodyMarkdown, style = MaterialTheme.typography.bodyMedium)
            }

            // 依据入口 + 行动召唤
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClick) {
                    Text("和 Aura 聊聊")
                    Icon(Icons.Default.ChevronRight, null)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "知道了")
                }
            }
        }
    }
}
```

### 4.3 关键交互

- **"和 Aura 聊聊"按钮**：点击后 prefill 一段 prompt 进聊天页，**不是直接展开 insight**
  - 例：用户点 Pattern insight"你最近 3 周周日下午都情绪偏低" → 预填"我们聊聊你最近周日下午的状态？"
  - 由对话体接管后续，避免 insight 单方面说教
- **一键关闭**：`X` 按钮 status → DISMISSED，后续不再呈现同类
- **长按查看依据**：弹层展示 evidence_ids 对应的 message / mood snapshot 列表，**让用户验证"Aura 没有编造"**
- **类别静音**：长按卡片 → "本周不再说工作类" → 同 category 7 天内不再呈现

### 4.4 隐私的"看见感"

> **强可见的本地存储是信任的来源，不是终点。**

- 设置页直接展示 insights 表当前条数
- 一键导出所有 insights 为 JSON
- 一键删除所有 insights / mood_snapshots / memories
- 单条 insight 可删除，单 category 可静音

**永远不做**：云端备份 insights、云端跨设备同步 insights。

---

## 5. 冷启动问题

> **空数据库的 Aura = 哑巴。** 必须主动引导用户在前 14 天交出足够上下文。

### 5.1 三阶段冷启动

| 阶段 | 时间 | 数据 | Insight 节奏 |
|---|---|---|---|
| **0-3 天：种子期** | 安装后 | 仅 onboarding 时用户填的 3-5 个事实 | 不展示 insight，只展示"正在认识你..."进度 |
| **4-14 天：观察期** | 早期使用 | 累计 5-20 条消息、3-10 条 mood snapshot | 仅展示 Reminder 类（基于 onboarding 事实），不展示 Pattern |
| **15+ 天：稳定期** | 正常使用 | 30+ 条消息、20+ mood snapshots | 完整 1-3 类 insight 节奏 |

### 5.2 Onboarding 的关键作用

> **第一周决定的不是产品体验，而是数据形态。**

Onboarding 必须在前 2 分钟内引导用户分享：

1. **最近 1-2 件让你挂心的事**（"让我记住"）
2. **未来 14 天的 1-2 个重要日期**（"让我提醒"）
3. **你希望我怎么称呼你 / 怎么跟你说话**（"让我适应"）
4. **3 个高频聊天的朋友/家人名字**（"让我识别人"）
5. **你最近的作息节奏**（早睡晚睡 / 工作日周末区别）（"让我知道规律"）

这 5 个问题不靠 LLM，**靠模板化表单**。回答后写入 Auto Memory 的 `user_patterns.md` 和 `recurring_topics.md`，作为前 14 天的 insight 数据源。

### 5.3 早期 Insight 的"低风险样板"

> **前 14 天不允许 LLM 生成 Pattern 类 insight**（数据太薄，幻觉率高）。

早期可展示的 insight 全部来自：

- **Onboarding 事实的回访**："你之前说最近挂心 X，现在怎么样了？"
- **重要日期倒计时**："你说 Y 还有 5 天"
- **作息节律校验**："你说一般 11 点睡，最近 3 天都 1 点睡，是不是节奏变了？"

这些**不需要 LLM 归纳**，靠模板生成。**Aura 的早期价值是"认真听完你 onboarding 时说的话"，不是"看起来很聪明"**。

---

## 6. 反模式（必须避免）

### 6.1 量化自嗨
> ❌ "你这周发了 27 条消息，比上周多 40%！"

Aura 不是用户的报告员，**不做 KPI 展示**。Insight 必须能引发**用户对自己生活状态的反思或行动**，不是显示"我统计得很准"。

### 6.2 过度解读
> ❌ "你用了 3 次'哎'字，说明你最近焦虑"

本地 1.5B 模型没有这种心理学权威。所有 mood / 心理判断**必须**有可验证的数据基础（持续低 mood snapshot、明确文字表达）。

### 6.3 假装懂
> ❌ "作为你的老朋友，我建议你..."

Aura 不是老朋友。Aura 是个**认识你 30 天的 AI**。语气要克制，不要用人际关系伪装亲密感。

### 6.4 替用户决定
> ❌ "你今天应该去跑步" / "你应该早点睡"

Insight 只**指出观察**，不**下达指令**。可以说"你最近 3 周都周日下午情绪偏低" + "要不要聊聊那天一般发生什么"，**不说"你应该去跑步"**。

### 6.5 通知轰炸
> ❌ 每天推 1 条 insight 通知

默认不推通知。通知是高价值 insight 的特权（参见 §4.1）。

---

## 7. 与 dual-mind 各层的关系

| dual-mind 层 | 在 Insight 流水线中的角色 |
|---|---|
| **L1 Inner Monologue** | 间接为 Insight 提供"内心信号"。inner_state.md 长期情绪 / 思考偏向可作为 Pattern 类的辅助依据（不作为主依据） |
| **L2 Dream Loop** | **Insight 的主要生成场所**。Dream run 完成后 Stage 2 跑模式识别 / 关联发现 / 重要日期扫描 |
| **L3 Reactive Companionship** | **不参与 Insight 生成**，但参与用户与 insight 互动后的对话承接 |
| **响应面（云端）** | **不直接生成 Insight**，但消费 Insight 摘要作为 prompt 上下文（"用户最近情绪偏低"作为 system prompt 注入） |

**关键不变量**：
- 响应面**永远不写 insights 表**，只读 `insight_summaries` 视图
- 响应面**永远不知道具体 evidence_ids**，只看到聚合后的"近 7 天 X 类信号 N 条"
- 觉察面**永远不调用云端 LLM 生成 insight**

---

## 8. 与远端 Agent Server 的协同

按 [`agent-capability-server-plan.md`](./agent-capability-server-plan.md) 整体方案，**远端 Agent 在新叙事下被收窄**为"信息回写"型 agent：

| 远端 Agent 能力 | 在新叙事下的允许范围 |
|---|---|
| ✅ 网页监控（降价、新闻） | 回写为 insight 的 `EXTERNAL_WRITE_BACK` 类型 |
| ✅ 文档总结（用户丢链接） | 回写到 memory_summaries，可触发 Pattern |
| ✅ 定期信息拉取（关注的人发新内容） | 回写到 user_patterns / recurring_topics |
| ❌ 通用浏览器自动化 | **不实现**，与"长期认识你"主线无关 |
| ❌ 通用 MCP 工具调度 | **延后**，等 insight 验证产品假设后再考虑 |
| ❌ 通用 Task Scheduler | **延后**，等外部信息回写稳定后再考虑 |

**理由**：远端 agent 是工具，**新叙事下工具必须服务 insight 主线**，否则 Aura 就变成"另一个 ChatGPT"。

---

## 9. Roadmap 锚定（M2-M5 调整）

按本方案，原 roadmap M2-M5 的 KPI 调整为：

### M2: 记忆 + Insight 框架 MVP（原"记忆 MVP"）
- **原 KPI**：记忆可查看
- **新 KPI**：
  - `insights` 表 + Entity + DAO 落地
  - `InsightValidator` 通过单元测试（5+ 边界用例）
  - Onboarding 5 问模板上线
  - 主页 Insight 卡片 UI 落地（仅占位，无真实数据）
  - 用户可一键删除 / 静音

### M3: 情绪 + Insight Pattern MVP（原"情绪 MVP"）
- **原 KPI**：状态持久化 + 头像
- **新 KPI**：
  - `patternDetect` prompt 跑通端到端
  - Dream Loop 跑出第一条真实 Pattern insight
  - 用户能在主页看到第一条"Aura 注意到..."
  - mood trend 可视化上线

### M4: Vision + Insight 增强（"记得你看见了什么"）
- **原 KPI**：CameraX 一次图片
- **新 KPI**：
  - 视觉内容进入 memory（"你在 2026-06-15 拍了张夕阳"）
  - Pattern insight 可跨 mood + memory + 图片生成
  - Connection 类 insight 第一版可触发

### M5: Pulse + Insight 主线化（原"Pulse 与主动陪伴"）
- **原 KPI**：WorkManager pulse + 离线衰减
- **新 KPI**：
  - 每周定时（周日 21:00）自动汇总 → Weekly Insight 推 1 条
  - 高价值 Reminder 走通知
  - 主页"和 Aura 聊聊"按钮预填 prompt 跑通
  - 用户反馈回路打通（👍 / 👎 / 文字 → InsightLog）

### M6-M7: 验证 + 远端收紧
- M6：跑过 30 天 + 100 真实用户的 Insight 数据后再讨论
- M7：远端 Agent 收窄到"信息回写"

---

## 10. 验证指标

> **核心指标不是"用户数"，是"用户 30 天后还愿不愿意看 insight"。**

| 指标 | 目标 | 测量方式 |
|---|---|---|
| **Insight 点击率** | 主页卡片 ≥ 25% | 上线 4 周后统计 |
| **Insight 关闭率** | < 60% 主动 dismiss | 同上 |
| **重复 insight 率** | < 5% | 用 heading 相似度检测 |
| **"和 Aura 聊聊"转化** | 被点 insight 中 ≥ 20% 触发对话 | 客户端埋点 |
| **用户 30 天回访** | ≥ 40% | 周活 / 月活 |
| **本地存储可见性评分** | 用户调研 ≥ 7/10 | 季度问卷 |

如果 **30 天回访 < 20%** 或 **Insight 关闭率 > 80%**，**暂停 M5 及以后**，回 M2 重做。

---

## 11. 关联文档

- [`dual-mind-architecture.md`](./dual-mind-architecture.md) — §1.4 产品叙事与差异化是本方案的上位说明
- [`agent-capability-server-plan.md`](./agent-capability-server-plan.md) — 远端 agent 在新叙事下被收窄为"信息回写"
- [`promise-system-design.md`](./promise-system-design.md) — Promise 系统可视为 insight 的一种特殊类型
- [`roadmap.md`](../roadmap.md) — M2-M5 的 KPI 已按本方案调整

---

**Status: 设计阶段（2026-06-15）**
