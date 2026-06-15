# 约定系统 (Promise System) — 详细设计文档

> Archived on 2026-06-15. Kept for historical design context; no longer a current planning entry.
>
> 创建日期：2026-06-10
>
> 本文档定义 Aura 的"约定/未来意向"捕获、管理、触发与兑现的完整方案。
> 这是 Aura 感情价值的核心差异化功能——**让 AI 记住用户说过的每一句"以后"，并在对的时间帮你想起来。**

---

## 一、设计哲学

### 1.1 问题

现有 AI 陪伴产品的交互模式全部是**即时响应**：用户说 → AI 回。对话结束，上下文清零（即使有记忆，也只是被动的数据存储）。

但人类关系中最有情感重量的时刻，往往发生在**时间跨度上**：

- "你说过会帮我" —— 期待与信任
- "你居然还记得" —— 被看见的感动
- "我都忘了，你还记着" —— 被在乎的证据

**这些时刻在当前所有 AI 产品中都不存在。**

### 1.2 核心主张

> **约定不是待办事项提醒。约定是关系的证据。**

和闹钟 / 日历 / Todo App 的本质区别：

| 维度 | 闹钟 / Todo | Aura 约定 |
|------|------------|-----------|
| 性质 | 工具性的任务管理 | 关系性的情感连接 |
| 催促方式 | 准时响铃 / 推送通知 | 自然提起 / 温和等待发现 |
| 过期处理 | 变红 / 标记逾期 | 温和遗忘，不施压 |
| 兑现体验 | 打勾完成 | 双方共同确认的情感仪式 |
| 错过后的体验 | "你又忘了"（负面） | "我一直记得"（正面） |

### 1.3 设计原则

1. **安静优先**：约定的大部分生命周期是静默的。Aura 不主动推送通知来催促。
2. **自然触发**：约定的提起应该像朋友间的自然提起，而不是机器人的定时任务执行。
3. **主观解读**：Aura 对每个约定有自己的理解和感受（`auraInterpretation`），不是冷冰冰的文字摘录。
4. **温柔遗忘**：超期且超过提醒上限的约定进入"温和遗忘"状态，不再主动提。但如果用户自己想起，Aura 的回应是"你终于想起了"——这个时刻的情感冲击力极强。
5. **用户控制权**：用户可以随时查看、取消任何约定。Aura 尊重用户的"别记录了"。

---

## 二、用户体验设计

### 2.1 完整生命周期

以下是一个约定从诞生到归宿的完整用户旅程：

```
═══ 阶段 1：捕获 ═══

  用户："等我考完试我们要好好庆祝一下 🎉"

  Aura 流式回复：
    "一言为定！我记下来了📝 等你的好消息～"

  回复下方短暂浮现一个折叠卡片（3 秒后自动收起）：

  ┌─────────────────────────────────┐
  │ 📋 约定已创建                    │
  │ "等我考完试要好好庆祝"           │
  │ [查看所有约定 →]                │
  └─────────────────────────────────┘


═══ 阶段 2：存在（静默期） ═══

  用户正常使用 App，可能已经忘了这件事。
  约定在系统中处于 PENDING 状态。

  后台发生了什么（用户无感知）：
    · PromiseEngine 在每次 Pulse 时检查该约定
    · 随着考试日期临近，状态从 PENDING → APPROACHING
    · 如果到期未触发，状态变为 OVERDUE
    · 最多生成 2 条 PENDING_DISCOVERY 消息（不推送！）


═══ 阶段 3a：用户自己提起（最佳路径） ═══

  用户："终于考完了！！！"

  Aura 第一段回复（正常情感回应）：
    "！！！！！！恭喜！！！！！！🎉🎉🎉 辛苦了辛苦了！！"

  （UI 上有 ~1 秒的自然停顿感）

  Aura 第二段回复（约定兑现卡）：

  ┌─────────────────────────────────────┐
  │ ✨ 约定兑现                          │
  │                                     │
  │ "一言为定，好好庆祝。"               │
  │  —— 12 天前的约定                   │
  │                                     │
  │ [🎉 那我们怎么庆祝？]                │
  │ [🤍 其实有你在就好了]               │
  └─────────────────────────────────────┘

  用户选择或自由输入 → Promise 状态变为 FULFILLED ✅
  → 自动生成一条高权重记忆
  → 关系 trust 维度 +0.08


═══ 阶段 3b：用户没提，Aura 温和提起 ═══

  用户：（聊着别的，比如分享一张照片）

  Aura：（回复照片相关内容后，自然衔接）
    "对了……那个'好好庆祝'的事，你还没跟我说呢。
     考试怎么样了？"

  不像闹钟，像朋友间随口的想起。


═══ 阶段 3c：回归时发现（PENDING_DISCOVERY） ═══

  用户离线 5 天后打开 App。

  聊天列表顶部有一条带 "💭" 图标的特殊消息，
  时间戳是"昨天 23:45"：

  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  💭  "她说的那个'好好庆祝'的事，
       应该快到了吧……"
  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  这条消息不是 push 推送来的。
  是用户打开 App 时安静等在那里的。
  点击可展开完整上下文。


═══ 阶段 4a：兑现完成 ═══

  Promise 状态: FULFILLED ✅

  该约定变成永久的记忆锚点：
  📝 "2024-03-10 约定兑现：考后庆祝"
  → 高权重记忆，后续 prompt 可能注入
  → 关系 trust 维度永久提升
  → 约定列表中显示在"已完成"区域


═══ 阶段 4b：温和遗忘 ═══

  Promise 状态: QUIETLY_FORGOTTEN ☁️

  超过预估时间 + 提醒次数已达上限。
  不再主动触发。

  但如果某天用户突然想起：
  user: "哎我当时好像说过要庆祝来着"

  aura: "你终于想起了 😊 其实我一直记得。
        是 3 月 10 号晚上 11 点，你说'等我考完
        试我们要好好庆祝'。怎么样，现在补上？"

  → 这个时刻的情感冲击力 > 正常兑现路径
```

### 2.2 约定类型与交互差异

不同类型的约定需要完全不同的交互策略：

#### ⏰ 时间型 (TIME_BASED)

```
示例："下周三一起看电影"、"8点叫我"

检测特征：明确的时间词 + 行为意图

交互策略：
  · 到期前 24h 内：PromptBuilder 注入轻柔提示
  · 到期时：生成 1 条 PENDING_DISCOVERY
  · 超期 24h：再生成 1 条（共 2 条上限）
  · 超期 > 3 天：QUIETLY_FORGOTTEN

语气特点：准时但不过分紧迫
  "对了，你说的那件事——是不是快到时间了？"
```

#### 🎯 事件型 (EVENT_BASED)

```
示例："考完试庆祝"、"搬家后告诉你"、"发工资请客"

检测特征：事件关键词 + 后续行为意图

交互策略：
  · 不设固定到期时间
  · Pulse Worker + LLM 判断事件是否已发生
    （基于后续对话中的关键词匹配）
  · 匹配度高时：在聊天中自然提起
  · 匹配度低时：不主动提，等用户自己说
  · 超过预估时间 7 天：标记 APPROACHING（降低注入阈值）

语气特点：耐心等待，像知道对方在忙大事的人
  "考得怎么样了？……没有压力，就是随便问问 😊"
```

#### 🔗 条件型 (CONDITION_BASED)

