# Aura 双轨智能体架构方案（Dual-Mind Architecture）

> Last updated: 2026-06-15
>
> Scope: Aura Android app，云端对话体（Conversational Mind）+ 本地陪伴体（Continuous Presence）的分层设计。
>
> 关联文档：[`roadmap.md`](../roadmap.md) · [`architecture.md`](../architecture.md) · [`koog-android-integration.md`](../koog-android-integration.md) · [`on-device-qwen-mnn-research.md`](../on-device-qwen-mnn-research.md) · [`agent-capability-server-plan.md`](./agent-capability-server-plan.md) · [`insight-driven-product.md`](./insight-driven-product.md)

---

## 0. 范围

本文档记录 Aura 把"云端对话体 / 本地陪伴体"分成两条子系统的设计。`KoogAgentFactoryImpl.create()` 仍是单一入口，按 `LlmConfig.provider` 路由到 `KoogPromptExecutorWrapper`（云端）或 `ReactiveCompanion`（本地）。

- 云端走 Koog + Anthropic Messages 兼容接口（GLM / Kimi / Anthropic）。
- 本地走 MNN + Qwen，支持文本与 Vision 多模态（2026-06-17 PR B 后）。
- 工具调用、Vision、结构化反思、记忆召回等能力以**云端为主**；本地路径当前不支持工具调用（`BuiltPrompt.allowTools` 被读但本地走 `allowTools=false`，打 warn 日志）。
- 陪伴体（`DreamLoopWorker` / `LocalQwenExecutor` / MoodDrift 等）由 `core/presence/runtime/` 独立承担，不在主对话路径上。

> **2026-06-17 重新评估**：原 §1.4 把"本地 = 隐私差异化"作为产品叙事主轴的论证已撤掉。Reasoning：云端成本可控、隐私作为产品卖点缺乏可验证的差异化，且 `KoogAgentWrapper` 4 方法契约对齐后云端 / 本地已经是合理的 Provider 切换形态，不需要拆双子系统。Roadmap §8 / §9 标记"已重新评估"。

---

## 1. 目标与背景

### 1.1 现状与问题

#### 工程层面

当前架构（2026-06 实际代码）：

```
用户消息 → CompanionRuntime
              ↓
        KoogAgentFactory.create()
              ↓
       二选一:  KoogPromptExecutorWrapper  /  LocalQwenAgentWrapper
              ↓                                 ↓
            云端 (Koog + Anthropic)         本地 (MNN + Qwen)
```

`LocalQwenAgentWrapper` 在两个地方是错的：

1. **职能错位** —— 它是 `KoogAgentWrapper` 的"轻量替代"，但端云是**两个不同职能**，不是"同一个职位的两个候选人"
2. **能力残缺** —— 直接抛 `UnsupportedOperationException`（Vision / 结构化），本应属于对话体的功能被错误地接到了本地路径

参见 [`on-device-qwen-mnn-research.md`](../on-device-qwen-mnn-research.md) 第 1 节："不推荐把端侧 Qwen 直接替代现有 GLM/Kimi 云端模型"，本文是该结论的**架构落地**。

#### 产品层面

本设计稿不预设产品叙事主轴。Aura 当前的差异化主要在**长期记忆 + 情绪 / 关系 / presence 层**这一组功能组合上（详见 [`insight-driven-product.md`](./insight-driven-product.md)），与本地 / 云端的拆分没有强绑定关系。

云端 / 本地作为两条独立子系统，主要价值在工程层面：
- **Provider 可切换**（GLM / Kimi / Anthropic / Local Qwen），用户自选。
- **本地路径离线可用**、不依赖网络、不产生 token 成本。
- **陪伴体**（Dream Loop / Inner Monologue / Reactive Respond）独立于主对话路径，不与云端 agent 强耦合。

> **2026-06-17 重新评估**：早期本文档把"本地 = 隐私 moat"作为产品卖点写过，撤掉。云端 token 成本可控（实际在 M3 阶段验证过），隐私差异化论证缺乏可验证的对照实验。叙事主轴回到产品功能本身，参见 §1.2 注释。

### 1.2 核心判断

| 维度 | 响应面（云端） | 觉察面（本地） |
|------|------------|------------|
| 角色 | 对话体（Responsive Mind） | 陪伴体（Continuous Awareness） |
| 何时在场 | 用户发消息时 | 永远在（包括用户不在时） |
| 主要能力 | 工具调用 / Vision / 长上下文 / 结构化反思 | 持续心跳 / 即时闲聊 / dream loop / mood drift / insight 提取 |
| 资源约束 | 按次计费、依赖网络 | 电池、续航、存储 |
| 类比 | "嘴和脑" | "心和身" |

**本地不是云的降级版**。把本地当成"低配版云"会推出"按场景路由"这种伪命题。两个 LLM 各自有独立的 system prompt、输入数据、输出 schema，**不该共用 wrapper**。

### 1.3 目标

