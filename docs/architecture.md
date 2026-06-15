# Android 智能体应用 — 技术架构文档

> 设计原则：**自研"灵魂"，外包"器官"。**
> 创新点在 Companion Core（情绪状态机 / 关系系统 / Prompt 组装 / LLM 输出解释器），
> 基础设施全部使用 Android/Kotlin 生态成熟组件。
>
> Agent 框架：**Koog（JetBrains）** | LLM：**GLM-5v-turbo（默认）/ Kimi 2.6（可切换）**

---

## 当前实现状态

> Last verified: 2026-06-15. 详细里程碑见 [roadmap.md](./roadmap.md)。

当前代码已经达到 **M3 端到端阶段**（Pattern Detect 端到端 PoC + Mood Trend Chart）：

**M1 收尾（`1bad958`）**：
- 模型连通性检查（`LlmConnectivityChecker` + SettingsScreen/McpSettingsScreen "Test connection" 按钮）
- 记忆编辑/置顶/归档（`MemoryRoomScreen` 长按弹层 + 4 个动作 + ⭐/Archive 图标）
- MIGRATION_5_6（`pinned` / `archived` 字段 + 2 索引）

**M2 核心（`5b77241`）**：
- `insights` 表 14 字段 + 3 索引 + MIGRATION_6_7
- `InsightValidator` 4 道校验（缺 evidence / 50% 真实存在 / confidence < 0.6 / 30 天 heading Jaccard > 0.8）
- `InsightPrompts` 3 个 prompt 字面量（patternDetect / anniversaryScan / connectionDetect）
- `InsightRepository.saveIfValid`（写路径必须过 Validator，4 道守门）
- `InsightCard` / `InsightCardList` / `InsightLongPressDialog`（主页 Section 集成 + 长按"本周不再说 X 类"/"知道了"/"查看依据"/"和 Aura 聊聊"）
- AuraHomeScreen 改 LazyColumn 集成

**M2 收尾（`4ee7758`）**：
- 5 步 Onboarding（plan §5.2 种子期，挂心事/重要日期/称呼/关系人/作息）— 全部可选可跳过
- `AutoMemoryStore`（DataStore-backed 雏形，userPatterns/recurringTopics/onboardingCompletedAt 三个 key）
- DataTransparencySection（设置页条数 + 导出 JSON via 系统 SAF + 3 个清空按钮 + Bipass 二次确认）
- AuraHomeScreen 与 SettingsScreen 都加隐私面板

**M3 端到端（`ba6ebad`）**：
- dual-mind Phase 0 命名清理：`LocalQwenAgentWrapper` → `ReactiveCompanion` + 二选一逻辑标 `@Suppress`
- `core/presence/runtime/` 目录新建（dual-mind 觉察面前驱）：
  - `LocalQwenExecutor`（包装 MNN 引擎 + Request(maxTokens/temperature) + parsePatternDetectOutput 6 case 单测）
  - `DreamDataCollector`（7 天 mood/message/memory 聚合 + 简易词频 top 10 + render prompt）
  - `DreamLoopWorker`（@HiltWorker + @AssistedInject 模式，参照 `ReminderNotificationWorker`）
  - `DreamLoopScheduler`（**7 档可配置周期** OFF / 15min / 30min / 1h / 3h / 6h(默认) / 12h + **立即跑一次按钮**；`WorkScheduler` 接口 + `WorkManagerScheduler` 实现解耦 WorkManager 静态；Hilt 注入 `@ApplicationScope` CoroutineScope 跑 collector；改档位走 `ExistingPeriodicWorkPolicy.UPDATE`；默认 6h 向后兼容）
  - `BatteryHelper`（API 29+ BATTERY_PROPERTY_CAPACITY + sticky broadcast 兜底）
- `CompanionApplication.onCreate` 注入 scheduler + 调度
- `feature/insight/MoodTrendChart` Compose Canvas 4 根周柱状图（3 档配色）
- ChatViewModel 第 9 个 collector 推近 28 天 mood_snapshots 到 uiState.moodTrend
- "和 Aura 聊聊" prefill 路由打通（`pendingPrefill` 字段 + `consumePrefillPrompt` 命令 + ChatScreen `LaunchedEffect(pendingPrefill)` 消费）

**M3 PoC 修复（`9fa58ab`）**：
- `seedDemoInsights` evidence 真实化（saveIfValid **前**先插真实 mock 行）
- `MemoryRepository.insertMemoryWithId`（固定 id 供 evidence 引用）
- `MoodSnapshotEntity` 外键需 `agent_state` parent row（先 `agentStateDao.insert`）
- Onboarding 5 问全部改为可选（plan §5.2 强调"不强迫"产品调性）

**M3 PoC UX 修复（`85cb87c`）**：
- Save 按钮从 LazyColumn 末尾提到 TopAppBar actions 永久可见（之前被 DataTransparencySection 推到屏外）
- `api_key` 字段实时保存（`updateSettingsApiKey` 直接调 `configRepository.setApiKey`，不依赖 Save 按钮）

**M4 vision→memory→dream 闭环（`1b826d1`）**：
- `MemoryEntity` 升级到 v8：`MIGRATION_7_8` 新增 `imageBase64 TEXT` + `imageMediaType TEXT DEFAULT 'image/jpeg'` 两列（Room 已 export schema v8）
- `MemoryDao.getRecentImages/observeImages`：按 `imageBase64 IS NOT NULL` 过滤 + `ORDER BY timestamp DESC LIMIT :limit`
- `MemoryRepository.saveVisionMemory`：把"用户发图"事件作为 `type=FACT, source=reflection:vision` 记忆写入（content 形如 `"[图片] 摘要"`）
- `SendMessageUseCase`：注入 `MemoryRepository`，发送带图消息时 fire-and-forget 调 `saveVisionMemory`，失败仅 log 不阻塞主流程
- `DreamDataCollector`：
  - `Snapshot.imageMemories: List<ImageMemorySummary>`（**仅 metadata**，id/content/timestamp/importance，**不含 base64**）
  - `collectLast7Days` 调 `memoryDao.getRecentImages(IMAGE_MEMORY_LIMIT=5)`，过滤 7 天窗口
  - `render` 新增 `## 视觉证据(N 张)` section
  - `isEmpty` 判定把 imageMemories 也纳入（无图无文本无 mood 算空）
  - 注释里把"base64 永远不进 DreamPrompt"作为安全护栏写死；`render_doesNotLeakBase64` 测试做硬约束

**测试**：372 单测全绿（0 失败），含 11 个 M4 vision memory 用例。`assembleDebug` 通过。

**架构当前已完整闭环**：
- Compose 聊天页 + `ChatViewModel` + `CompanionRuntime` + Koog `AIAgent` 流式调用
- Room/DataStore/Hilt 基础设施（7 表 + 16 索引 + 7 DAO + 5 Repository）
- 只读上下文工具、记忆/摘要搜索、设备/时间/天气/提醒与远程 MCP 工具
- 写路径严格走 Validator 守门（insight 写、memory 合并）
- Agent 写操作在回复后系统阶段处理（不再作主对话工具）
- WorkManager 周期任务（DreamLoop 6h + Reminder OneTime）
- 本地 LLM 引擎在位（`LocalQwenEngine` + MNN 桥），通过 `ReactiveCompanion` 暴露为 Koog Wrapper