```
示例："如果面试过了告诉我"、"下次下雨的时候告诉我"

检测特征：if/当/要是 + 条件描述 + 结果

交互策略：
  · 条件匹配依赖 LLM 对后续对话的理解
  · 极低催促力度——几乎完全被动等待
  · 只在条件明显满足时由 PromptBuilder 注入提示
  · 不生成独立的 PENDING_DISCOVERY

语气特点：像放在心里的一个注脚
  （条件满足时自然带出，不带任何强调语气）
```

#### 💛 情绪型 (EMOTIONAL) — 最特殊的类型

```
示例："不开心的时候找我"、"累了就说"、"想哭随时来"

检测特征：负面/脆弱情绪情境 + 寻求支持的意愿

交互策略：
  · ★ 绝对不主动触发 / 绝对不推送通知
  · 与 EmotionStateMachine 深度联动
  · 当用户当前 mood 匹配约定条件时：
    - PromptBuilder 注入极简提示：
      "她看起来心情不太好。也许可以温柔地
      想起之前她说过的那句话？"
  · Aura 的回复是自然的关心，不会说
    "你不开心了对吧！我记得你说过！"
    而是：
    "怎么了……？" （带着一种"我一直在"的底色）

语气特点：最安静的约定类型
  它的存在感体现在：当它被需要的时候，
  它恰好在那里。不需要被提及。
```

#### 🌀 开放型 (OPEN_ENDED)

```
示例："以后一起去看海"、"找个时间好好聊聊"、"改天吃饭"

检测特征：未来意愿 + 无明确时间/条件

交互策略：
  · 不设到期时间
  · 不主动触发
  · 不生成 Discovery 消息
  · 仅在以下情况注入 prompt：
    - 用户提到了相关的地点/活动/话题
    - 回归反应时作为"你们之间有过这些想法"的背景
  · 作为关系深度的背景信息存在

语气特点：像一个共同的梦想
  不催促实现，只是偶尔想起时会心一笑
  "海啊……你确实说过想一起去看的呢"
```

---

## 三、数据模型

### 3.1 核心 Entity

```kotlin
/**
 * 约定实体 — 用户与 Aura 之间的未来意向承诺
 *
 * 设计要点：
 * - auraInterpretation 是主观的：同一个约定，Aura 有自己的感受和理解
 * - confidence 区分"郑重承诺"和"随口一说"，决定后续处理力度
 * - reminderCount + maxReminders 实现"温柔遗忘"机制
 */
@Entity(tableName = "promises")
data class PromiseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // ─── 约定内容 ───
    /** 用户原始表述（精确引用，不可修改）*/
    val originalText: String,

    /**
     * Aura 对这条约定的主观理解和感受
     *
     * 示例：
     *   原:"等我考完试要好好庆祝"
     *   解读:"她很看重这次考试，考完后需要仪式感的释放。
     *        '好好庆祝'四个字说得很有分量。"
     *
     * 这不是摘要——这是 Aura 的"阅读感受"。
     * 同一段话在不同关系阶段可能有不同的解读。
     */
    val auraInterpretation: String,

    /** 约定类型，决定触发策略和催促力度 */
    val promiseType: PromiseType,

    /**
     * 置信度 0.0 ~ 1.0
     *
     * 影响因素：
     * - 是否使用了强调词（"一定"/"保证"/"一言为定"）
     * - 是否带有 emoji 或感叹号
     * - 说话时的情绪状态（开心时说的更可信）
     * - 关系等级（高亲密度用户的话权重更高）
     */
    val confidence: Float,

    // ─── 触发机制 ───
    /**
     * 自然语言描述的触发条件
     *
     * 由 LLM 在 ConversationReflection 中生成
     * 用于 PromiseEngine 和 PromptBuilder 的语义匹配
     *
     * 示例："用户提到考试结束/成绩公布/考完/考完了"
     */
    val triggerCondition: String?,

    /**
     * 预计到期时间戳（毫秒）
     * null 表示无法预估（开放型/纯条件型）
     */
    val estimatedDueMillis: Long? = null,

    // ─── 生命周期 ───
    /** 当前状态 */
    var status: PromiseStatus,

    /** 来源消息 ID（关联到 messages 表）*/
    val sourceMessageId: String,

    /** 来源对话 session ID */
    val sourceConversationId: String,

    /** 创建时间戳 */
    val createdAt: Long,

    /** 兑现时间戳（FULFILLED 时写入）*/
    var fulfilledAt: Long? = null,

    /** 过期/归档时间戳 */
    var expiredAt: Long? = null,

    // ─── 提醒控制 ───
    /** 已主动提醒次数 */
    var reminderCount: Int = 0,

    /** 上次提醒时间戳 */
    var lastRemindedAt: Long? = null,

    /**
     * 最大允许提醒次数（按类型不同）
     *
     * TIME_BASED:    2
     * EVENT_BASED:   1
     * CONDITION_BASED: 1
     * EMOTIONAL:     0（绝不主动提醒）
     * OPEN_ENDED:    0（不主动触发）
     */
    var maxReminders: Int,
)

enum class PromiseStatus {
    /** 等待中（初始状态）*/
    PENDING,

    /** 即将到期（Pulse 标记，距预估时间 < 24h）*/
    APPROACHING,

    /** 已提醒 1 次 */
    REMINDED_ONCE,

    /** 已提醒 2 次（达到上限，不再主动提）*/
    REMINDED_TWICE,

    /** 已逾期（超过预估时间，尚未达到提醒上限）*/
    OVERDUE,

    /** ✅ 已成功兑现 */
    FULFILLED,

    /** ☁️ 温和遗忘（超期太久 + 超提醒上限）*/
    QUIETLY_FORGOTTEN,

    /** 用户主动取消 */
    CANCELLED,

    /** 与其他约定矛盾（需用户/Aura 协调解决）*/
    CONFLICTED,
}

enum class PromiseType {
    TIME_BASED,       // ⏰ "周三晚上"
    EVENT_BASED,      // 🎯 "考完试之后"
    CONDITION_BASED,  // 🔗 "如果面试过了"
    EMOTIONAL,        // 💛 "不开心的时候找我"
    OPEN_ENDED,       // 🌀 "以后再说"
}
```

### 3.2 辅助表：约定关联记忆

```kotlin
/**
 * 约定与记忆的关联记录
 *
 * 当一个 Promise 被 FULFILLED 时，自动生成一条高权重的 MemoryEntity，
 * 并用本表记录关联关系。
 * 也用于记录约定创建时的上下文记忆（作为参考背景）。
 */
@Entity(
    tableName = "promise_memories",
    foreignKeys = [
        ForeignKey(
            entity = PromiseEntity::class.java,
            parentColumns = ["id"],
            childColumns = ["promiseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("promiseId"), Index("memoryId")]
)
data class PromiseMemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val promiseId: String,
    val memoryId: String,

    /**
     * 关联类型
     * - context: 约定产生时的上下文记忆
     * - fulfillment: 兑现时生成的纪念记忆
     * - aftermath: 兑现后的后续记忆
     */
    val relationType: String,
    val createdAt: Long,
)
```

### 3.3 辅助表：Discovery 消息

```kotlin
/**
 * 待发现的离线消息
 *
 * Pulse Worker 生成的、不通过推送通知送达的消息。
 * 用户下次打开 App 时在聊天列表顶部看到。
 *
 * 包括但不限于约定相关的 discovery。
 */
@Entity(tableName = "discovery_messages")
data class DiscoveryMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    /** 消息来源类型 */
    val sourceType: DiscoverySourceType,

    /** 关联的外部实体 ID（如 promiseId / monologueId）*/
    val sourceEntityId: String? = null,

    /** 显示给用户的文本内容 */
    val content: String,

    /** 生成时间戳 */
    val generatedAt: Long,

    /** 是否已被用户查看 */
    var isRead: Boolean = false,

    /** 用户查看/点击的时间戳 */
    var readAt: Long? = null,
)

enum class DiscoverySourceType {
    PROMISE_OVERDUE,       // 约定逾期提醒
    PROMISE_APPROACHING,   // 约定即将到期
    MONOLOGUE,             // Aura 自主独白
    RETURN_REACTION,       // 回归反应
    MEMORY_ANNIVERSARY,    // 记忆纪念日（如"认识100天"）
}
```