短期（Phase 0–2）：
- 觉察面骨架可运行，能在锁屏后启动 dream loop
- Auto Memory 文件系统层可写入
- Inner Monologue 心跳可观测（续航、独白质量、mood 漂移）
- 第一个 insight 卡片可在主页 / 聊天页被动呈现

中期（Phase 3–4）：
- Reactive Companionship 可承接用户即时闲聊
- 三层（L1/L2/L3）协同工作
- dream loop 能从对话中提取长期记忆
- Weekly Insight 自动汇总并主动推送

长期（Phase 5+）：
- 用户感知到 Aura "在为他工作"（不只是被动响应）
- 端侧 KV-cache 续写技术成熟，让 Aura 形成"自我感"延续
- 远端 Agent Server 主要服务"信息回写到记忆"这条主轴



> **Aura 不做"会聊天的 App"，做"长期认识你的 AI"。**

#### 1.4.1 为什么"第二大脑 / 数字孪生"是 Aura 的最佳叙事

---

## 2. 双子系统划分

### 2.1 响应面 / 对话体（Responsive Mind，云端）

> **对外办事**：你提问、查东西、要做决定时调它。

**职责清单**：
- 用户发起消息时的主对话路径
- 工具调用（search_memory、search_records、get_weather、create_reminder、MCP 工具）
- Vision（图片理解）
- 长上下文总结（用户上传大文档 / 历史超过 2k token）
- 用户显式触发的深度反思（"你认真想想"）
- 消费觉察面写出的 insight 摘要（"本周 Aura 注意到..."）作为 prompt 上下文

**实现栈**（**当前代码完全保留**）：
- Koog `AIAgent` + `MultiLLMPromptExecutor`
- `AnthropicMessagesLLMClient`
- `AgentToolRegistry`（9 个内置工具 + MCP）
- `ConversationContextBuilder`（prompt 组装）

**简化点**（未来做）：
- `LlmConfig.provider` 字段从 "GLM / LOCAL_QWEN 二选一" 改为只描述对话体（"GLM / Kimi / Anthropic"）
- `ConversationReflection` 从每轮触发改为阈值触发 + 用户显式触发

### 2.2 觉察面 / 陪伴体（Continuous Awareness，本地）

> **对内懂事**：观察、记录、整合、提醒。Aura 的"长期认识你"全部在这一面发生。

三层架构，每层独立运行：

#### L1 — Inner Monologue（持续心跳）

- **触发**：App 在前台时持续运行
- **频率**：0.5 Hz tick，但 85% tick 是 no-op（省电），真正生成约 0.05–0.1 Hz
- **核心机制**：KV-cache 续写
  - 模型状态保存在 CompanionKVCache
  - 每次 tick 从上次中断处继续生成 ~30 tokens
  - 形成"自我感"的延续，不是每次从 0 开始
- **产物**：
  - `inner_state.md` 文件滚动追加
  - mood 数值小幅漂移
  - PresenceController 状态持续更新（无用户事件也变化）

#### L2 — Dream Loop（闲时整合）

- **触发条件**（满足任一）：
  - App 锁屏超过 5 分钟
  - 充电中（可选）
  - 累积未处理 message ≥ 10 条
  - 距上次 dream run 超过 6 小时
- **核心机制**：批量流水线
  - Stage 1: 收集（SQL，不调 LLM）
  - Stage 2: 摘要（本地 Koog `PromptExecutor.execute()`）
  - Stage 3: 分类决策（本地）
  - Stage 4: 写入 Auto Memory 文件
  - Stage 5: confidence < 阈值时上调云端深度反思
- **产物**：
  - `aura_memory/*.md` 文件更新
  - `memory_summaries` 表新增
  - `dream_log` 表新增

#### L3 — Reactive Companionship（即时陪伴）

- **触发**：用户消息到达，**先尝试**本地响应
- **核心机制**：单次生成 + confidence 评估
  - 零延迟（不联网）
  - 零成本（不调云端）
- **判定逻辑**：
  - confidence ≥ 阈值 → 直接本地响应
  - 检测到工具需求 → 转交对话体
  - confidence 低 → 转交对话体
- **产物**：
  - 短文本回复（≤ 150 tokens）
  - 或转交信号给 `ChatExecutor`

### 2.3 双子系统的协作

协作场景很少但很关键：

| 触发 | 觉察面（本地） | 响应面（云端） | 备注 |
|------|------------|------------|------|
| 用户消息 | L3 Reactive 尝试 | 接管 if 工具需求 / confidence 低 / Vision | 主对话路径 |
| Dream Loop 跑完 | L2 本地写入 Auto Memory + ObservationQueue | confidence 低时跑深度 reflection | Dream 不直接发消息，沉淀到 observation 池 |
| Pulse（主动关怀） | L3 本地生成文案 | 永远本地（mood / inner state 不上云） | 决定要不要推通知 |
| Weekly Insight | L2 汇总本周 mood/pattern/memory | 仅在生成精美总结时用 | 见 insight-driven-product.md §3 |
| 用户说"认真想想" | L1 标记为深度思考 | **永远上调云端** | 深度思考必须用最强模型 |
| 用户发图片 | 2026-06-17 PR B 后本地可走 vision（`submitWithImageNative`） | 默认仍走云端 GLM-5v-turbo（tool 调用 + 质量） | 本地 vision 不挂 tools，云端走完整 tool loop |
| 外部信息回写 | 接收 server 推回的总结 | server agent 拉取/总结 | 见 agent-capability-server-plan §3.4 |