仍处于规划或部分实现状态的模块：

- CameraX 拍照/选图 UI（图片接收 + 压缩已实现，UI 待 — 当前走 Photo Picker 选图满足 MVP）
- SpeechRecognizer/TextToSpeech 语音 I/O
- Rive/Lottie 状态机动画（Aura 角色用 Compose Canvas 临时替代）
- 远端 Agent Server / `RemoteAgentRuntime` / MCP Gateway / Browser Worker（plan §8 收窄到"信息回写"主轴）
- WorkManager Pulse worker（除 DreamLoop 之外的离线衰减/回归反应/主动通知）
- Weekly Insight 自动汇总（M5） + 用户反馈回路（InsightLog）
- M4 余下：Connection 类 insight 端到端、Pattern 跨 mood+memory+图片三种数据源的真机验证（PoC 待 Qwen 模型下载后跑通）

## 一、基础技术栈

| 层面 | 选型 | 说明 |
|------|------|------|
| JDK | **21**（Oracle LTS 21.0.6） | 与当前本地环境一致 |
| 语言 | **Kotlin 2.3.21** | 由 Gradle plugin 管理 |
| Android Gradle Plugin | **9.2.0** | 以 `gradle/libs.versions.toml` 为准 |
| SDK | compileSdk 36 / minSdk 26 / targetSdk 36 | 当前项目实际配置 |
| UI | Jetpack Compose | 官方现代声明式 UI，状态驱动 |
| 架构 | MVVM + Repository + UseCase | 标准分层，职责清晰 |
| 并发 | Kotlin Coroutines + Flow | 异步事件流，天然适配 Agent 场景 |
| Agent 框架 | **Koog 0.8.0**（JetBrains） | Agent 运行时 |
| LLM 默认模型 | **GLM-5v-turbo**（智谱 AI） | 多模态（Vision），兼容 Anthropic Messages API |
| LLM 备选模型 | **Kimi 2.6**（Moonshot AI） | 可切换，同样兼容 Anthropic Messages API |
| API 协议 | **Anthropic Messages API**（兼容格式） | 统一接口，切换模型只需改 base_url + model name |
| 依赖注入 | Hilt | 与 ViewModel/Navigation/WorkManager 深度集成 |
| 数据库 | Room / SQLite | 持久化消息、记忆、状态 |
| 配置存储 | DataStore | 轻量键值对配置（API key、主题等） |
| 后台任务 | WorkManager | 可延迟、可持久化的后台调度（Pulse） |
| 序列化 | kotlinx.serialization | 结构化 JSON 输入输出 |
| 动画 | Lottie / Rive | 依赖已规划，角色表情层尚未实现 |
| 语音 | Android TTS + SpeechRecognizer | 规划中 |
| 相机 | CameraX | 依赖已接入，拍照/选图 UI 尚未实现 |
| 图片加载 | Coil | Compose 原生图片加载 |
| 日志 | Timber | 轻量日志库 |
| 崩溃分析 | Firebase Crashlytics / Sentry | 生产级监控 |

---

## 二、项目分层

```
app/
├─ feature/                    # 功能模块（UI 层）
│  ├─ chat/                    #   聊天对话（已实现）
│  │  ├─ presence/             #     角色视觉(背景光晕 + Aura 本体)
│  │  ├─ mapper/               #     Entity/Config → Chat* 纯函数映射
│  │  ├─ usecase/              #     SendMessageUseCase / SettingsUseCase
│  │  ├─ ChatViewModel.kt      #     编排器(8 个 collector + Presence 编排)
│  │  ├─ ChatScreen.kt         #     聊天页 Composable 编排
│  │  ├─ ChatHeader.kt         #     顶栏 + Memory/Reminders/MCP/Settings 入口
│  │  ├─ ChatInputBar.kt       #     输入栏 + IME 副作用封装
│  │  ├─ ChatDialogs.kt        #     弹窗集合(Reminders/Permission/Aura 原语)
│  │  ├─ SettingsScreen.kt     #     设置页
│  │  ├─ McpSettingsScreen.kt  #     MCP 服务配置
│  │  ├─ MemoryRoomScreen.kt   #     记忆房间
│  │  └─ AuraHomeScreen.kt     #     角色主屏入口
│  ├─ avatar/                  #   角色主屏（规划中）
│  ├─ memory_room/             #   记忆房间（规划中）
│  ├─ settings/                #   设置页（规划中）
│  └─ onboarding/              #   新手引导（规划中）
│
├─ core/                       # 核心业务逻辑（自研 + Koog）
│  ├─ companion/               #   ★ CompanionRuntime 主循环（自研）
│  ├─ llm/                     #   Anthropic Messages 兼容 LLM client / executor
│  ├─ prompt/                  #   ★ Prompt 组装引擎（自研）
│  ├─ tools/                   #   Agent tools（已实现基础能力）
│  ├─ presence/                #   PresenceController / PresenceReactionPolicy(状态推导与反应节流)
│  ├─ local/                   #   本地 LLM 链路(MNN + Qwen 模型下载/加载/推理)
│  ├─ reminder/                #   提醒系统(AlarmManager + Worker)
│  ├─ logging/                 #   日志封装与字段脱敏
│  └─ pulse/                   #   ★ 生命脉冲策略（规划中）
│
├─ data/                       # 数据层
│  ├─ db/                     #   Room DAO / Entity
│  ├─ datastore/              #   DataStore 配置
│  ├─ repository/             #   Repository 实现
│  └─ assets/                 #   本地资源文件
│
└─ platform/                   # 平台能力封装
   ├─ speech/                 #   语音输入输出
   ├─ camera/                 #   相机能力
   ├─ notification/           #   通知推送
   ├─ widget/                 #   桌面小组件
   └─ permissions/            #   权限管理
```

> 上面包含目标形态。当前源码中已经落地 `feature/chat`（含 `presence` / `mapper` / `usecase` 三个子包）、`core/companion`、`core/llm`、`core/prompt`、`core/tools`、`core/logging`、`core/presence`、`core/local`、`core/reminder`、`data`、`di`；`platform` 与多数非聊天 feature 仍在 roadmap 中。

### 2.1 核心调用链（聊天主路径）

```text
ChatScreen (Composable)
  └─→ ChatViewModel (@HiltViewModel)
        ├─ 8 个 init { launch } collector(配置 / 状态 / 消息 / 记忆 / 提醒 / 工具 / 偏好)
        ├─ Presence 编排(withPresence / triggerPresenceReaction)
        └─ 委托给 UseCase:
              ├─ SendMessageUseCase  → CompanionRuntime → Koog AIAgent → LLM
              │     └─ 消费 AgentEvent 流(Streaming / ToolCall / MemorySaved / Complete / Error)
              └─ SettingsUseCase     → ConfigRepository / AppPreferences / LocalQwenModelDownloader
```

- **ViewModel 只做编排**：8 个 collector 把仓库状态汇入 `uiState`；复杂业务交给 UseCase
- **UseCase 不持有 MutableStateFlow**：通过 `(ChatUiState.() -> ChatUiState) -> Unit` 回调写状态,可独立单元测试
- **Mapper 全部为纯函数**：`feature/chat/mapper/ChatMappers.kt` 集中 9 个 Entity/Config → Chat* 映射,无副作用