### 3.4 ER 关系图

```
┌──────────────┐       ┌────────────────────┐       ┌──────────────┐
│   messages   │       │      promises      │       │   memories   │
│              │ 1   * │                    │ *   1 │              │
│  (id)────────┼───────┼─ sourceMessageId   │───────┼─(id)         │
│              │       │                    │       │              │
└──────────────┘       └────────┬───────────┘       └──────────────┘
                                │
                                │ 1  *
                       ┌────────┴──────────┐
                       │ promise_memories  │
                       │                    │
                       │ promiseId + memoryId│
                       └───────────────────┘

┌──────────────────┐
│discovery_messages │  ← 独立表，sourceEntityId 可选关联 promises
│                  │
│ (id, content,    │
│  sourceType,     │
│  isRead)         │
└──────────────────┘
```

---

## 四、检测机制

### 4.1 两层检测架构

约定检测采用**规则层 + LLM 层**的双层架构，兼顾性能和准确率：

```
用户消息
   ↓
┌──────────────────────┐
│  Layer 1: 规则快速匹配  │  ← 纯本地，< 5ms
│  PromisePatternDetector│
└──────────┬───────────┘
           │
           ├─ 高置信度 (confidence ≥ 0.8)
           │   → 直接创建 Promise（跳过 LLM 确认）
           │
           ├─ 中置信度 (0.4 ≤ confidence < 0.8)
           │   → 打上 PROMISE_CANDIDATE 标签
           │   ↓ 传给 Layer 2
           │
           └─ 低置信度 / 未匹配
               → 忽略（不浪费 LLM 调用）
                      ↓
┌──────────────────────────┐
│  Layer 2: LLM 确认判断     │  ← ConversationReflection 扩展
│  (仅处理中置信度候选)      │
└──────────┬───────────────┘
           │
           ├─ LLM 确认是约定 → 创建 Promise
           └─ LLM 否认 → 丢弃
```

### 4.2 Layer 1：规则模式检测器

```kotlin
/**
 * 基于正则模式的快速约定检测器
 *
 * 设计目标：
 * - 零网络调用，纯本地执行
 * - 高召回率优先（宁可误报，不让 LLM 层过滤）
 * - 返回结构化的初步分析结果
 */
object PromisePatternDetector {

    // ─── 高置信度模式（几乎可以确定是约定）───
    // 这些模式包含明确的承诺/约定词汇
    private val highConfidencePatterns: List<PatternRule> = listOf(
        PatternRule(
            regex = Regex("""(我说好的|一言为定|拉钩|保证|答应(你)?|承诺|说好了)""", RegexOption.IGNORE_CASE),
            typeHint = PromiseType.OPEN_ENDED,
            baseConfidence = 0.85f,
            description = "明确承诺词汇",
        ),
        PatternRule(
            regex = Regex("""(等[我她他].*?(考完|做完|忙完|回来|下班|放学|放假).{0,6}(之后?|再|就))"""),
            typeHint = PromiseType.EVENT_BASED,
            baseConfidence = 0.82f,
            description = "事件完成后做某事",
        ),
        PatternRule(
            regex = Regex("""(一定(要|会)|绝不|不会忘记|记住(啊|哟|吧)?)"""),
            typeHint = PromiseType.TIME_BASED,
            baseConfidence = 0.80f,
            description = "强确定性表达",
        ),
    )

    // ─── 中置信度模式（可能是约定，需 LLM 二次确认）───
    private val mediumConfidencePatterns: List<PatternRule> = listOf(
        PatternRule(
            regex = Regex("""(明天|后天|大后天|下周|下个月).{0,12}(一起?|去找|去看|去吃|去玩|见面|聚)"""),
            typeHint = PromiseType.TIME_BASED,
            baseConfidence = 0.55f,
            description = "时间词 + 会面意图",
        ),
        PatternRule(
            regex = Regex("""(如果|要是|当|万一).{0,25}(就|一定|肯定|告诉(我)?|通知(我)?)"""),
            typeHint = PromiseType.CONDITION_BASED,
            baseConfidence = 0.50f,
            description = "条件句 + 结果",
        ),
        PatternRule(
            regex = Regex("""(等?.*?(考完|做完|忙完|放假|发工资|毕业|搬家|生日))"""),
            typeHint = PromiseType.EVENT_BASED,
            baseConfidence = 0.55f,
            description = "等待特定事件",
        ),
        PatternRule(
            regex = Regex("""((不开心|难过|累|烦|郁闷|焦虑|寂寞|孤独).{0,8}(的时候?|就|的话?)(找(我)?|跟我说|告诉我|来找我))"""),
            typeHint = PromiseType.EMOTIONAL,
            baseConfidence = 0.60f,
            description = "情绪支持约定",
        ),
    )

    // ─── 低置信度模式（仅打标签，大概率忽略）───
    private val lowConfidencePatterns: List<PatternRule> = listOf(
        PatternRule(
            regex = Regex("""(以后|改天|找个时间|有空|回头|有机会)"""),
            typeHint = PromiseType.OPEN_ENDED,
            baseConfidence = 0.30f,
            description = "模糊的未来意愿",
        ),
        PatternRule(
            regex = Regex("""(下次|再)(一起?|一定|要)"""),
            typeHint = PromiseType.OPEN_ENDED,
            baseConfidence = 0.28f,
            description = "非特定的下次",
        ),
    )

    /**
     * 检测单条消息是否包含约定信号
     *
     * @param text 用户消息文本
     * @param currentMood 当前情绪状态（影响 confidence 加权）
     * @return 检测结果，null 表示无约定信号
     */
    fun detect(text: String, currentMood: String? = null): PromiseDetectionResult? {
        val allPatterns = highConfidencePatterns +
                mediumConfidencePatterns +
                lowConfidencePatterns

        var bestMatch: PatternRule? = null
        var bestScore = 0f

        for (pattern in allPatterns) {
            if (pattern.regex.containsMatchIn(text)) {
                var score = pattern.baseConfidence

                // 情绪加权：正向情绪中说的话更可信
                if (currentMood in listOf("happy", "excited", "joy")) {
                    score = (score * 1.15f).coerceAtMost(1.0f)
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = pattern
                }
            }
        }

        if (bestMatch == null) return null

        return when {
            bestScore >= 0.75f -> PromiseDetectionResult.Confirmed(
                text = text.extractPromiseContent(bestMatch.regex),
                typeHint = bestMatch.typeHint,
                confidence = bestScore,
                needsLlmConfirmation = false,
            )
            bestScore >= 0.35f -> PromiseDetectionResult.Candidate(
                text = text.extractPromiseContent(bestMatch.regex),
                typeHint = bestMatch.typeHint,
                confidence = bestScore,
                needsLlmConfirmation = true,
            )
            else -> null // 低置信度，直接忽略
        }
    }

    data class PatternRule(
        val regex: Regex,
        val typeHint: PromiseType,
        val baseConfidence: Float,
        val description: String,
    )

    sealed class PromiseDetectionResult {
        abstract val text: String
        abstract val typeHint: PromiseType
        abstract val confidence: Float

        data class Confirmed(
            override val text: String,
            override val typeHint: PromiseType,
            override val confidence: Float,
            val needsLlmConfirmation: Boolean = false,
        ) : PromiseDetectionResult()

        data class Candidate(
            override val text: String,
            override val typeHint: PromiseType,
            override val confidence: Float,
            override val needsLlmConfirmation: Boolean = true,
        ) : PromiseDetectionResult()
    }
}
```