**关键不变量**：
- **对话体只读陪伴体写的产物**（mood / relationship / inner state summary / observation），不写
- **陪伴体不读用户对话的中间状态**，只读最终持久化的 messages
- **Auto Memory 文件是陪伴体独占**，对话体不直接读文件，但可以通过 `memory_summaries` 表间接读到本地提炼后的摘要
- **观察（observation）必须有"为什么"、"什么时候"、"基于什么数据"三要素** —— 写入前必填，缺失即丢弃（见 insight-driven-product.md §2.3）

---

## 3. 数据流图

### 3.1 双子系统总览

```
                       用户消息
                          │
                          ▼
                  ┌─────────────────┐
                  │  ChatViewModel  │  ← 路由决策入口
                  └────────┬────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
    ┌──────────────────┐      ┌──────────────────┐
    │  对话体 (云端)    │      │  陪伴体 (本地)    │
    │                  │      │                  │
    │  Koog AIAgent    │      │  L3 Reactive     │ ← 优先级高
    │  + Tools         │      │   Coordinator    │
    │  + Vision        │      │                  │
    │  + Structured    │      │  L2 Dream        │ ← 锁屏触发
    │                  │      │   Pipeline       │
    │  ChatExecutor    │      │                  │
    │  (新增)          │      │  L1 Heartbeat    │ ← 0.5 Hz
    │                  │      │   (KV-cache 续写)│
    └────────┬─────────┘      └────────┬─────────┘
             │                         │
             │  ┌──────────────────────┘
             │  │
             ▼  ▼
      ┌──────────────────────┐
      │  Room / DataStore    │
      │  - messages          │
      │  - memories          │
      │  - memory_summaries  │
      │  - agent_state       │
      │  - mood_snapshots    │
      │  - presence_marks [新]│
      │  - dream_log      [新]│
      └──────────┬───────────┘
                 │
                 ▼
      ┌──────────────────────┐
      │  aura_memory/        │  ← 陪伴体独占
      │  (文件系统)          │
      │  - MEMORY.md (索引)  │
      │  - user_patterns.md  │
      │  - emotional_history │
      │  - skill_templates   │
      │  - inner_state.md    │
      │  - reflection_log.md │
      └──────────────────────┘
```

### 3.2 数据访问边界

| 数据 | 对话体读 | 对话体写 | 陪伴体读 | 陪伴体写 |
|------|---------|---------|---------|---------|
| `messages` | ✅ | ✅ | ✅（dream 时） | ❌ |
| `memories` | ✅（search_memory 工具） | ❌ | ✅（dream 时） | ❌ |
| `memory_summaries` | ✅ | ❌ | ✅ | ✅ |
| `agent_state` (mood/rel) | ✅ | ❌ | ✅ | ✅ |
| `mood_snapshots` | ❌ | ❌ | ✅ | ✅ |
| `presence_marks` [新] | ❌ | ❌ | ✅ | ✅ |
| `dream_log` [新] | ❌ | ❌ | ✅ | ✅ |
| `aura_memory/*.md` | ❌（间接通过 memory_summaries） | ❌ | ✅ | ✅ |
| CompanionKVCache | ❌ | ❌ | ✅ | ✅ |
| AppPreferences | ✅ | ✅ | ❌ | ❌ |

**关键约束**：
- 对话体**永远不写** mood / relationship（这是用户情绪画像）
- 陪伴体**永远不写** messages（不污染对话历史）
- 陪伴体**永远不上云** mood / inner_state 数据

---

## 4. 模块改造清单

### 4.1 删除 / 改造

| 现有 | 操作 | 原因 |
|------|------|------|
| `KoogAgentFactoryImpl.create()` 二选一 | **拆掉** | 对话体不需要"选 provider" |
| `LocalQwenAgentWrapper` 作为 `KoogAgentWrapper` | **重命名**为 `ReactiveResponder` 的一部分 | 不是 Koog agent 的替代，是陪伴体组件 |
| `LocalQwenAgentWrapper.toLocalRequest()` 两处 `throw UnsupportedOperationException` | **已删**（PR A + PR B） | structured 改 JSON 解析兜底（PR A），vision 接 MNN `submitWithImageNative`（PR B） |
| `LlmConfig.provider` 字段语义 | **改为只描述对话体**（去掉 LOCAL_QWEN 选项） | 陪伴体不在用户配置里 |
| `ConversationReflection` 每轮调一次 | **改为阈值触发 + 本地优先** | 当前是浪费 token 的过度反思 |
| `AppPreferences.llmProvider` UI 切换 | **降级为只影响对话体** | 陪伴体无 UI 开关 |