### 自研范围（标 ★）

只有以下模块需要深度自研：

- `core/companion/` — 云端 Agent 主循环运行时（`CompanionRuntime` + `KoogAgentFactoryImpl` + `OutputParser` + `EmotionStateMachineImpl` + `RelationshipModelImpl` + `LlmConversationReflection`）。详见 [`docs/agent-architecture.md`](./agent-architecture.md)
- `core/presence/runtime/` + `core/local/` — 本地陪伴体运行时（MNN 推理 + Heartbeat + DreamLoop + ReactiveCompanion）。详见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md)
- `core/prompt/` — Prompt 组装（`PromptBuilder` + `SystemPersona` yml 模板）
- `core/llm/AnthropicMessagesLLMClient.kt` — 自研 OkHttp + SSE 实现，包成 Koog `LLMClient`
- `core/tools/CompanionToolRegistry.kt` — 9 内置 + 远程 MCP tool 合并
- `feature/chat/ChatViewModel.kt` — 8 个 collector + Presence 编排
- `feature/chat/usecase/SendMessageUseCase.kt` — Flow\<AgentEvent\> → ChatUiState 翻译 + 90ms/30s 节流

**Koog 覆盖的部分：** LLM 调用抽象（`PromptExecutor` / `MultiLLMPromptExecutor`）、AIAgent 构造、graph strategy 调度、EventHandler 4 个 hook、Tool registry 注册抽象、structured output（`executeStructured`）。

### 双轨智能体架构（2026-06 新增设计；2026-06-15 叙事升级）

> 详细方案见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md) · 产品叙事见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md)。

Aura 的 LLM 使用拆成两个**职能不同**的子系统，不是"同一任务的两条路径"：

- **云端 = 响应面 / 对话体（Responsive Mind）**：用户发消息时的主路径。Koog `AIAgent` + 工具 + Vision + 反思 + 消费觉察面的 insight 摘要。永远走云端 LLM。**对应"对外办事"**。
- **本地 = 觉察面 / 陪伴体（Continuous Awareness）**：用户不在场时也在场。持续心跳（KV-cache 续写）、dream loop、mood drift、insight 提取、即时闲聊。永远走 MNN + 本地 Qwen。**对应"对内懂事"**。

**产品叙事（2026-06-15 升级）**：Aura 不做"会聊天的 App"，做"**长期认识你的 AI**"（第二大脑 / 数字孪生）。详细见 [`plan/dual-mind-architecture.md` §1.4](./plan/dual-mind-architecture.md) 与 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md)。M2-M5 的 KPI 已按本叙事调整（见 `roadmap.md`）。

**关键不变量**：
- `KoogAgentFactoryImpl` 的二选一逻辑将被移除（对话体不需要"选 provider"，陪伴体永远本地）
- `core/presence/runtime/` 新增：LocalQwenExecutor、PresenceHeartbeat、DreamPipeline、ReactiveResponder、CompanionKVCacheStore
- 对话体**永远不写** mood / relationship（敏感）
- 对话体**永远不直接生成 insight**（insight 是觉察面的产物）
- 陪伴体**永远不上云** inner_state / mood / insight 数据
- 陪伴体**永远不写** messages（不污染对话历史）

Phase 0（重命名 + 注释）预计 2026-06 内完成；具体 PoC 入口和阶段计划见 plan 文档。

---

## 三、LLM 选型与多模态设计

### 3.1 模型选择

#### 主力模型：GLM-5v-turbo（默认）

| 属性 | 值 |
|------|-----|
| 提供商 | 智谱 AI（ZhipuAI） |
| 多模态 | **原生支持 Vision**（文本 + 图片理解） |
| API 格式 | **Anthropic Messages API 兼容** |
| 长上下文 | 支持 |
| 流式输出 | SSE streaming |
| 结构化输出 | Tool Use / JSON mode |

选择 GLM-5v-turbo 的原因：
- **原生多模态**：Vision 能力是核心需求（CameraX → 图片理解）
- **Anthropic 兼容**：通过项目内 `AnthropicMessagesLLMClient` 适配 Koog executor，统一 GLM/Kimi 调用形态
- **国内服务**：延迟低、稳定性好
- **成本优势**：相比 Claude 有竞争力

#### 备选模型：Kimi 2.6（可切换）

| 属性 | 值 |
|------|-----|
| 提供商 | Moonshot AI（月之暗面） |
| API 格式 | **Anthropic Messages API 兼容** |
| 特点 | 长文本能力强，适合记忆密集场景 |

切换方式：**只改 base_url + api_key + model name，代码零改动。**

```kotlin
// 模型配置（DataStore 存储，设置页切换）
data class LlmConfig(
    val provider: LlmProvider = LlmProvider.GLM,     // GLM 或 KIMI
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v1",
    val apiKey: String,
    val modelName: String = "glm-5v-turbo",          // 或 "kimi-latest"
)
```

### 3.2 Anthropic 兼容协议

GLM-5v-turbo 和 Kimi 2.6 都兼容 **Anthropic Messages API** 格式，这意味着：

```
App 代码
  ↓
Koog PromptExecutor + AnthropicMessagesLLMClient(baseUrl, apiKey, model)
  ↓ （自动组装 Anthropic 格式请求）
{ "model": "glm-5v-turbo", "messages": [...], "stream": true }
  ↓
GLM / Kimi API Server（兼容 Anthropic 格式）
  ↓ （返回 Anthropic 格式响应）
{ "type": "content_block_delta", "delta": { "type": "text_delta", "text": "..." } }
  ↓
Koog 自动解析 → Flow<AgentEvent>
```

**关键收益：**
- App 业务层只依赖 `CompanionRuntime` / `KoogAgentFactory`，不直接接触 HTTP 细节
- Anthropic Messages 请求、SSE 解析和工具 schema 适配集中在 `core/llm`
- 模型切换只需换配置，Runtime/Chat UI 接口不变

### 3.3 API 能力映射

```
文本对话（Chat）          → Messages API          ✅ GLM/Kimi 均支持
流式输出（Streaming）     → SSE stream             ✅ 通过项目内兼容层接入 Koog
多模态视觉（Vision）      → image content block    ✅ GLM-5v-turbo 原生支持
结构化输出（Tool Use）    → tool_use + JSON schema ✅ 通过项目内兼容层接入 Koog tools
长上下文                 → 大窗口                 ✅ 两者均支持
Prompt 缓存（Caching）   → cache_control          ⚠️ 取决于提供商实现
```

---

## 四、各层详细设计

### 4.1 UI：Jetpack Compose

所有界面使用 Compose 声明式实现：

| 页面 | 状态驱动要素 |
|------|-------------|
| 聊天页 | reply streaming → 气泡逐字显示 |
| 角色主屏 | mood 变化 → 表情/姿态变化 |
| 记忆房间 | memories → 卡片列表 |
| 设置页 | 配置项 → 表单控件（含模型切换） |
| 关系天气 | relationship level → 天气可视化 |
| 小纸条 | secret note → 弹出动画 |