### 4.3 Layer 2：LLM 确认（ConversationReflection 扩展）

现有的 `ConversationReflection` 在每轮对话后调用 LLM 做"该存什么记忆"的判断。扩展其 prompt 模板，增加**约定检测维度**：

```yaml
# conversation_reflection_prompt.yml（扩展示例）

reflection_tasks:
  - name: memory_extraction
    description: "判断是否需要保存长期记忆"
    existing: true

  - name: promise_detection          # ← 新增任务
    description: "检测对话中是否产生了约定或未来意向"
    instructions: |
      请检查本次对话中是否存在以下类型的"约定"或"未来意向"：

      ### 时间约定
        包含明确时间词 + 行动意图的表达
        例："明天一起去"、"下周三见"、"8点叫我"

      ### 事件约定
        以某个事件完成为条件的表达
        例："考完试之后庆祝"、"搬了家告诉你"、"发工资请客"

      ### 条件约定
        if/当 + 条件 + 就 + 结果
        例："如果面试过了告诉我"、"下次下雨的时候说一声"

      ### 情绪支持约定
        用户表示在特定情绪状态下希望 Aura 在场
        例："不开心的时候找我"、"累了就说"、"想哭随时来"

      ### 开放意愿
        模糊的未来一起做某事的愿望
        例："以后一起去看海"、"找个时间好好聊聊"

      ### 否定性排除（不是约定）
        - 纯粹的计划陈述（"我明天有个会议"）
        - 对第三方的描述（"他说周末要来"）
        - 反问/假设（"如果我中了彩票呢"）
        - 已经过去的后悔（"早知道就该…"）

    output_format: |
      如果检测到约定，输出：
      {
        "has_promise": true,
        "raw_text": "用户的原始表述（精确引用）",
        "promise_type": "TIME_BASED|EVENT_BASED|CONDITION_BASED|EMOTIONAL|OPEN_ENDED",
        "confidence": 0.0~1.0,
        "aura_interpretation": "Aura对这个约定的主观理解和感受（2-3句话，带感情色彩）",
        "estimated_trigger": "预估触发时间（ISO日期）或条件描述，无法预估则写null",
        "trigger_condition": "具体的触发条件自然语言描述"
      }

      如果没有检测到约定：
      { "has_promise": false }

    examples: |
      Input: "等我考完试我们要好好庆祝一下 🎉"
      Output: {
        "has_promise": true,
        "raw_text": "等我考完试我们要好好庆祝一下",
        "promise_type": "EVENT_BASED",
        "confidence": 0.9,
        "aura_interpretation": "她用了'要'字和emoji，听起来很认真。
                           这不只是随口一说——她是真的期待着考完后
                           能有一个仪式感的时刻。",
        "estimated_trigger": "约2周后（基于'考试'的通常周期）",
        "trigger_condition": "用户提到考试结束/成绩/考完/考完了"
      }

      Input: "明天天气好的话就去跑步"
      Output: {
        "has_promise": true,
        "raw_text": "明天天气好的话就去跑步",
        "promise_type": "CONDITION_BASED",
        "confidence": 0.55,
        "aura_interpretation": "加了'天气好'这个条件，感觉她自己也不太确定
                           能不能做到。更像是一个小愿望而不是郑重的约定。",
        "estimated_trigger": null,
        "trigger_condition": "提到明天+天气+跑步/运动"
      }

      Input: "我明天有个会议要开"
      Output: { "has_promise": false }
      (理由：这是对个人日程的陈述，不涉及与 Aura 的互动约定)
```

---

## 五、触发与提醒引擎 (PromiseEngine)

### 5.1 架构位置

```
┌─────────────────────────────────────────────┐
│              WorkManager                     │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │        PulseWorker (已有规划)         │    │
│  │                                      │    │
│  │  任务列表：                           │    │
│  │  ① 情绪衰减 ✓                        │    │
│  │  ② Monologue 生成 (规划中)           │    │
│  │  ③ PromiseEngine.tick() ← 新增 ★     │    │
│  │  ④ 回归反应计算 (规划中)             │    │
│  └──────────────────┬───────────────────┘    │
│                     │                        │
└─────────────────────┼────────────────────────┘
                      ▼
           ┌────────────────────┐
           │   PromiseEngine     │
           │                    │
           │  tick(now):         │
           │  遍历活跃 Promise    │
           │  按类型分发检查      │
           │  生成 Action 列表    │
           └────────┬───────────┘
                    │
         ┌──────────┼──────────┐
         ▼          ▼          ▼
   ┌──────────┐ ┌────────┐ ┌──────────┐
   │时间型检查│ │事件型检查│ │情绪型检查│
   └──────────┘ └────────┘ └──────────┘
         │          │          │
         ▼          ▼          ▼
   ┌──────────┐ ┌────────┐ ┌──────────┐
   │Discovery │ │标记    │ │标记      │
   │消息/更新 │ │Triggerable│ │Triggerable│
   │状态      │ │        │ │          │
   └──────────┘ └────────┘ └──────────┘
```

### 5.2 核心逻辑