### 4.2 新增模块（陪伴体运行时）

```
app/src/main/java/com/xiaoqi/companion/
├── core/
│   ├── presence/
│   │   ├── (已有)
│   │   │   ├── PresenceController.kt
│   │   │   ├── PresenceReactionPolicy.kt
│   │   │   └── PresenceModels.kt
│   │   └── runtime/                  ← 【新】陪伴体运行时
│   │       ├── LocalQwenExecutor.kt      封装 Koog PromptExecutor, 接 MNN
│   │       ├── CompanionKVCacheStore.kt  KV-cache 持久化
│   │       ├── PresenceHeartbeat.kt      L1 心跳协程
│   │       ├── DreamPipeline.kt          L2 dream 流水线
│   │       ├── ReactiveResponder.kt      L3 即时响应 (原 LocalQwenAgentWrapper 重命名)
│   │       ├── ConfidenceGate.kt         决策: 够用 / 上调云端 / 拒绝
│   │       └── MoodDrift.kt              mood/relationship 数值漂移
│   │
│   └── local/                       ← 【缩】只剩引擎 + 任务抽象
│       ├── LocalQwenEngine.kt       (保留)
│       ├── MnnLocalQwenEngine.kt    (保留)
│       ├── NativeMnnLlmBridge.kt    (保留)
│       └── LocalQwenModelDownloader.kt (保留)
│
├── data/
│   ├── file/                        ← 【新】
│   │   └── AuraMemoryStore.kt       Auto Memory 文件系统层
│   │
│   └── db/
│       └── CompanionDatabase.kt     (改) 新增 presence_marks / dream_log 表
│
└── (work/)                          ← 【新】WorkManager 任务
    ├── DreamLoopWorker.kt           锁屏/累积/周期触发
    ├── AutoMemoryFlushWorker.kt     定期 flush 内存中的 memory
    ├── MoodDecayWorker.kt           离线时长导致 mood 自然衰减
    └── PresenceHeartbeatStarter.kt  App 启动时启动前台协程
```

### 4.3 保留模块（对话体不动）

`core/companion/`、`core/llm/`、`core/tools/`、`core/prompt/`、`data/repository/` 全部保留，等 Phase 1（对话体拆分）再做局部调整。

---

## 5. 数据层

### 5.1 Auto Memory 文件系统布局