```kotlin
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
```

Compose 的优势：强状态驱动产品天然匹配 —— mood 变了 UI 自动变，reply streaming 自动刷新气泡。

---

### 4.2 数据库：Room

Room 负责持久化的核心数据：

| 表名 | 存储内容 |
|------|----------|
| messages | 聊天消息记录（已实现） |
| memories | 长期记忆条目（已实现） |
| agent_state | Agent 当前状态快照（已实现基础表） |
| mood_snapshots | 情绪历史快照（已实现） |
| tool_calls | Agent 工具调用记录（已实现） |
| life_events | 生活事件时间线（规划中） |
| agent_profile | 角色档案配置（规划中） |
| memory_objects | 记忆物品（照片、地点等，规划中） |
| scheduled_actions | 待执行的定时动作（规划中） |

```kotlin
implementation("androidx.room:room-runtime:<version>")
implementation("androidx.room:room-ktx:<version>")
ksp("androidx.room:room-compiler:<version>")
```

不要自己写 SQLiteOpenHelper，没必要。

---

### 4.3 配置：DataStore

DataStore 存储轻量键值对配置：

| 配置项 | 类型 |
|--------|------|
| API Key | String?（已实现） |
| 主题模式 | Enum (Light/Dark/System，已实现） |
| **LLM Provider** | **Enum (GLM / KIMI，已实现）** |
| **当前模型名称** | **String (glm-5v-turbo / kimi-latest 等，已实现）** |
| 当前角色 ID | String（规划中） |
| 语音开关 | Boolean（规划中） |
| 是否允许主动通知 | Boolean（规划中） |
| 用户隐私设置 | Preferences（规划中） |

```kotlin
implementation("androidx.datastore:datastore-preferences:<version>")
```

---

### 4.4 后台 Pulse：WorkManager

"生命感"不靠常驻后台服务，而是通过以下机制组合：

| 触发方式 | 用途 |
|----------|------|
| 打开 App 时补算状态 | 冷启动恢复 |
| WorkManager 低频 pulse | 定期情绪衰减/回忆触发 |
| 通知触发 | 用户交互响应 |
| 定时小纸条 | 主动关怀推送 |
| 夜间复盘 | 离线期间状态更新 |
| 离开后反应 | 回归时的欢迎逻辑 |

```kotlin
implementation("androidx.work:work-runtime-ktx:<version>")
implementation("androidx.hilt:hilt-work:<version>")
ksp("androidx.hilt:hilt-compiler:<version>")
```

---

### 4.5 网络：Koog + Anthropic Messages 兼容层

当前项目通过 `core/llm/AnthropicMessagesLLMClient.kt` 实现 Anthropic Messages 兼容请求，再接入 Koog 的 `PromptExecutor` / `AIAgent`：

- HTTP 连接管理（OkHttp 4.12.0，60s callTimeout）
- 请求序列化（Anthropic Messages 格式，含 system / messages / tools / image base64）
- 响应反序列化（SSE stream 解析，区分 `text_delta` / `input_json_delta` / `content_block_stop` / `message_delta`）
- 错误处理与重试（**当前未启用重试**，只把错误转成 `AgentError.NetworkTimeout` / `ApiError`）
- 超时控制（callTimeout 60s + LlmConnectivityChecker connect 5s / read 8s）

App 业务层通过 `ConfigRepository`（DataStore）管理 `baseUrl / apiKey / modelName`：
- **GLM / Kimi** 在 `SettingsScreen` 切换 provider → 改 DataStore → 下次 `send()` 自动用新模型
- **连通性检查**：`SettingsScreen` / `McpSettingsScreen` 的 "Test connection" 按钮调 `LlmConnectivityChecker.check()`，区分 200 / 401-403 / 网络失败，缓存到 `uiState.connectivityResult`

> 详细 SDK 集成（API 表面、线程规则、KG-750 死锁规避）见 [`docs/koog-android-integration.md`](./koog-android-integration.md)。本项目**没有**用 Ktor / Retrofit — 全部走自研的 `AnthropicMessagesLLMClient`，LLM 是 App 唯一对外 HTTP 流量。

---

### 4.6 依赖注入：Hilt

选 Hilt 而非 Koin 的原因：
- 与 Jetpack ViewModel / Navigation / WorkManager 官方集成
- 编译期校验，错误更早发现
- Android 项目标准实践

```kotlin
implementation("com.google.dagger:hilt-android:<version>")
ksp("com.google.dagger:hilt-compiler:<version>")
implementation("androidx.hilt:hilt-navigation-compose:<version>")
implementation("androidx.hilt:hilt-work:<version>")
ksp("androidx.hilt:hilt-compiler:<version>")
```

---

### 4.7 动画与角色显示

**当前实现（2026-06-15）**：

| 组件 | 实现 | 位置 |
|------|------|------|
| `AuraPetAvatar` | Compose Canvas 自绘（眼睛 / 腮红 / 表情） | `feature/chat/AuraPetAvatar.kt` |
| `PresenceAvatar` | Compose Canvas + 表情层（mood / intensity） | `feature/chat/PresenceAvatar.kt` |
| `HomePresenceAvatar` | 主页背景上的呼吸动画 | `feature/chat/presence/HomePresenceAvatar.kt` |
| `AuraHomeScreen` | 角色主屏 LazyColumn 集成 | `feature/chat/AuraHomeScreen.kt` |

**演进路径**：

| 阶段 | 方案 | 状态 |
|------|------|------|
| MVP | **Compose Canvas 自绘** | ✅ 已实现 |
| 进阶 | **Lottie 状态机** | ⏳ `lottie-compose 6.6.0` 依赖在但**未启用**；需要设计 mood→animation 映射 + 资源 |
| 高级 | Rive | 未来规划；状态机式角色动画 |

```kotlin
// 当前实际依赖
implementation("com.airbnb.android:lottie-compose:6.6.0")   // 备用,未启用
implementation("io.coil-kt:coil-compose:2.7.0")            // 头像 / 记忆缩略图
```

> **决策原因**：MVP 阶段 Compose Canvas 自绘已足够表达 mood × intensity × relationshipLevel 的 8-12 个表情，**避免** Lottie 资源文件带来的包体膨胀和热更新难题。Lottie 将在角色表现力成为产品瓶颈时引入。

---

### 4.8 语音（尚未实现）

**当前状态**：CLAUDE.md 已声明 "`SpeechRecognizer` / `TextToSpeech` 语音 I/O" 尚未实现。数据模型**已经预留** `UserInput.Speech`（`core/companion/model/CoreModels.kt`），但 runtime / UI / VM 都没有接。

**未来设计（接口层）**：

```kotlin
interface SpeechInput {
    fun startListening(): Flow<SpeechEvent>
}

interface SpeechOutput {
    suspend fun speak(text: String, style: VoiceStyle)
}
```

- ASR：`SpeechRecognizer`（系统语音识别）
- TTS：`TextToSpeech`（系统语音合成）

**第一版不做**：实时全双工语音、唤醒词、打断检测（VAD）。后续可无缝替换为云端方案（Whisper API / ElevenLabs 等），只换实现不改接口。

---

### 4.9 视觉输入：Photo Picker → Vision 多模态（M4 闭环）

> **范围变更（2026-06）**：原本设计用 CameraX 预览/拍照作为 Vision 入口。**当前 MVP 改用系统 Photo Picker**（`ActivityResultContracts.PickVisualMedia`），不接 CameraX 预览。CameraX 1.4.0 依赖**保留在 `libs.versions.toml` 但未启用**，等真有"实时拍摄 + 即时分析"场景再启用。

#### 多模态交互场景

| 场景 | 输入 | 模型理解 | Agent 响应 |
|------|------|---------|-----------|
| "你看我今天的穿搭怎么样" | Photo Picker 选图 | 衣服颜色、风格、搭配 | 情绪化评价 + 关系亲昵度影响语气 |
| "帮我看看这个" | 用户拍照/选图 | 物体识别、场景理解 | 记忆存储（`saveVisionMemory`）+ 情绪反应 |
| 食物照片 | 选图 | 菜品识别 | 关心/调侃（取决于关系等级） |
| 外出风景 | 选图 | 天气、地点、氛围 | 共鸣情绪 + 记忆标记 |

#### 数据流（M4 闭环）

```
Photo Picker (ActivityResultContracts.PickVisualMedia)
    ↓ URI
ChatImageProcessor.prepare(uriString)        // ContentResolver + BitmapFactory
    ↓ JPEG Bitmap
    ↓ Bitmap → JPEG compress(q=80) → base64
    ↓ ChatImageAttachment(uriString, imageBase64, mediaType)
SendMessageUseCase.attachImage → _uiState.pendingImage
User 发送消息
    ├─ ① fire-and-forget: memoryRepository.saveVisionMemory(summary, imageBase64, ...)
    │      ↓ 写 MemoryEntity (含 imageBase64 / imageMediaType, MIGRATION_7_8)
    │      ↓ 失败仅 log,不影响主流程
    └─ ② 立即发: UserInput.Vision(text, imageBase64, mediaType, displayText)
            ↓ CompanionRuntime.send
            ↓ promptBuilder.build(allowTools = true)
            ↓ KoogPromptExecutorWrapper.toKoogAgentPrompt() (ContentPart.Image + AttachmentContent.Binary.Base64)
            ↓ MultiLLMPromptExecutor → AnthropicMessagesLLMClient.executeStreaming
            ↓ POST /v1/messages with image source.base64
            ↓ 模型 Vision 理解
            ↓ Tool / Streaming / Complete 事件流 → UI
            ↓
DreamDataCollector (M3 末 M4 补)
    ↓ 7 天图 memory 聚合
    ↓ 把 metadata 注入 ## 视觉证据 section
    ↓ base64 不进 DreamPrompt
```

关键设计：
- **图片压缩**：`ChatImageProcessor` 内部用 `BitmapFactory` 二次采样（`inSampleSize`）+ JPEG `compress(quality=80)`，目标 < 500KB
- **base64 编码**：Anthropic 兼容 API 接受 inline base64 图片，无需外部 URL
- **持久化策略**：图片数据持久化到 Room（`MemoryEntity.imageBase64`），用于"看过的图"清单与 Dream 聚合；**不**走系统相册
- **权限管理**：Photo Picker 不需要任何运行时权限（Android 13+ 系统级），低版本走 `READ_MEDIA_IMAGES`；CameraX 暂不接
- **隐私保护**：图片数据仅本机 Room + 单次 API 调用；导出 / 删除走 `MemoryRepository.clearAll()` 或单条 `deleteMemory`

> `MemoryEntity.imageBase64` / `imageMediaType` + MIGRATION_7_8 已落库。`MemoryRoomScreen` 列表会显示图缩略图（Coil + base64 URI）。

---

## 五、Agent Core 设计

> **范围说明**：本章只讲**云端对话体**的 Agent Core。本地陪伴体（Continuous Presence）的运行时独立于 Koog，在 `core/presence/runtime/` + `core/local/` 目录，自有 task orchestration 协议，详见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md)。
>
> 详细编排（Provider 路由、Graph Strategy、Tool 系统、Memory Reflection、流式 UX 节流）见 [`docs/agent-architecture.md`](./agent-architecture.md)。Koog SDK 集成参考（API 表面、线程规则、KG-750 死锁规避）见 [`docs/koog-android-integration.md`](./koog-android-integration.md)。本章不复述。

### 5.1 一句话定位

> **Aura 的云端 Agent 是一条"事件流"**：
> `ChatViewModel → SendMessageUseCase → CompanionRuntime → KoogAgentFactory → KoogPromptExecutorWrapper → LLM`，
> 返回 `Flow<AgentEvent>`，UI 按事件类型分别消费。

Koog 负责 **LLM 调度 + 工具循环 + 流式**。其他所有事情（情绪机、关系模型、记忆注入、tool 注册、tool 持久化、output 解析、reflection、UX 节流）都是 Aura 自研。

### 5.2 核心运行时：11 步 Pipeline

`CompanionRuntime.send(input: UserInput): Flow<AgentEvent>` 是云端 Agent 的总入口，**不是同步返回 String**——它返回 `Flow<AgentEvent>`，UI 在 `SendMessageUseCase` 里消费。

```kotlin
class CompanionRuntime @Inject constructor(
    // 外部依赖
    private val configRepository: ConfigRepository,                // LlmConfig（provider/url/key/model）
    private val koogAgentFactory: KoogAgentFactory,                // AIAgent 工厂
    private val promptBuilder: PromptBuilder,                      // BuiltPrompt 组装
    private val outputParser: OutputParser,                        // regex 解析 [mood:..] 等
    private val messageRepository: MessageRepository,              // USER/ASSISTANT 消息落库
    private val memoryRepository: MemoryRepository,                // 记忆库 + selectPromptContext
    private val conversationContextBuilder: ConversationContextBuilder, // 近 N 条对话
    private val conversationReflection: ConversationReflection,    // runStructured 写记忆
    private val emotionMachine: EmotionStateMachine,               // mood 状态机（Singleton）
    private val relationshipModel: RelationshipModel,               // affinity 累加（Singleton）
) {
    open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
        // ① 拉记忆 context（向量检索 + summary 检索）
        val memoryContext = memoryRepository.selectPromptContext(input.content)
        // ② 拉近 50 条对话
        val conversationContext = conversationContextBuilder.build(DEFAULT_SESSION_ID)
        // ③ 组装 prompt（persona + emotion + relation + summaries + recent + memories + tools）
        val prompt = promptBuilder.build(input, emotionMachine.getContext(),
            relationshipModel.contextModifier(), conversationContext.recentMessages,
            memoryContext.memorySnippets, memoryContext.summarySnippets)
        // ④ 读 LlmConfig
        val config = configRepository.getCurrentLlmConfig().first()
        // ⑤ 构造 agent（Provider 路由：LOCAL_QWEN → ReactiveCompanion，其他 → KoogPromptExecutorWrapper）
        val agent = koogAgentFactory.create(config)
        // ⑥ 写 user message（Vision 时附 imageBase64）
        val userMessageId = messageRepository.sendMessage(...)
        // ⑦ 跑 agent.runEvents(prompt).collect → 转 AgentEvent
        // ⑧ 解析 raw response → ParsedOutput（textReply / emotionSignal / interactionSignal / actions）
        val parsed = outputParser.parse(rawResponse)
        // ⑨ 喂情绪 + 关系
        emotionMachine.feed(parsed.emotionSignal)
        relationshipModel.update(parsed.interactionSignal)
        // ⑩ memory reflection（agent.runStructured，不走主 turn 的 graph）
        val savedMemoryCount = conversationReflection.reflectAndSave(...).savedMemoryCount
        // ⑪ 写 assistant message + emit Complete / MemorySaved
    }
}
```

**关键事实**：

- 整个 11 步跑在 `callbackFlow` 里，第 ⑦ 步显式 `launch(Dispatchers.IO)` 切线程，详见 [agent-architecture.md §1](./agent-architecture.md#1-分层与依赖关系)。
- 记忆 reflection 在第 ⑩ 步**额外**调一次 `agent.runStructured`，**绕开**主 turn 的 graph strategy（不允许 `allowTools`），详见 [agent-architecture.md §7](./agent-architecture.md#7-memory-reflection)。
- 历史上 §5 旧版本的 `StateReducer` / `ActionDispatcher` / `MemoryManager` **不存在** — 状态归约已合并进 `SendMessageUseCase.persistStatus`，动作分发已合并进 `ToolCallRecorder`，记忆存储已下沉到 `memoryRepository.saveMemory`。

### 5.3 多模态输入处理

```kotlin
sealed class UserInput {
    abstract val content: String
    data class Text(override val content: String) : UserInput()
    data class Vision(
        val text: String,                  // 用户文字描述
        val imageBase64: String,           // Photo Picker / CameraX 选图
        val mediaType: String = "image/jpeg",
        val displayText: String = text,    // 历史区展示用；空时 fallback "Shared a picture"
    ) : UserInput() { override val content get() = displayText.ifBlank { "Shared a picture" } }
    data class Speech(val transcript: String) : UserInput() { override val content get() = transcript }
}
```

**Vision 来源（M4 闭环）**：
- 不接 CameraX 预览/拍照（CLAUDE.md 已声明"用 Photo Picker 替代 MVP"）
- `ChatImageProcessor.prepare(uriString)` 走 `ContentResolver` + `BitmapFactory` 压缩到 base64
- 选图后 `SendMessageUseCase` 走 **fire-and-forget** 落 vision memory：

```kotlin
scope.launch {
    runCatching {
        memoryRepository.saveVisionMemory(
            summary = trimmed,
            imageBase64 = pendingImage.imageBase64,
            imageMediaType = pendingImage.mediaType,
            sourceMessageId = userMsg.id,
        )
    }.onFailure { AppLogger.warn(...) }   // 失败仅 log,不影响主流程
}
UserInput.Vision(...)   // 立即发,不等 vision memory 写完
```

- `MemoryEntity.imageBase64 / imageMediaType` + MIGRATION_7_8 已落
- `DreamDataCollector` 把图 memory **metadata** 注入 `## 视觉证据` section（**base64 不进 DreamPrompt**）

**LLM 模型要求**：Vision 输入时 `LLModel` 必须声明 `LLMCapability.Vision.Image`，否则 Koog 拒绝 `ContentPart.Image`（详见 [koog-android-integration.md §3.3](./koog-android-integration.md#33-builtprompt--prompt-转换)）。

### 5.4 Provider 路由

`KoogAgentFactoryImpl.create(config)` 是路由总闸：

```kotlin
override fun create(config: LlmConfig): KoogAgentWrapper {
    if (config.provider == LlmProvider.LOCAL_QWEN) {
        // dual-mind Phase 0 临时二选一;Phase 1 拆开云端对话体/本地觉察面后,
        // 这条分支应改为 "ReactiveCompanion 仅在 presence runtime 内被 LocalQwenExecutor 调"
        @Suppress("DEPRECATION_RENAMED_TO_REACTIVE_COMPANION")
        return ReactiveCompanion(engine = localQwenEngine, modelName = config.modelName)
    }
    return KoogPromptExecutorWrapper(
        config = config,
        executor = executorFactory.create(config),     // MultiLLMPromptExecutor
        toolRegistry = toolRegistry,                    // AgentToolRegistry（9 内置 + MCP）
        toolCallRecorder = toolCallRecorder,            // 写 Room tool_calls 表
    )
}
```

| 路由 | 走什么 | 说明 |
|------|-------|------|
| `LlmProvider.LOCAL_QWEN` | `ReactiveCompanion` | MNN 本地推理，不进 Koog，**不**支持流式 + tool |
| `LlmProvider.GLM` / `KIMI` / 其他云端 | `KoogPromptExecutorWrapper` | 走 Anthropic Messages 兼容端点，支持流式 + tool + reflection |

切换模型只需改 `ConfigRepository`（DataStore），下次 `send()` 自动用新模型。

### 5.5 Tool 系统

详见 [agent-architecture.md §6](./agent-architecture.md#6-tool-系统)。这里只列 9 个内置 tool 的总览：

| Tool | 类别 | 用途 |
|------|------|------|
| `SearchMemoryTool` | memory | 向量检索记忆 |
| `SearchRecordsTool` | memory | 检索消息历史 |
| `SearchSummariesTool` | memory | 检索摘要 |
| `GetCurrentTimeTool` | context | 让 LLM 知道"现在" |
| `GetRecentInteractionContextTool` | context | 拉近 N 条对话 |
| `GetUserContextSettingsTool` | context | 读 5 个 capability 偏好 |
| `GetDeviceStatusTool` | context | 设备电量 / 网络（API 29+） |
| `GetWeatherTool` | context | 外部 weather provider |
| `CreateLocalReminderTool` | action | 写本地 Reminder + AlarmManager |

**Tool 双源注册**：`CompanionToolRegistry.create()` 合并内置 9 个 + 远程 MCP tool，任何一个 MCP server 失败不影响其他 server。

**Tool 状态 → Presence**：`ChatViewModel` 第 7 个 collector 订阅 `ToolCallRepository.observeBySession`，每次 tool 状态变化（STARTED/SUCCEEDED/FAILED）→ `presenceController.reactionFor(PresenceEvent.ToolChanged(...))` → Presence Reaction 节流展示。

### 5.6 不要自己造的轮子

> 来源：`gradle/libs.versions.toml` + 实际 import 清单

| 能力 | 使用什么 | 版本 / 备注 |
|------|---------|------------|
| LLM HTTP 客户端 | **项目内 AnthropicMessagesLLMClient** | OkHttp 4.12.0 + 自研 SSE 解析，包成 Koog `LLMClient` |
| Streaming SSE → Koog StreamFrame | **项目内兼容层** | 走 `executeStreaming` 的 `Flow<StreamFrame>` |
| 对话历史 / Token 压缩 | Koog prompt 上下文 | **未启用压缩**；靠 `ConversationContextBuilder` 控制近 50 条 |
| 结构化 Tool Use | Koog `ToolRegistry` + `EventHandler.Feature` | 9 内置 + 远程 MCP（`McpRemoteTool`） |
| 错误重试 / 容错 | **当前以错误事件 + UI 提示为主** | 规划中；`AgentError` 已分 4 类（NetworkTimeout/RateLimited/ApiError/ParseError） |
| JSON 解析 | `kotlinx.serialization` 1.7.3 | `ReflectionResponse` 等 `@Serializable` 数据类 |
| 数据库 ORM | Room 2.8.4 | `MessageDao` / `ToolCallDao` / `AgentStateDao` 等 |
| 后台调度 | WorkManager 2.10.0 + hilt-work 1.3.0 | `DreamLoopWorker` (PeriodicWorkRequest **7 档可配置 + 立即触发**) + `ReminderNotificationWorker` (OneTimeWorkRequest) |
| DI 容器 | Hilt 2.59.2 | `@HiltViewModel` / `@AndroidEntryPoint` / `@HiltWorker` |
| 日志系统 | Timber 5.0.1 + 自研 `AppLogger` / `LogTags` | 走 Timber `Tree`，自研 `LogFieldSanitizer` 防 PII |
| 精确闹钟权限 | **自研**（`ChatPermissionPrompt` + `ChatPermissionType.EXACT_ALARM`） | 不用 Accompanist Permissions |
| 角色动画 | **Compose Canvas 临时替代** | `AuraPetAvatar.kt` / `PresenceAvatar.kt`；Lottie / Rive 资源**尚未实现**（仅有 `lottie-compose 6.6.0` 依赖） |
| 图片加载 | Coil 2.7.0 | 头像 / 记忆缩略图 |
| 相机 / 选图 | **Photo Picker 替代 CameraX** | `ActivityResultContracts.PickVisualMedia`；CameraX 1.4.0 依赖保留但未启用 |
| Coroutines | `kotlinx-coroutines-android` 1.8.1 | `viewModelScope` / `Dispatchers.IO` / `callbackFlow` |

---

## 六、工程化规范（Gradle 项目结构）

### 6.1 目录结构总览

```
project-root/
├── app/                          # 主模块
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/xiaoqi/companion/
│       │   │   ├── CompanionApplication.kt
│       │   │   ├── feature/          # 功能模块（UI）
│       │   │   ├── core/             # 核心业务逻辑
│       │   │   ├── data/             # 数据层
│       │   │   └── platform/         # 平台能力
│       │   └── res/
│       ├── test/                   # 单元测试
│       └── androidTest/            # 仪器测试
│
├── buildSrc/                      # 或 gradle/ 目录
│   └── src/main/kotlin/            #   VersionCatalog / Dependencies
│
├── gradle/
│   ├── libs.versions.toml         # 版本目录（统一版本号管理）
│   └── conventions/                # Convention Plugins（复用构建逻辑）
│       ├── android.application.gradle.kts
│       ├── android.compose.gradle.kts
│       ├── android.hilt.gradle.kts
│       ├── android.room.gradle.kts
│       └── android.test.gradle.kts
│
├── build.gradle.kts               # 根构建脚本（plugin 管理）
├── settings.gradle.kts            # 模块注册
├── gradle.properties              # Gradle 配置
├── local.properties               # 本地路径（SDK 等）
└── gradlew / gradlew.bat
```

### 6.2 Version Catalog（版本目录）

所有依赖版本集中在 `gradle/libs.versions.toml` 管理：

```toml
# gradle/libs.versions.toml

[versions]
# Kotlin & AGP
kotlin = "2.3.21"
agp = "9.2.0"

# Core Android
compileSdk = "36"
minSdk = "26"
targetSdk = "36"
ndk = "27.0.12077973"

# Jetpack Compose
compose-bom = "2026.05.00"
compose-activity = "1.9.3"
navigation = "2.8.4"
lifecycle = "2.8.7"

# DI
hilt = "2.59.2"

# Database
room = "2.8.4"

# Background
work = "2.10.0"

# Serialization
kotlinx-serialization = "1.7.3"

# Media / UI
lottie = "6.6.0"
coil = "2.7.0"

# Camera
camerax = "1.4.0"

# Logging
timber = "5.0.1"

# Agent Framework
koog = "0.8.0"

# Testing
junit = "4.13.2"
androidx-test-ext-junit = "1.2.1"
espresso = "3.6.1"
turbine = "1.1.0"
mockk = "1.13.13"

[libraries]
# --- Compose UI ---
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
compose-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version.ref = "compose-activity" }

# --- Coroutines ---
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlin" }

# --- DI: Hilt ---
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
hilt-compiler-androidx = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }

# --- Database: Room ---
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# --- Config: DataStore ---
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version = "1.1.1" }

# --- Background: WorkManager ---
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# --- Serialization ---
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# --- Media / Animation ---
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# --- Camera ---
camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }

# --- Logging ---
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# --- Agent Framework: Koog ---
koog-agents = { group = "ai.koog", name = "koog-agents", version.ref = "koog" }

# --- Testing ---
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext-junit" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[bundles]
compose = ["compose-ui", "compose-material3", "compose-navigation", "compose-viewmodel"]
hilt = ["hilt-android", "hilt-navigation-compose", "hilt-work"]
room = ["room-runtime", "room-ktx"]
camera = ["camera-core", "camera-camera2", "camera-lifecycle", "camera-view"]
test = ["junit", "turbine", "mockk"]
androidTest = ["androidx-test-ext-junit", "espresso-core"]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.7" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

### 6.3 Convention Plugins（约定插件）

将重复的构建逻辑抽取为 Convention Plugin，各模块 `build.gradle.kts` 极简：

#### `conventions/android.application.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaoqi.companion"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xiaoqi.companion"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

#### `conventions/android.compose.gradle.kts`

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(libs.bundles.compose)
    implementation(libs.compose.activity)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

#### `conventions/android.hilt.gradle.kts`

```kotlin
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)
}
```

#### `conventions/android.room.gradle.kts`

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    // 如果需要导出 Schema
    // room.schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
}
```

#### `conventions/android.test.gradle.kts`

```kotlin
dependencies {
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
}
```

### 6.4 app/build.gradle.kts（最终形态）

有了 Convention Plugin 后，app 模块的构建脚本非常干净：

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)

    // 应用自定义 Convention Plugins
    id("conventions.android.application")
    id("conventions.android.compose")
    id("conventions.android.hilt")
    id("conventions.android.room")
    id("conventions.android.test")
}

dependencies {
    // Compose UI
    implementation(libs.bundles.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // DI
    implementation(libs.bundles.hilt)

    // Database
    implementation(libs.datastore.preferences)
    implementation(libs.bundles.room)

    // Background
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Media / Animation
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    // Camera
    implementation(libs.bundles.camera)

    // Logging
    implementation(libs.timber)

    // Agent Framework: Koog
    implementation(libs.koog.agents)

    // Testing
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
}
```

### 6.5 包命名规范

```
com.xiaoqi.companion
├── CompanionApplication.kt          # Application 类
├── MainActivity.kt                  # Single Activity
│
├── feature                          # 功能模块（UI 层）
│   ├── chat
│   │   ├── ui/                     #   Compose Screen / ViewModel
│   │   └── navigation/             #   路由定义
│   ├── avatar/
│   ├── memory_room/
│   ├── settings/
│   └── onboarding/
│
├── core                             # 核心业务逻辑
│   ├── companion/                  #   Agent 主循环
│   │   ├── CompanionRuntime.kt
│   │   ├── KoogAgentFactory.kt
│   │   └── model/                  #     AgentEvent / UserInput / AgentState
│   ├── emotion/                    #   情绪状态机
│   │   ├── EmotionStateMachine.kt
│   │   ├── EmotionState.kt
│   │   └── model/
│   ├── relationship/               #   关系系统
│   │   ├── RelationshipModel.kt
│   │   └── model/
│   ├── pulse/                      #   生命脉冲
│   │   ├── PulsePolicy.kt
│   │   └── PulseScheduler.kt
│   ├── prompt/                     #   Prompt 引擎
│   │   ├── PromptBuilder.kt
│   │   ├── templates/              #     Prompt 模板文件
│   │   └── system/                 #     系统 Persona 定义
│   ├── memory/                     #   记忆管理
│   │   ├── MemoryManager.kt
│   │   └── MemorySelector.kt
│   └── actions/                    #   动作分发
│       ├── ActionDispatcher.kt
│       └── model/
│
├── data                            # 数据层
│   ├── db/
│   │   ├── dao/
│   │   ├── entity/
│   │   ├── database/               #     CompanionDatabase + migrations
│   │   └── converter/              #     TypeConverter
│   ├── datastore/
│   │   └── AppPreferences.kt
│   ├── repository/
│   │   ├── MessageRepository.kt
│   │   ├── MemoryRepository.kt
│   │   ├── ConfigRepository.kt      #     LlmConfig 读写
│   │   └── AgentStateRepository.kt
│   └── assets/
│
└── platform                        # 平台能力封装
    ├── speech/
    │   ├── SystemSpeechInput.kt
    │   └── SystemSpeechOutput.kt
    ├── camera/
    │   └── CameraVisionProvider.kt
    ├── notification/
    │   └── NotificationHelper.kt
    ├── widget/
    │   └── CompanionWidget.kt
    └── permissions/
        └── PermissionHandler.kt
```

---

## 七、代码量估算

### 当前技术闭环 Demo

**9k - 17k 行**

当前已覆盖：Compose 聊天 UI、Koog Agent 调用、项目内 Anthropic Messages 兼容 LLM client、Room 持久化、DataStore 配置、结构化 Tool Use、简单记忆系统、GLM-5v-turbo Vision 底层接入预留。

### 可玩的情绪 MVP

**20k - 33k 行**

包含：聊天、表情动画、TTS 语音、agent_state 状态机、memories 记忆、life_events 事件线、secret notes 小纸条、return reaction 回归反应、WorkManager pulse 脉冲、通知系统、设置页（含模型切换）、CameraX 多模态基础接入。

### 接近产品化

**43k - 72k 行**

包含：完整 UI、隐私/导出/删除功能、CameraX 多模态完整体验、桌面小组件、多角色切换、高级动画、错误恢复、模型热切换（GLM ↔ Kimi）、崩溃分析、付费/订阅体系。

### 使用成熟包后的代码节省

| 领域 | 节省来源 |
|------|----------|
| **LLM 客户端** | **通过项目内兼容层集中封装，业务层无需直接处理 HTTP/SSE** |
| Streaming / 历史压缩 | Streaming 已接入；历史压缩当前配置为 NoCompression |
| Tool Use / 结构化输出 | Koog tools + 项目内 Anthropic Messages 兼容层 |
| 错误重试 / 容错 | 当前基础错误处理，重试策略待补 |
| 数据库 | Room 替代手写 SQLite |
| 后台任务 | WorkManager 替代 Service+AlarmManager |
| DI | Hilt 替代手动工厂 |
| 动画 | Lottie/Rive 替代自定义动画引擎 |
| 图片加载 | Coil 替代手动缓存 |
| 语音 | 系统 API 替代自研引擎 |
| 相机 | CameraX 替代 Camera2 原生 API |

自研代码集中在：

- 情绪状态机（EmotionStateMachine）
- 关系状态模型（RelationshipModel）
- 记忆选择器（MemorySelector）
- PromptBuilder（Prompt 组装引擎）
- PulsePolicy（生命脉冲策略）
- Action 映射与分发
- CompanionRuntime 主循环编排

---

## 八、最终依赖清单

### 必选依赖

```kotlin
// === UI (Compose) ===
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.navigation:navigation-compose
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.activity:activity-compose

// === 异步 ===
org.jetbrains.kotlinx:kotlinx-coroutines-android

// === DI (Hilt) ===
com.google.dagger:hilt-android
androidx.hilt:hilt-navigation-compose
androidx.hilt:hilt-work

// === 数据库 (Room) ===
androidx.room:room-runtime
androidx.room:room-ktx
androidx.datastore:datastore-preferences

// === 序列化 ===
org.jetbrains.kotlinx:kotlinx-serialization-json

// === 后台任务 ===
androidx.work:work-runtime-ktx

// === 媒体 / UI 资源 ===
com.airbnb.android:lottie-compose
io.coil-kt:coil-compose

// === 相机 ===
androidx.camera:camera-core
androidx.camera:camera-camera2
androidx.camera:camera-lifecycle
androidx.camera:camera-view

// === 日志 ===
com.jakewharton.timber:timber

// === Agent Framework (Koog) ===
ai.koog:koog-agents:0.8.0
```

### 测试依赖

```kotlin
// Unit Test
junit:junit
app.cash.turbine:turbine          // Flow 测试
io.mockk:mockk                   // Mock 框架

// Instrumented Test
androidx.test.ext:junit
androidx.test.espresso:espresso-core
```

> **不再需要的依赖：** Ktor Client（LLM 网络层由 Koog 内部处理）。如果后续有非 LLM 的网络需求（如 analytics 上报），按需单独引入。

---

## 九、参考链接

### Android 生态

- [Jetpack Compose](https://developer.android.google.cn/jetpack/compose)
- [Room 持久化库](https://developer.android.google.cn/jetpack/androidx/releases/room)
- [DataStore](https://developer.android.google.cn/topic/libraries/architecture/datastore)
- [WorkManager](https://android-docs.cn/develop/background-work/background-tasks/persistent/getting-started)
- [Hilt + Jetpack 集成](https://developer.android.google.cn/training/dependency-injection/hilt-jetpack)
- [CameraX](https://developer.android.google.cn/media/camera/camerax)
- [Gradle Version Catalog](https://developer.android.google.cn/build/migrate-to-catalogs)
- [Build Variant](https://developer.android.google.cn/build/build-variants)

### Agent / LLM

- [Koog (GitHub)](https://github.com/jetbrains/koog)
- [Koog 官方文档](https://docs.koog.ai/)
- [Anthropic Messages API 文档](https://docs.anthropic.com/en/api/messages)
- [Anthropic Vision（多模态）](https://docs.anthropic.com/en/docs/vision)
- [Anthropic Tool Use（结构化输出）](https://docs.anthropic.com/en/docs/tool-use)
- [Anthropic Streaming](https://docs.anthropic.com/en/api/streaming)
- [GLM-5v-turbo（智谱 AI）](https://open.bigmodel.cn/)
- [Kimi 2.6（Moonshot AI）](https://platform.moonshot.cn/)