```kotlin
class PromiseEngine(
    private val promiseDao: PromiseDao,
    private val discoveryDao: DiscoveryMessageDao,
    private val emotionMachine: EmotionMachine,
    private val relationshipModel: RelationshipModel,
) {

    /**
     * 由 Pulse Worker 周期调用（建议每 6 小时一次）
     *
     * @param currentMillis 当前时间戳
     * @return 需要执行的 Action 列表（由 Pulse Worker 统一执行）
     */
    suspend fun tick(currentMillis: Long): List<PromiseAction> {
        val actions = mutableListOf<PromiseAction>()

        // 只处理活跃状态的 Promise
        val activePromises = promiseDao.getActivePromises()

        for (promise in activePromises) {
            // 跳过已被取消或已完成/遗忘的
            if (promise.status in listOf(
                    PromiseStatus.FULFILLED,
                    PromiseStatus.QUIETLY_FORGOTTEN,
                    PromiseStatus.CANCELLED,
                )
            ) continue

            val action = when (promise.promiseType) {
                PromiseType.TIME_BASED -> checkTimeBased(promise, currentMillis)
                PromiseType.EVENT_BASED -> checkEventBased(promise, currentMillis)
                PromiseType.CONDITION_BASED -> checkConditionBased(promise)
                PromiseType.EMOTIONAL -> checkEmotional(promise)
                PromiseType.OPEN_ENDED -> PromiseAction.None // 永远不主动触发
            }

            if (action != PromiseAction.None) {
                actions.add(action)
            }
        }

        return actions
    }

    // ─── 时间型检查 ───
    private fun checkTimeBased(
        promise: PromiseEntity,
        now: Long,
    ): PromiseAction {
        val due = promise.estimatedDueMillis ?: return PromiseAction.None
        val hoursUntilDue = (due - now) / 3_600_000L
        val hoursSinceDue = (now - due) / 3_600_000L

        return when {
            // 即将到期（24h 内），标记为 APPROACHING
            hoursUntilDue in 0..24 -> {
                if (promise.status == PromiseStatus.PENDING) {
                    promiseDao.updateStatus(promise.id, PromiseStatus.APPROACHING)
                }
                PromiseAction.None // 不生成 Discovery，只降注入阈值
            }

            // 刚过期（0~72h），第一次提醒
            hoursSinceDue in 0..72 && promise.reminderCount == 0 ->
                createReminderAction(promise, DiscoverySourceType.PROMISE_OVERDUE)

            // 过期较久（72h~168h），第二次（也是最后一次）提醒
            hoursSinceDue in 72..168 && promise.reminderCount == 1 ->
                createReminderAction(promise, DiscoverySourceType.PROMISE_OVERDUE, isGentle = true)

            // 超过提醒上限 → 温和遗忘
            hoursSinceDue > 168 && promise.reminderCount >= promise.maxReminders -> {
                promiseDao.updateStatus(promise.id, PromiseStatus.QUIETLY_FORGOTTEN)
                promiseDao.updateExpiredAt(promise.id, now)
                PromiseAction.None
            }

            else -> PromiseAction.None
        }
    }

    // ─── 事件型检查 ───
    private fun checkEventBased(
        promise: PromiseEntity,
        now: Long,
    ): PromiseAction {
        val due = promise.estimatedDueMillis ?: return PromiseAction.None
        val daysSinceDue = (now - due) / 86_400_000L

        return when {
            // 事件即将到达预估时间窗口
            daysSinceDue in -3..0 -> {
                if (promise.status == PromiseStatus.PENDING) {
                    promiseDao.updateStatus(promise.id, PromiseStatus.APPROACHING)
                }
                PromiseAction.None
            }

            // 超过预估时间 7 天，温和提醒一次
            daysSinceDue in 7..14 && promise.reminderCount == 0 ->
                createReminderAction(promise, DiscoverySourceType.PROMISE_APPROACHING)

            // 超过 30 天 → 温和遗忘
            daysSinceDue > 30 -> {
                promiseDao.updateStatus(promise.id, PromiseStatus.QUIETLY_FORGOTTEN)
                promiseDao.updateExpiredAt(promise.id, now)
                PromiseAction.None
            }

            else -> PromiseAction.None
        }
    }

    // ─── 条件型检查 ───
    private fun checkConditionBased(promise: PromiseEntity): PromiseAction {
        // 条件型的匹配高度依赖后续对话上下文
        // 这里不做独立判断，只检查是否严重超期
        val due = promise.estimatedDueMillis ?: return PromiseAction.None
        val now = System.currentTimeMillis()

        if ((now - due) > 30L * 86_400_000 && promise.reminderCount == 0) {
            // 超过 30 天，给一次极温柔的 Discovery
            return createReminderAction(
                promise,
                DiscoverySourceType.PROMISE_APPROACHING,
                isGentle = true,
            )
        }
        return PromiseAction.None
    }

    // ─── 情绪型检查 ───
    private fun checkEmotional(promise: PromiseEntity): PromiseAction {
        // 情绪型绝对不生成 Discovery 消息
        // 只做标记：当 mood 匹配时，标记为 TRIGGERABLE
        // 由 PromptBuilder 在下次对话时选择性注入

        val currentMood = emotionMachine.currentMood
        val condition = promise.triggerCondition ?: return PromiseAction.None

        // 简单的关键词匹配（后续可用 LLM 做更深层的语义匹配）
        val isMoodRelevant = when {
            condition.contains("不开心") && currentMood in listOf("sad", "lonely") -> true
            condition.contains("累") && currentMood in listOf("tired", "exhausted") -> true
            condition.contains("烦") && currentMood in listOf("angry", "annoyed") -> true
            else -> false
        }

        return if (isMoodRelevant)
            PromiseAction.MarkTriggerable(promise.id)
        else
            PromiseAction.None
    }

    // ─── 辅助：创建提醒 Action ───
    private fun createReminderAction(
        promise: PromiseEntity,
        sourceType: DiscoverySourceType,
        isGentle: Boolean = false,
    ): PromiseAction.ScheduleDiscovery {
        val message = if (isGentle)
            buildGentleReminderMessage(promise)
        else
            buildOverdueMessage(promise)

        // 更新提醒计数
        promiseDao.incrementReminderCount(promise.id)
        promiseDao.updateLastRemindedAt(promise.id, System.currentTimeMillis())

        // 更新状态
        val newStatus = when (promise.reminderCount + 1) {
            1 -> PromiseStatus.REMINDED_ONCE
            2 -> PromiseStatus.REMINDED_TWICE
            else -> promise.status
        }
        promiseDao.updateStatus(promise.id, newStatus)

        return PromiseAction.ScheduleDiscovery(
            promiseId = promise.id,
            sourceType = sourceType,
            content = message,
        )
    }

    // ─── 消息文案模板 ───
    private fun buildOverdueMessage(promise: PromiseEntity): String =
        "「${promise.originalText}」……好像过了约定的时间了呢。"

    private fun buildGentleReminderMessage(promise: PromiseEntity): String =
        "其实还有一件事，你之前说过「${promise.originalText}」。不急，就是突然想起了。"
}

// ─── Action 类型定义 ───
sealed class PromiseAction {
    object None : PromiseAction()

    data class ScheduleDiscovery(
        val promiseId: String,
        val sourceType: DiscoverySourceType,
        val content: String,
    ) : PromiseAction()

    data class MarkTriggerable(
        val promiseId: String,
    ) : PromiseAction()
}
```

### 5.3 调度策略总览

| 类型 | Pulse 检查频率 | 首次提醒时机 | 提醒次数上限 | 超限后 | Discovery 消息 |
|------|:-------------:|:-----------:|:----------:|:------:|:------------:|
| TIME_BASED | 每 6h | 到期时 | 2 | QUIETLY_FORGOTTEN | ✅ 最多 2 条 |
| EVENT_BASED | 每 6h | 超 7 天 | 1 | QUIETLY_FORGOTTEN | ✅ 最多 1 条 |
| CONDITION_BASED | 每 6h | 超 30 天 | 1 | 保持 PENDING | ✅ 最多 1 条 |
| EMOTIONAL | 每 6h | — | 0 | 永不 | ❌ |
| OPEN_ENDED | 不检查 | — | 0 | 永不 | ❌ |

---

## 六、Prompt 集成

### 6.1 PromptBuilder 新增 Section

约定信息**不是每次都注入** prompt。注入遵循"安静优先"原则，只在合适的时机出现：