参考 Claude Code 的 [Auto Memory 机制](https://code.claude.com/docs/en/memory)：

```
/data/data/com.xiaoqi.companion/
└── files/
    └── aura_memory/
        ├── MEMORY.md                   # 索引 (启动时 hot load, 前 200 行 / 25KB)
        ├── user_patterns.md            # 用户习惯 (晚睡/通勤/工作节奏)
        ├── recurring_topics.md         # 高频话题
        ├── emotional_history.md        # 情绪轨迹 (按周滚动)
        ├── skill_templates.md          # 应对模板 (安抚/庆祝/道晚安)
        ├── inner_state.md              # Inner Monologue 滚动日志 (每日轮转)
        └── reflection_log.md           # 历史 reflection 摘要
```

**MEMORY.md 示例结构**：

```markdown
# Aura Auto Memory Index

Last updated: 2026-06-14 03:42

## Active topics
- 用户最近追的剧: 《暗黑 4》(updated 2026-06-13)
- 用户当前项目: 端云双轨架构 (updated 2026-06-14)

## User patterns
- 习惯晚睡, 凌晨 1–3 点最活跃
- 工作日通勤时间 09:00–10:00
- 周末喜欢户外, 关注天气

## Emotional history
- 见 emotional_history.md (近 7 天)

## Skill templates
- 见 skill_templates.md (12 个模板)

## Recent reflections
- 见 reflection_log.md (近 30 次)
```

**Auto Memory 写入决策**（仿 Claude Code 的"我自己判断写什么"）：

```kotlin
class AuraMemoryWriter(
    private val localExecutor: LocalQwenExecutor,
) {
    suspend fun shouldWrite(message: Message): WriteDecision {
        // 本地 LLM 跑个 ~200ms 的小分类
        val result = localExecutor.execute(
            prompt = classifyPrompt(
                task = "decide_if_worth_remembering",
                message = message.content,
                existingMemory = AuraMemoryStore.currentIndex(),
            ),
            temperature = 0.2f,
            maxTokens = 50,
        )
        return WriteDecision.parse(result)
    }
}
```

**触发频率**（仿 [Generative Agents](https://arxiv.org/abs/2304.03442) 的阈值触发）：
- **不每条消息都问"该不该写"**
- 攒够 10 条新消息 / 距上次写入 6 小时 / 情绪变化超阈值 → 批量处理
- 用户消息长度 ≤ 5 句 → 直接跳过分类

### 5.2 Room 新增表

```kotlin
// 新增: 陪伴体触发的事件流
@Entity(tableName = "presence_marks")
data class PresenceMarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val eventType: String,  // APP_FOREGROUND / APP_BACKGROUND / SCREEN_ON / SCREEN_OFF / USER_ACTIVE
    val moodSnapshot: String?,  // JSON: {mood, intensity, relationshipLevel}
)

// 新增: dream loop 历史, 用于追溯
@Entity(tableName = "dream_log")
data class DreamLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerType: String,  // LOCK / ACCUMULATION / PERIODIC / BATTERY
    val startedAt: Long,
    val finishedAt: Long,
    val messagesProcessed: Int,
    val memoryWrites: Int,        // 写入了几个文件
    val cloudEscalations: Int,    // 上调云端几次
    val summary: String,          // 本次 dream 的一句话总结
)
```

---

## 6. 运行时结构（基于 Koog 的薄封装）

### 6.1 为什么用 Koog 而不是自建

考虑过的选项：

| 选项 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| 用 Koog `PromptExecutor` | 已是项目统一 LLM 抽象；维护成本 0 | 部分能力（agent loop）不用 | ✅ 采用 |
| 自建 sealed interface runtime | 概念对齐 | 500+ 行新代码；与 Koog 概念重叠 | ❌ 过度设计 |
| LangChain4j | 提供 chat memory 等 | APK +5MB；概念错位 | ❌ 不适合 |
| Spring AI | 全功能 | 不支持 Android | ❌ 不适用 |

**核心思路**：
- **Koog 的 `PromptExecutor` / `LLMClient` / `MultiLLMPromptExecutor` 是 LLM 抽象层**，云端本地都用
- **Koog 的 `AIAgent` / Strategy / ToolRegistry 只在对话体用**
- **KV-cache 续写是 MNN 引擎原语，不经过 Koog**

### 6.2 抽象层一条线

```
MnnLocalQwenEngine (MNN 推理)
       ↓ 实现
LocalQwenExecutor (封装 Koog PromptExecutor)
       ↓ 调用
三个场景:
  - PresenceHeartbeat (绕过 Koog, 直通 MNN continueFromCache)
  - DreamPipeline (多次 execute() 顺序调用)
  - ReactiveResponder (streamingCompletion 单次调用)
```

### 6.3 关键 API 签名

```kotlin
// core/presence/runtime/LocalQwenExecutor.kt

interface LocalQwenExecutor {
    /** 单次 LLM 调用, 返回完整结果 */
    suspend fun execute(
        prompt: Prompt,
        temperature: Float = 0.7f,
        maxTokens: Int = 500,
    ): LocalLlmResult

    /** 流式 LLM 调用, 供 Reactive 闲聊 */
    fun streamingCompletion(
        prompt: Prompt,
        temperature: Float = 0.8f,
    ): Flow<String>

    /** KV-cache 续写, 不经过 system prompt, 直通 MNN */
    suspend fun continueFromCache(
        cache: CompanionKVCache,
        maxTokens: Int = 50,
    ): LocalLlmResult

    /** 资源管理 */
    suspend fun preload()
    suspend fun release()
    val isReady: StateFlow<Boolean>
}

data class LocalLlmResult(
    val text: String,
    val confidence: Float,    // 0.0~1.0, 用于决策
    val tokensUsed: Int,
    val durationMs: Long,
)
```

```kotlin
// core/presence/runtime/PresenceHeartbeat.kt

class PresenceHeartbeat @Inject constructor(
    private val executor: LocalQwenExecutor,
    private val kvCacheStore: CompanionKVCacheStore,
    private val moodDrift: MoodDrift,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            while (isActive) {
                tick()
                delay(2_000)  // 0.5 Hz
            }
        }
    }

    private suspend fun tick() {
        // 85% tick 是 no-op, 真正生成约 0.05–0.1 Hz
        if (Random.nextDouble() > 0.15) return

        val result = executor.continueFromCache(
            cache = kvCacheStore.current(),
            maxTokens = 30,
        )
        moodDrift.applyInnerThought(result.text)
    }
}
```

```kotlin
// core/presence/runtime/DreamPipeline.kt

class DreamPipeline @Inject constructor(
    private val executor: LocalQwenExecutor,
    private val messageDao: MessageDao,
    private val memoryStore: AuraMemoryStore,
    private val chatExecutor: ChatExecutor,  // 偶尔上调云端
) {
    suspend fun run(trigger: DreamTrigger): DreamResult {
        // Stage 1: 收集
        val messages = messageDao.since(lastDreamAt())
        if (messages.isEmpty()) return DreamResult.NoOp

        // Stage 2: 摘要 (本地)
        val summary = executor.execute(
            prompt = summarizePrompt(messages),
            temperature = 0.3f,
            maxTokens = 500,
        )

        // Stage 3: 分类决策 (本地)
        val decision = executor.execute(
            prompt = classifyPrompt(summary.text),
            temperature = 0.2f,
            maxTokens = 100,
        )

        // Stage 4: 写入文件系统
        val writes = memoryStore.applyDecision(decision.text, summary.text)

        // Stage 5: 决策是否上调云端
        return if (decision.confidence < 0.6f) {
            chatExecutor.deepReflect(messages)
            DreamResult.EscalatedToCloud(writes)
        } else {
            DreamResult.Done(writes)
        }
    }
}
```

```kotlin
// core/presence/runtime/ReactiveResponder.kt

class ReactiveResponder @Inject constructor(
    private val executor: LocalQwenExecutor,
    private val chatExecutor: ChatExecutor,
) {
    suspend fun respond(userMessage: String, mood: Mood): ReactiveResult {
        // Stage 1: 检测是否需要工具
        if (detectToolNeed(userMessage)) {
            return ReactiveResult.EscalateToCloud(userMessage, reason = "needs_tools")
        }

        // Stage 2: 本地流式生成
        val stream = executor.streamingCompletion(
            prompt = reactivePrompt(userMessage, mood),
            temperature = 0.8f,
        )

        // Stage 3: 流式首 token 延迟 / 长度启发式 confidence
        val confidence = stream.firstTokenLatencyMs.let { if (it < 300) 0.9f else 0.5f }

        return if (confidence < 0.5f) {
            ReactiveResult.EscalateToCloud(userMessage, reason = "low_confidence")
        } else {
            ReactiveResult.StreamHere(stream)
        }
    }

    private fun detectToolNeed(message: String): Boolean {
        val keywords = listOf("天气", "提醒", "搜索", "查找", "设置")
        return keywords.any { message.contains(it) }
    }
}
```

### 6.4 KV-cache 续写（MNN 直通）

KV-cache 续写是 MNN 引擎原语，**不经过 Koog**：

```kotlin
// core/presence/runtime/CompanionKVCacheStore.kt

class CompanionKVCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheFile: File = File(context.filesDir, "kv_cache/inner_state.bin")

    fun current(): CompanionKVCache {
        // 从本地文件加载, 或初始化新 cache
        return if (cacheFile.exists()) {
            CompanionKVCache.deserialize(cacheFile.readBytes())
        } else {
            CompanionKVCache.createEmpty()
        }
    }

    fun save(cache: CompanionKVCache) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeBytes(cache.serialize())
    }

    fun reset() {
        cacheFile.delete()
    }
}
```

**设计意图**：维持"自我感"的连续性。今天想的是"用户最近心情不好"，明天接着这个上下文继续想，而不是每次从 0 开始。**24h 自动 reset 一次**，避免长期偏题。

---

## 7. WorkManager / 协程调度

| 任务 | 类型 | 触发 | 频率 | 跑在哪 |
|------|------|------|------|--------|
| Inner Monologue tick | **协程**（非 Worker） | App 前台 | 持续 0.5 Hz（85% no-op） | 本地 MNN |
| Reactive Responder | **协程** | 用户消息即时 | 每次消息 | 本地 MNN |
| Dream Loop | **WorkManager** | 锁屏 / 累积 / 周期 | 一天 2–6 次 | 本地 MNN（偶尔云端） |
| Auto Memory Flush | **WorkManager** | 周期 | 每天 03:00 | 本地文件系统 |
| Mood Decay | **WorkManager** | 周期 | 每小时 | 纯计算，无 LLM |
| Pulse（主动关怀） | **WorkManager** | 周期 + 用户活跃度判断 | 每 4–8 小时 | 本地 MNN |
| Reminder Notification | **AlarmManager**（已有） | 触发式 | 用户设的提醒时间 | 本地 MNN 生成文案 |
| Conversation Reflection | **按需**（非 Worker） | 累积 / 用户触发 | 不定 | 云端 |

**WorkManager 约束处理**：

```kotlin
// DreamLoopWorker.kt
class DreamLoopWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 电量检查: < 20% 不跑
        if (BatteryHelper.isLow(context)) return Result.success()

        val trigger = DreamTrigger.fromInputData(inputData)
        val pipeline = (applicationContext as AuraApp).dreamPipeline

        return when (pipeline.run(trigger)) {
            is DreamResult.Done -> Result.success()
            is DreamResult.EscalatedToCloud -> Result.success()  // 云端调用失败也不重试
            is DreamResult.NoOp -> Result.success()
            is DreamResult.Failed -> Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DreamLoopWorker>(
                6, TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "dream_loop",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
```

---

## 8. 用户可见变化

### 8.1 Settings 页

**对话体配置**（已有，改名 / 简化）：
```
模型配置（对话体）
├── Provider:  GLM / Kimi / Anthropic
├── Model Name: _______
├── API Key: ********
├── Base URL: _______
└── 连通性检查 [按钮]
```

**陪伴体配置**（首次启动引导）：
```
陪伴体配置
├── 本地模型状态:  已下载 Qwen 1.5B (1.2 GB) / 未下载 [下载]
├── Auto Memory:    开 / 关
├── Dream Loop:     节能 / 标准 / 高频
├── Inner Monologue: 开 / 关
└── [新] 数据导出 / 删除
```

### 8.2 主页 / 聊天页体验

**现在**：用户切到本地 → 工具全没、Vision 报错、记忆反思崩

**未来**：
- 不再有"切换 provider"的概念
- 聊天时：用户消息秒级响应（本地兜底），如需深度思考 → 短暂停顿 → 云端接管
- 锁屏后：Aura 在 dream（用户能感知到吗？看下条）

### 8.3 让用户感知 dream（可选产品创新）

> Aura 主页底部一行小字："💭 奥拉在整理今天的对话..."
> 或者 status bar 一个悄悄的图标

**价值**：**信任建立**——用户知道 Aura 在为他工作，不只是被动等待。Cloud-only 的产品永远做不到这个。

**风险**：如果 dream 质量差（胡说八道 / 写错 memory），反而损害信任。**所以 PoC 阶段必须先验证独白质量**，再开这个 UI。

---

## 9. 迁移路径（Phased Rollout）

| Phase | 内容 | 周期 | 验收 |
|-------|------|------|------|
| **Phase 0 — 准备** | 重命名 + 注释，不改功能 | 1 周 | 测试全绿 |
| **Phase 1 — 拆对话体**（用户说先不拆，**TODO 占位**） | `CompanionRuntime` → `ChatExecutor`；移除二选一 | 2 周 | 云端功能不变 |
| **Phase 2 — 陪伴体骨架** | `PresenceHeartbeat` + `MoodDrift` + `DreamLoopWorker`（空跑） | 2 周 | 锁屏后 worker 能启动 |
| **Phase 3 — Auto Memory** | `AuraMemoryStore` + `MEMORY.md` 索引 + Dream 写入 | 2 周 | dream run 能写文件 |
| **Phase 4 — Reactive Companionship** | 用户消息先本地响应 + confidence 评估 + 上调云端 | 1 周 | 体验可接受 |
| **Phase 5 — 产品化** | Settings UI / 数据导出 / 数据可见可改 / dream 状态可见 UI | 持续 | 产品发布 |

**总周期**：约 8–10 周出 v2 基础版。

### Phase 0 详细步骤

1. `LocalQwenAgentWrapper` 重命名为 `ReactiveResponder`（仍作为 `KoogAgentWrapper` 实现）
2. `KoogAgentFactoryImpl` 二选一逻辑加 `@Deprecated` 注释
3. `core/presence/runtime/` 目录创建 + 占位文件
4. `AuraMemoryStore` 接口定义（不实现）
5. `docs/architecture.md` 加交叉引用（本 PR）

---

## 10. 风险与未解问题

### 10.1 性能 / 资源

| 风险 | 缓解 |
|------|------|
| Inner Monologue 持续耗电 | 85% tick 是 no-op；仅前台运行 |
| Dream Loop 占资源 | 电量 < 20% 不跑；用户可调频率 |
| 本地 LLM 冷启动 3–5s | App 启动时 `executor.preload()`；首次对话稍长可接受 |
| KV-cache 续写偏题 | 24h 自动 reset；监控 `inner_state.md` 长度 |
| 长期 KV-cache 撑爆存储 | 单 cache 上限 50MB；超限自动 reset |

### 10.2 正确性

| 风险 | 缓解 |
|------|------|
| 本地 LLM 写出垃圾 memory | 用户可见 Auto Memory 文件，可手动编辑/删除 |
| Dream loop 和对话体抢同一份 memory | 数据库行级锁 + 状态机互斥 |
| Reactive 误把工具请求当闲聊 | 关键词兜底 + prompt 分类器双层 |
| KV-cache 续写产生"幻觉"自我对话 | 写出 inner state 必须校验才更新 mood |
| mood drift 失控（mood 一直走低） | 周期性 `MoodDecayWorker` 向中性回拉 |

### 10.3 产品定位

| 风险 | 缓解 |
|------|------|
| 用户不理解"为什么本地模型一直跑" | Settings 解释 + 电池用量透明 |
| 用户觉得"我的对话怎么变了" | 数据导出功能让用户随时看到 Aura 记住了什么 |
| 用户担心 dream 偷听 | 明确告知：dream 只看持久化的 messages，不监听麦克风 |

### 10.4 未解问题（需 PoC 验证）

- MNN 在主流手机的真实 t/s（1.5B Q4 / 3B Q4）
- 1.5B / 3B 在中文细腻情感表达上的实际质量
- Inner Monologue 续写的连贯性能维持多久
- Dream Loop 用户主观体验（觉得 Aura "更懂我" 还是 "变怪了"）
- Reactive confidence 评估是否需要更复杂的方案（不只靠首 token 延迟）

---

## 11. PoC 入口

**只做一件事证明这条路对**：实现 `PresenceHeartbeat` + `CompanionKVCacheStore` + `MoodDrift`，跑一周。

### PoC 范围（不超出）

1. `core/presence/runtime/CompanionKVCacheStore.kt` — KV-cache 文件持久化（~80 行）
2. `core/presence/runtime/PresenceHeartbeat.kt` — 0.5 Hz 心跳协程（~100 行）
3. `core/presence/runtime/MoodDrift.kt` — mood 数值漂移（~50 行）
4. `LocalQwenExecutor.continueFromCache()` — MNN 续写 API（~30 行）
5. 一个临时 debug UI 显示 inner state 滚动 + mood 曲线

### 验证项

- 续航影响（每小时耗电 < 1%？目标）
- KV-cache 续写连贯性（看 inner_state.md 像不像"持续的思考"）
- 内心独白质量（人工 review 100 条样本）
- mood 数值漂移是否自然（不会一直走低 / 走高）

### 不在 PoC 范围

- Auto Memory 写入（Phase 3）
- Dream Loop 流水线（Phase 2 才有）
- Reactive Companionship（Phase 4）
- 任何对话体的改动（用户说先不拆）

**PoC 决策门**：续航 < 2%/小时 **或** 独白质量人工评测 < 60 分 → 暂停整个 v2 计划。

---

## 12. 关联文档与调研参考

### 12.1 项目内文档

- [`roadmap.md`](../roadmap.md) — 当前实现状态 + 里程碑
- [`architecture.md`](../architecture.md) — 整体架构（本 plan 在 §二 项目分层有交叉引用）
- [`koog-android-integration.md`](../koog-android-integration.md) — Koog 在 Android 的接入细节，本方案的对话体部分完全基于此
- [`on-device-qwen-mnn-research.md`](../on-device-qwen-mnn-research.md) — 端侧 Qwen / MNN 可行性调研，本文是该结论的架构落地
- [`plan/agent-capability-server-plan.md`](./agent-capability-server-plan.md) — 端云协同智能体的服务器端方案
- [`plan/vision-tools-plan.md`](./vision-tools-plan.md) — Vision 与工具协同
- [`archive/plan/promise-system-design.md`](../archive/plan/promise-system-design.md) — 承诺系统设计（历史方案）

### 12.2 外部调研参考

**Auto Memory / 持久化机制**：
- [Claude Code Auto Memory](https://code.claude.com/docs/en/memory) — `MEMORY.md` 索引 + 主题文件 lazy load；Claude 自己判断写什么
- [Claude Code Routines](https://code.claude.com/docs/en/routines) — 定时/API/GitHub 触发的后台会话，"laptop closed 也在跑"，可映射为 Aura 的 WorkManager 后台任务

**分层记忆与 OS 风格调度**：
- [MemGPT 论文](https://arxiv.org/abs/2310.08560) — OS-style 分层记忆（core / archival / recall），LLM 通过 function call 自己管理 context window
- [Letta Sleep-Time Compute](https://www.letta.com/sleep-time-compute) — MemGPT 演进版，闲时跑 reflection / memory editing

**Agent 闲时反思范式**：
- [Generative Agents（Stanford Smallville）](https://arxiv.org/abs/2304.03442) — Memory Stream + Reflection + Planning；reflection 阈值触发而非每轮触发
- [Voyager（Minecraft 永生学习 agent）](https://arxiv.org/abs/2305.16291) — 永增技能库，应对模板图谱化

**产品定位参考**：
- [Apple Intelligence](https://www.apple.com/apple-intelligence/) — 默认端侧、更复杂才上 PCC；关键是端侧模型作为常驻进程
- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) — 纯本地产品定位，"data never leave your phone"

**端侧推理引擎**：
- [MNN](https://github.com/alibaba/MNN) — Aura 当前用的端侧推理引擎，Android 核心 SO ~800KB，FP16/Int8 量化

### 12.3 调研结论的本地化映射

| 外部范式 | Aura 对应实现 |
|---------|-------------|
| Claude Code Auto Memory | `AuraMemoryStore` + `MEMORY.md` |
| Claude Code Routines | `DreamLoopWorker` + `MoodDecayWorker` + `Pulse` |
| MemGPT 三层记忆 | core = 当前对话 context; archival = `memories` 表; recall = `messages` 表 |
| Letta sleep-time | `DreamPipeline.run(trigger)` |
| Generative Agents 阈值反思 | `AuraMemoryWriter.shouldWrite()` 攒够 10 条才跑 |
| Voyager 技能库 | `skill_templates.md` |
| Apple Intelligence 双层 | 对话体 + 陪伴体双子系统 |

---

## 附录 A — 关键 prompt 设计（待 PoC 验证后补全）

本附录预留位置，Phase 0 完成后填入：

- `reactivePrompt(message, mood)` — L3 闲聊的 system prompt
- `summarizePrompt(messages)` — L2 dream 摘要的 system prompt
- `classifyPrompt(summary)` — L2 分类决策的 system prompt
- `innerMonologueSeed` — L1 KV-cache 初始化的种子 prompt

---

**Status: Phase 0 准备阶段（2026-06-14 起）**