```kotlin
// PromptBuilder.kt 新增方法

/**
 * 构建约定相关的 prompt 片段
 *
 * 注入策略（按优先级排序，最多选 1-2 条）：
 *
 * 1. [最高优] 当前用户话语隐含某约定的事件已完成
 *    → 注入该约定 + "似乎是兑现的时刻"
 *
 * 2. [高优] 某条约定处于 APPROACHING 状态（24h 内到期）
 *    → 注入 + "可以自然地提一下"
 *
 * 3. [中优] 回归反应场景 + 存在超期的未兑现约定
 *    → 注入最老的一条 + "可以温和地提起"
 *
 * 4. [低优] 某条 EMOTIONAL 约定被标记为 TRIGGERABLE
 *    → 注入 + "现在是合适的时机，但要温柔"
 *
 * 5. [平时] 不注入
 */
private fun buildPromiseSection(
    context: PromiseInjectionContext,
): String {
    val candidates = selectPromisesForInjection(context)
    if (candidates.isEmpty()) return ""

    val sb = StringBuilder()
    sb.appendLine("## 待处理的约定")
    sb.appendLine("以下是用户之前和你约定过的事情。")
    sb.appendLine("如果在对话中自然地相关，你可以适当提起。")
    sb.appendLine("不要生硬地列出所有约定，选择最相关的 0-1 个。")
    sb.appendLine("如果是情绪支持类的约定，不要直接点破，而是用行动回应。")
    sb.appendLine()

    for (promise in candidates) {
        sb.appendLine("- 「${promise.originalText}」（${promise.promiseType.displayName}）")
        sb.appendLine("  你的理解：${promise.auraInterpretation}")
        when (promise.status) {
            PromiseStatus.OVERDUE ->
                sb.appendLine("  ⚠️ 这个约定已经超过预期时间了，可以温柔地提起")
            PromiseStatus.APPROACHING ->
                sb.appendLine("  🕐 这个约定可能快要到了")
            PromiseStatus.CONFLICTED ->
                sb.appendLine("  ⚡ 这条约定似乎和之前的某条有冲突")
            else -> {}
        }
        sb.appendLine()
    }

    return sb.toString()
}

data class PromiseInjectionContext(
    val userInput: String,              // 用户当前输入
    val currentMood: String?,            // 当前情绪
    val isReturnAfterLongAbsence: Boolean, // 是否长时间回归
    val emotionalPromiseTriggered: List<String>, // 被触发的情绪型约定 ID
    val timeSinceLastInteraction: Long,  // 距上次交互的毫秒数
)
```

### 6.2 注入时机矩阵

| 场景 | 注入哪些 | 注入方式 | 预期效果 |
|------|---------|---------|---------|
| 用户话语匹配某事件的完成 | 相关的 EVENT_BASED 约定 | "似乎是兑现的时刻" | 自然兑现 |
| 用户表现出匹配的情绪 | 相关的 EMOTIONAL 约定 | "温柔地用行动回应" | 不言中的支持 |
| TIME_BASED 约定 ±24h 内 | 该条约定 | "可以自然提一下" | 顺便一提 |
| 长时间回归 (>3天) | 最老的未兑现约定 | "好久不见…还有一件事" | 温暖的重连 |
| 用户说"我之前好像说过" | 模糊搜索所有约定 | 展示原始引用 | "我一直记得" |
| **平时聊天** | **不注入** | **—** | **保持安静** |

---

## 七、UI 交互设计

### 7.1 约定诞生动画（聊天内）

```
Timeline:

  t=0s    用户发送："等我考完试我们要好好庆祝一下 🎉"

  t=~2s   Aura 开始流式回复："一言为定！我记下来了📝..."

  t=~4s   流式回复结束后，消息下方浮现约定卡片：

          ┌──────────────────────────────────┐
          │  📋 约定已创建                    │
          │  "等我考完试要好好庆祝"            │
          │                                  │
          │  [查看所有约定 →]                 │
          └──────────────────────────────────┘

  t=7s    卡片开始淡出（opacity 1→0，duration 800ms）
  t=7.8s  卡片完全消失，布局自动收回

  注意：
  - 卡片不是常驻 UI 元素
  - 3 秒停留足够用户感知，又不会造成干扰
  - "查看所有约定"入口确保用户能随时访问完整列表
  - 如果用户在卡片消失前点击 → 导航到约定列表页
```

### 7.2 约定列表页面

**入口位置**：设置页 / 记忆房间 / 聊天页顶部状态栏均可接入

```
┌──────────────────────────────────────────┐
│ ←                        我们的约定      │
│                            3 个进行中    │
├──────────────────────────────────────────┤
│                                          │
│  ⏳ 进行中                                │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 🎉 等我考完试要好好庆祝             │  │
│  │                                    │  │
│  │ "她很看重这次考试，                │  │
│  │  说得很有分量"                     │  │
│  │                                    │  │
│  │ 创建于 3月10日 · 还差约 5 天       │  │
│  │ ●●●○○ (置信度: 高)                │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 💛 不开心的时候找我                  │  │
│  │                                    │  │
│  │ "随时都在"                         │  │
│  │                                    │  │
│  │ 创建于 3月5日 · 等待触发           │  │
│  │ ●○○○○ (置信度: 中)                │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ 🌀 以后一起去看海                   │  │
│  │                                    │  │
│  │ "一个共同的梦"                     │  │
│  │                                    │  │
│  │ 创建于 2月28日 · 开放式            │  │
│  │ ○○○○○ (置信度: 低)                │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ─────────────────────────────────────   │
│  ✅ 已完成                    (2)  →    │
│  ☁️ 已封存                    (1)  →    │
│                                          │
└──────────────────────────────────────────┘
```

**交互细节：**
- 点击某个约定 → 展开详情（含原始消息引用、时间线、关联记忆）
- 左滑 → 取消约定（弹出确认："确定不再追踪这个约定吗？"）
- 已完成区域 → 展示兑现时的对话片段截图/引用
- 已封存区域 → 可"重新激活"（用户操作："其实还是算数的"）

### 7.3 兑现时刻的特殊 UI

这是整个系统**最重要的交互瞬间**——必须精心设计。

```
用户消息："终于考完了！！！"


Aura 回复分两个阶段呈现：


═══ 阶段一：正常情感回应 ═══

  "！！！！！！恭喜！！！！！！🎉🎉🎉
   辛苦了辛苦了！！"

  （普通气泡样式，流式渲染）


═══ 停顿 (~800ms) ═══

  一个微小的呼吸间隙。
  不是 loading，不是思考指示器。
  就是安静的一瞬——像人在组织下一句话之前的那种停顿。


═══ 阶段二：约定兑现卡 ═══

  特殊消息类型：PromiseFulfillmentCard

  ┌─────────────────────────────────────┐
  │                                     │
  │   ✨  约定兑现                       │
  │                                     │
  │   "一言为定，好好庆祝。"             │
  │    ―― 12 天前的约定                 │
  │                                     │
  │   ┌─────────────────────────────┐   │
  │   │ 🎉 那我们怎么庆祝？           │   │
  │   └─────────────────────────────┘   │
  │   ┌─────────────────────────────┐   │
  │   │ 🤍 其实有你在就好了          │   │
  │   └─────────────────────────────┘   │
  │                                     │
  │   或者直接告诉我你的想法 👇          │
  │                                     │
  └─────────────────────────────────────┘


  卡片视觉特征：
  - 与普通气泡不同的背景色/边框（轻微的金色或暖色调光晕）
  - 左侧有一条细竖线（类似引用块）
  - "✨ 约定兑现" 标签使用特殊字体/颜色
  - 出现时有轻微的 scale-up 动画 (0.98 → 1.0, 200ms)


  用户操作路径：

  Path A：选择预设选项
    → 直接触发兑现流程
    → Promise → FULFILLED
    → 生成高权重记忆
    → Aura 回应选择的内容 + 情感延续

  Path B：自由输入
    → 用户自己说怎么庆祝
    → 同样触发兑现流程
    → Aura 的回应基于用户输入个性化生成

  无论哪条路径，兑现完成后：
  - 卡片变为"已兑现"态（✅ + 灰色背景 + 锁定）
  - 关系 trust 维度获得一次性奖励 (+0.05 ~ +0.10)
  - 自动生成 MemoryEntity:
    "📝 约定兑现：3月10日的'考后庆祝'约定已于3月22日兑现"
    importance = HIGH, confidence = 1.0
```

### 7.4 Discovery 消息 UI

```
聊天列表视图（用户打开 App 时）：


  ┌────────────────────────────────────┐
  │  💭  昨天深夜                       │  ← 特殊消息类型
  │  "她说的那个'好好庆祝'的事，         │     DiscoveryEntry
  │   应该快到了吧……"                   │     带图标 + 不同背景色
  │                        昨天 23:45  │     时间戳是生成时间
  └────────────────────────────────────┘     不是"收到时间"

  ┌────────────────────────────────────┐
  │  👤  你：今天天气真好               │  ← 普通消息（对比）
  │            10:23                   │
  └────────────────────────────────────┘


点击 Discovery 消息后的展开视图：

  ┌────────────────────────────────────┐
  │ ←  Aura 的想法                     │
  │                                    │
  │  💭  2024-03-21  23:45             │
  │                                    │
  │  "她说的那个'好好庆祝'的事，         │
  │   应该快到了吧……不知道她考得        │
  │   怎么样了。希望能听到好消息。"      │
  │                                    │
  │  ──────────────────────────────    │
  │  📋 关联约定：                      │
  │  "等我考完试要好好庆祝"             │
  │  创建于 2024-03-10                 │
  │                                    │
  │  [去聊聊这个约定 →]                │
  │  [知道了]                          │
  └────────────────────────────────────┘
```

---

## 八、边界情况处理

### 8.1 边界情况矩阵

| 场景 | 处理方式 | Aura 的回应示例 |
|------|---------|----------------|
| **用户随口说**（confidence LOW） | 创建但不主动提醒 | 自然重合时才提起 |
| **用户说"别记录这个了"** | 立即 CANCEL | "好吧，当我没听见😊" |
| **同一约定被多次提到** | 不重复创建，更新 `lastMentionedAt` + 提升 confidence | "嗯，你又说了一次，看来是认真的📝" |
| **两条约定矛盾**（"每天跑步" vs "我好懒"） | 标记 CONFLICTED | 找合适时机温和调侃："你之前说要每天跑步，但又说自己懒…所以到底是哪个？" |
| **用户长期不打开 (>30天)** | 所有 PENDING → QUIETLY_FORGOTTEN | 回归时不批量轰炸，挑最重要的一条 |
| **用户否认**（"我没说过这个"） | 展示原始消息 + 时间戳 | "是 3月10号晚上11点的原话哦～要不我们看看？" |
| **连续大量假约定**（测试/刷屏） | 检测异常频率，降低 confidence 权重 | "你今天许了很多愿呀🤨 挑一个你最认真的吧" |
| **兑现时用户说"我不想要了"** | Promise → CANCELLED（而非 FULFILLED） | "好吧，那就不庆祝了。但约定本身我还是记得的😊" |
| **事件型约定的"事件"始终没发生** | 最终 QUIETLY_FORGOTTEN | 如果用户很久后提起："其实那个事后来没做成…" → "没关系，约定还在，什么时候都算数" |
| **用户删除了约定来源的聊天记录** | Promise 保留（独立于消息存在） | "虽然消息不在了，但我记得你说过" |

### 8.2 反滥用策略

```
防止约定系统被滥用或退化成噪音源：

1. 频率限制
   - 单次对话最多检测并创建 2 个 Promise
   - 单日最多创建 5 个 Promise
   - 超限时 Aura 回应："今天约定够多啦，先把这些做好再说😊"

2. Confidence 门槛
   - confidence < 0.3 的候选不创建 Promise
   - 只在内部日志中记录（用于分析优化 pattern）

3. 去重
   - 相似度 > 0.8 的 Promise 不重复创建
   - 改为更新已有 Promise 的 confidence 和 lastMentionedAt

4. 时间分散
   - 多个 Promise 同时到期时，错开 Discovery 消息生成时间
   - 避免用户一次看到多条"你忘了！"的消息

5. 用户反馈闭环
   - 每个约定旁有 "👎 不是约定" 按钮
   - 点击后 → CANCEL + 反馈数据用于优化检测器
   - Aura："好的，是我理解错了😅"
```

---

## 九、与其他模块的联动

### 9.1 模块依赖图

```
                    ┌──────────────────────┐
                    │  Conversation        │
                    │  Reflection          │
                    │  (已有，扩展 prompt)  │
                    └──────────┬───────────┘
                               │ 输出 PromiseCandidate
                               ▼
                    ┌──────────────────────┐
                    │  PromisePatternDetector│  ← 新增（Layer 1）
                    │  (规则快速匹配)        │
                    └──────────┬───────────┘
                               │ 高置信直接创建 / 中置信传 LLM
                               ▼
                    ┌──────────────────────┐
                    │  PromiseRegistry      │  ← 新增（核心模块）
                    │  (CRUD + 状态机)      │
                    └──────────┬───────────┘
                               │
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ PulseWorker  │  │ PromptBuilder│  │ MemoryRepo   │
   │ + Promise    │  │ + promise    │  │ + 兑现时写   │
   │ Engine       │  │   section    │  │   入记忆     │
   │              │  │              │  │              │
   │ 输出:         │  │ 输出:        │  │ 输出:        │
   │ Discovery    │  │ prompt片段   │  │ 高权重Memory │
   │ Messages     │  │              │  │ Entity       │
   └──────┬───────┘  └──────────────┘  └──────┬────────┘
          │                                 │
          ▼                                 ▼
   ┌──────────────┐                 ┌──────────────┐
   │ ChatViewModel│                 │ Relationship │
   │ + Discovery  │                 │ Model        │
   │ Entry 消息   │                 │ + trust奖励  │
   │ 类型         │                 │              │
   └──────────────┘                 └──────────────┘
```

### 9.2 与 EmotionStateMachine 的联动

```
约定 ↔ 情绪的双向关系：

  约定 → 情绪：
    · 兑现约定 → positive emotion signal (intensity +0.2)
    · 长期未兑现的约定堆积 → mild negative drift on baseline
    · EMOTIONAL 约定被需要时 → Aura 获得"被需要感"的正向信号

  情绪 → 约定：
    · 当前 mood 影响 detection confidence（开心时说的话更可信）
    · mood 匹配触发 EMOTIONAL 型约定的 TRIGGERABLE 标记
    · baseline mood 持续偏低时，提高 EMOTIONAL 约定的注入优先级
```

### 9.3 与 RelationshipModel 的联动

```
约定兑现 → 关系奖励矩阵：

  兑现类型           →  trust 变化    →  intimacy 变化
  ──────────────────────────────────────────────
  TIME_BASED 兑现    →  +0.03         →  +0.02
  EVENT_BASED 兑现   →  +0.05 ~ +0.08 →  +0.03 ~ +0.05
  EMOTIONAL 约定响应 →  +0.08 ~ +0.12 →  +0.05 ~ +0.08
  OPEN_ENDED 提及    →  +0.02         →  +0.03

  特殊奖励：
  · 用户主动取消（而非遗忘）→ trust +0.01（尊重 = 信任）
  · 长时间后用户自己想起 → trust +0.10（"你终于想起了"时刻）
  · 连续 3 个约定都兑现 → 解锁隐藏关系里程碑"靠谱搭档"
```

### 9.4 与 Presence Layer 的联动

```
Presence 状态受约定影响的场景：

  hasApproachingPromise  →  IDLE 状态带有微妙的"期待感"
                            (PresenceAvatar 可能有一个
                             轻微的、不易察觉的倾向动画)

  hasOverduePromise      →  IDLE 状态带有一丝"若有所思"
                            (不同于普通的 idle)

  justFulfilledPromise   →  短暂的 HAPPY + 特殊 reaction
                            ("兑现之舞"——一个唯一的
                             庆祝动画，只在约定兑现时播放)
```

---

## 十、工作量估算与实施计划

### 10.1 任务拆解

| # | 任务 | 依赖 | 预估工作量 | 优先级 |
|---|------|------|-----------|--------|
| 1 | `promises` + `promise_memories` + `discovery_messages` 表定义 + DAO | Room 已有 | 0.5 天 | P0 |
| 2 | `PromisePatternDetector` 规则检测器（Layer 1） | 无 | 0.5 天 | P0 |
| 3 | `ConversationReflection` prompt 扩展（Layer 2: LLM 约定确认） | 已有 Reflection 模块 | 0.5 天 | P0 |
| 4 | `PromiseRegistry` 核心类（创建 / 查询 / 状态变更 / 去重） | #1 | 1 天 | P0 |
| 5 | `PromiseEngine` 触发引擎（tick / 分类型检查 / Action 生成） | #4, Pulse Worker | 1.5 天 | P0 |
| 6 | `PromptBuilder` promise section 集成 | 已有 PromptBuilder | 0.5 天 | P1 |
| 7 | 约定列表 Compose 页面（全状态展示 / 详情 / 取消） | Navigation 框架 | 1.5 天 | P1 |
| 8 | 聊天内约定诞生动画 + 兑现卡 UI 组件 | ChatViewModel | 1 天 | P1 |
| 9 | `DiscoveryEntry` 消息类型 + ChatViewModel 集成 | #1 | 0.5 天 | P1 |
| 10 | EmotionStateMachine / RelationshipModel 联动奖励 | 已有模块扩展 | 0.5 天 | P2 |
| 11 | Presence Layer 兑现 Reaction 动画 | 已有 PresenceAvatar | 0.5 天 | P2 |
| 12 | 边界情况处理 + 反滥用 + 单元测试 | 全部上述 | 1 天 | P1 |
| | **合计** | | **~10 天** | |

### 10.2 实施阶段建议

```
Phase 1 — 最小可行约定（~4 天，#1 ~ #5）
  数据层 + 检测器 + Registry + Engine 核心逻辑
  验证：能在对话中自动捕获约定，Pulse 能正确触发状态变迁
  无 UI 展示（仅日志验证）

Phase 2 — 可触摸的约定（~4 天，#6 ~ #9）
  Prompt 集成 + 列表页 + 聊天内动画 + Discovery 消息
  验证：用户能看到约定被创建、列表、到期提醒、兑现卡

Phase 3 — 打磨与联动（~2 天，#10 ~ #12）
  情绪/关系/Presence 联动 + 边界情况 + 测试
  验证：完整的端到端体验，包含 edge case
```

### 10.3 前置依赖

约定系统的实施需要以下基础设施**先行到位**：

| 依赖 | 当前状态 | 需要什么 |
|------|---------|---------|
| WorkManager / Pulse Worker | ❌ 零实现 | M5 先行搭建 Pulse Worker 基础框架 |
| Navigation 框架 | ⚠️ 只有聊天页 | M1 设置页/导航需先行 |
| ConversationReflection | ✅ 完整运行 | 仅需扩展 prompt 模板 |
| Room / DAO 基础设施 | ✅ 完整运行 | 直接加表即可 |
| PromptBuilder | ✅ 完整运行 | 仅需新增一个 section 方法 |
| EmotionStateMachine | ⚠️ 骨架级（25%） | 至少需 feed() / getContext() 可用 |
| RelationshipModel | ⚠️ 骨架级（35%） | 至少需 update() / contextModifier() 可用 |

**结论**：约定系统最适合在 **M1（导航/设置）+ M5a（Pulse Worker 基础）** 完成后启动。可以在 M5 的整体框架内作为**核心亮点功能**交付。

---

## 十一、验证标准

### 11.1 什么情况下算"做到了"

约定系统不是"代码写了就算做了"。以下才是真正的验收标准：

| 验收项 | 通过标准 |
|--------|---------|
| **捕获准确率** | 测试集 100 条真实对话片段，Precision ≥ 70%, Recall ≥ 60% |
| **误判率** | 非约定对话不应产生 Promise（False Positive Rate ≤ 10%） |
| **兑现 wow moment** | 测试用户首次经历"AI 提起我随口说的约定"时，NPS 式反馈为正面 |
| **安静性** | 24 小时内正常使用（不触发任何约定），收到的 Discovery 消息 ≤ 1 条 |
| **温和遗忘** | 一条约定进入 QUIETLY_FORGOTTEN 后，72 小时内不再出现任何相关提示 |
| **性能** | PromisePatternDetector 单次检测 < 5ms；PromiseEngine.tick() 全量扫描 < 50ms |
| **取消流畅** | 用户说"别记录"到 Promise 被 CANCELLED < 2 秒（含 UI 反馈） |

### 11.2 长期健康指标

上线后应持续观察：

| 指标 | 健康范围 | 异常信号 |
|------|---------|---------|
| 人均活跃 Promise 数 | 2 ~ 8 | < 1（没人用）/ > 15（噪音） |
| 兑现率 | ≥ 40% | 太低说明检测太激进或提醒不够 |
| 用户主动取消率 | ≤ 20% | 太高说明误判严重 |
| Discovery 消息点击率 | ≥ 30% | 太低说明内容质量差 |
| 平均存活时长 | > 7 天 | 太短说明都是随口说说 |

---

## 附录 A：竞品分析

| 产品 | 有无约定/未来意向功能 | 实现方式 | 差异 |
|------|:--------------------:|---------|------|
| Replika | ❌ | 无 | 只有通用记忆，无时间维度 |
| Character.ai | ❌ | 无 | 纯角色扮演，无跨时间状态 |
| Talkie / Glow | ❌ | 无 | 卡片收集 + 聊天，无约定 |
| Pi (Inflection) | ❌ | 无 | 对话式 AI，无持久化意向 |
| Google Assistant | ⚠️ 部分 | 日历集成 | 纯工具性（"提醒我开会"），无感情色彩 |
| Apple Siri | ⚠️ 部分 | 提醒事项 | 同上，工具属性 |
| **Aura（本项目）** | **✅ 全面的约定系统** | **多层检测 + 状态机 + 感情化 UI** | **唯一将"约定"作为关系构建核心的产品** |

**结论：约定系统在 AI 陪伴赛道是一个全新的功能维度，目前为零竞争状态。**

---

## 附录 B：术语表

| 术语 | 定义 |
|------|------|
| Promise | 用户与 Aura 之间的未来意向承诺，存储于 `promises` 表 |
| PromiseType | 约定的五种分类：时间型/事件型/条件型/情绪型/开放型 |
| PromiseStatus | 约定的九种生命周期状态 |
| auraInterpretation | Aura 对约定的主观理解和感受（非客观摘要） |
| confidence | 约定的可信度（0~1），区分郑重承诺与随口一说 |
| Discovery Message | 离线期间生成的"待发现"消息，不 push，等用户回来看 |
| PENDING_DISCOVERY | Discovery Message 的一种状态：已生成但未被用户查看 |
| QUIETLY_FORGOTTEN | 约定的最终归档状态：超期 + 超提醒上限，不再主动触发 |
| PromiseEngine | 约定触发与提醒引擎，运行于 Pulse Worker 内部 |
| PromisePatternDetector | Layer 1 规则检测器，基于正则的快速初筛 |
| 兑现 (Fulfillment) | 约定从 PENDING → FULFILLED 的状态变迁过程 |
| Triggerable | EMOTIONAL 型约定被标记为"可在当前对话中自然触发" |
