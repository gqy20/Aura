# Aura Agent 编排层技术文档

> **版本**: Aura Companion App | 最后更新: 2026-06-15
>
> 范围：Aura App **云端对话体**（Conversational Mind）的 Agent 编排层 ——
> 不含本地陪伴体（Continuous Presence）的运行时，那个在 `core/presence/runtime/`
> 与 `core/local/` 目录下，独立于 Koog，自有 task orchestration 协议。
>
> 关联文档：
> - [koog-android-integration.md](./koog-android-integration.md) — Koog SDK 集成参考
> - [architecture.md](./architecture.md) — 顶层架构 / 数据层 / Agent Core
> - [plan/dual-mind-architecture.md](./plan/dual-mind-architecture.md) — 双轨产品定位

---

## 0. 一句话定位

> **Aura 的云端 Agent 是一条"事件流"**：
> `ChatViewModel → SendMessageUseCase → CompanionRuntime → KoogAgentFactory → KoogPromptExecutorWrapper → LLM`，返回的不是一次性 `String`，而是 `Flow<AgentEvent>`，UI 按事件类型分别消费。

Koog 在这条链路里负责 **LLM 调度 + 工具循环**，其他所有事情（情绪机、关系模型、记忆、Prompt 组装、tool 注册、tool 持久化、output 解析、reflection、UI 渲染节流）都是 Aura 自研。

---

## 目录

1. [分层与依赖关系](#1-分层与依赖关系)
2. [Provider 路由：云端 vs 本地](#2-provider-路由云端-vs-本地)
3. [Prompt 管线](#3-prompt-管线)
4. [Koog Graph Strategy](#4-koog-graph-strategy)
5. [事件流与类型](#5-事件流与类型)
6. [Tool 系统](#6-tool-系统)
7. [Memory Reflection](#7-memory-reflection)
8. [流式 UX 集成](#8-流式-ux-集成)
9. [取消与错误处理](#9-取消与错误处理)
10. [关键文件速查](#10-关键文件速查)
11. [已验证约束](#11-已验证约束)

---

## 1. 分层与依赖关系

```
┌──────────────────────────────────────────────────────────────────┐
│  UI (Compose, NavHost)                                            │
│  ChatScreen / AuraHomeScreen / SettingsScreen / MemoryRoomScreen │
└─────────────────────────────┬────────────────────────────────────┘
                              │ collect ChatUiState
┌─────────────────────────────▼────────────────────────────────────┐
│  ChatViewModel                                                    │
│  8 个 init { launch } 订阅 + 1 个 SendMessageUseCase 委托         │
│  ─┬─ configStatus  ─┬─ agentStateDao  ─┬─ capabilityPrefs         │
│  ─┬─ messageRepo   ─┼─ memoryRepo    ─┬─ toolCalls (→Presence)    │
│  ─┬─ reminderRepo  ─┬─ insightRepo   ─┬─ moodTrend (28d)         │
└─────────────────────────────┬────────────────────────────────────┘
                              │ sendMessage(text, pendingImage)
┌─────────────────────────────▼────────────────────────────────────┐
│  SendMessageUseCase                                               │
│  - 90ms 批量渲染 + 30s 空闲超时 + markdown chunker                 │
│  - 把 Flow<AgentEvent> 翻译成 ChatUiState 增量                     │
└─────────────────────────────┬────────────────────────────────────┘
                              │ runtime.send(UserInput)
┌─────────────────────────────▼────────────────────────────────────┐
│  CompanionRuntime (Singleton)                                     │
│  Pipeline:                                                        │
│   1. 拉记忆 context  (MemoryRepository.selectPromptContext)       │
│   2. 拉近 50 条对话  (ConversationContextBuilder)                 │
│   3. build prompt  (PromptBuilder + SystemPersona)                │
│   4. load LlmConfig (ConfigRepository.getCurrentLlmConfig)        │
│   5. create Agent   (KoogAgentFactory)                            │
│   6. 写 user msg    (MessageRepository)                           │
│   7. agent.runEvents(prompt).collect → translate to AgentEvent   │
│   8. parse output   (OutputParser: regex 解析 [mood:..] 等标签)   │
│   9. update emotion + relationship                                │
│  10. conversation reflection → 调 runStructured 写记忆             │
│  11. persist assistant msg + emit Complete/MemorySaved            │
└─────────────────────────────┬────────────────────────────────────┘
                              │ create(config)
┌─────────────────────────────▼────────────────────────────────────┐
│  KoogAgentFactoryImpl                                             │
│  - provider == LOCAL_QWEN → ReactiveCompanion (本地,不进 Koog)    │
│  - else → KoogPromptExecutorWrapper + AIAgent.builder()          │
└─────────────────────────────┬────────────────────────────────────┘
                              │ executorFactory.create
┌─────────────────────────────▼────────────────────────────────────┐
│  KoogPromptExecutorFactory → MultiLLMPromptExecutor               │
│       └─ AnthropicMessagesLLMClient (自研 OkHttp + SSE)          │
└─────────────────────────────┬────────────────────────────────────┘
                              │ /v1/messages  (Anthropic Messages API)
┌─────────────────────────────▼────────────────────────────────────┐
│  GLM-5v-turbo / Kimi 2.6 (Anthropic 兼容端点)                     │
└──────────────────────────────────────────────────────────────────┘
```

**单一职责边界**：

| 层 | 唯一职责 | **不**应包含 |
|----|---------|-------------|
| `ChatViewModel` | 8 个 collector + 简单 UI 反馈 | LLM/Agent 调用（委托给 UseCase） |
| `SendMessageUseCase` | Flow\<AgentEvent\> → ChatUiState 翻译 | Koog 类型、Prompt 组装、tool 注册 |
| `CompanionRuntime` | Pipeline 编排（拉数据 → build → 调 → 解析 → 落库） | 任何 Compose/State |
| `KoogAgentFactoryImpl` | Provider 路由 + AIAgent 构造 | 业务编排 |
| `KoogPromptExecutorWrapper` | 流式 + tool + structured 三套 API | UI/Repository 依赖 |

---

## 2. Provider 路由：云端 vs 本地

`KoogAgentFactoryImpl.create(config)` 是路由总闸：

```kotlin
override fun create(config: LlmConfig): KoogAgentWrapper {
    if (config.provider == LlmProvider.LOCAL_QWEN) {
        // dual-mind Phase 0 临时二选一
        // Phase 1 拆开云端对话体/本地觉察面后,这条分支应改为
        //   "ReactiveCompanion 仅在 presence runtime 内被 LocalQwenExecutor 调"
        @Suppress("DEPRECATION_RENAMED_TO_REACTIVE_COMPANION")
        return ReactiveCompanion(engine = localQwenEngine, modelName = config.modelName)
    }
    return KoogPromptExecutorWrapper(
        config = config,
        executor = executorFactory.create(config),   // MultiLLMPromptExecutor
        toolRegistry = toolRegistry,                  // AgentToolRegistry
        toolCallRecorder = toolCallRecorder,          // ToolCallRecorder
    )
}
```

`KoogAgentWrapper` 接口统一 3 个调用入口：

```kotlin
interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String                        // 一次性
    suspend fun <T> runStructured(
        prompt: BuiltPrompt,
        serializer: KSerializer<T>,
        examples: List<T> = emptyList(),
    ): T                                                                 // 反射用
    fun runStreaming(prompt: BuiltPrompt): Flow<String>                 // 文本流
    fun runEvents(prompt: BuiltPrompt): Flow<KoogAgentEvent>             // 事件流（含 tool）
}
```

**设计要点**：
- `run()` 实际不被生产路径使用（保留给将来 script/quick path）。
- `runEvents()` 是聊天主路径的入口 — `CompanionRuntime` 只调它。
- `runStructured()` **专为 memory reflection 调** — `LlmConversationReflection.reflectAndSave` 在每次对话 turn 结束时调一次，传入两个 `ReflectionResponse` 的 few-shot examples。

---

## 3. Prompt 管线

### 3.1 数据结构

`BuiltPrompt` 是 Aura 自己的中间表示，最终在 `KoogPromptExecutorWrapper.toKoogAgentPrompt()` 转换成 Koog `Prompt`：

```kotlin
data class BuiltPrompt(
    val systemPrompt: String,
    val userMessage: String,
    val hasImage: Boolean = false,
    val imageBase64: String? = null,
    val imageMediaType: String? = null,
    val allowTools: Boolean = true,    // reflection 时设为 false
)
```

### 3.2 `PromptBuilder.build()` 拼装顺序

```kotlin
val sb = StringBuilder(SystemPersona.base)            // ① base persona（YAML 加载）
emotionContext?.let  { append(SectionTemplate("emotion_context")) }
relationshipContext?.let { append(SectionTemplate("relationship_context")) }

if (summaries.isNotEmpty())            { append("## ${summariesTitle}\n" + summaries.joinToString("\n")) }
if (recentConversation.isNotEmpty())   { append("## ${recentTitle}\n" + recent.joinToString("\n")) }
if (memories.isNotEmpty())             { append(SectionTemplate("memories" = memoryText)) }
if (toolsSectionTemplate.isNotEmpty()) { append(SectionTemplate(no placeholders)) }
```

**关键事实**：
- `SystemPersona` 模板从 `core/prompt/templates/SystemPersona` 加载，**所有文案都来自 assets/yml**，不在 Kotlin 字符串里。
- 占位符 `{{key}}` 走 `replacePlaceholders` — 缺 key 时输出 `[MISSING:key]` **而不是** 静默替换，方便一眼看出 yml 缺 slot。
- 记忆/总结/近况 **按"段"插入**，每段标题独立（便于 LLM 区分），不是塞在一个 big text block。

### 3.3 `MemoryRepository.selectPromptContext(userInput)` 策略

```kotlin
data class MemoryPromptContext(
    val memorySnippets: List<String>,     // 来自 SearchMemoryTool 风格的向量检索
    val summarySnippets: List<String>,    // 来自 SearchSummariesTool
)
```

只取 top-K（k 由 yml / config 决定），**不**全部塞进去 — 避免 system prompt 暴涨。

### 3.4 Vision 输入

```kotlin
is UserInput.Vision -> BuiltPrompt(
    systemPrompt = systemPrompt,
    userMessage = input.text,
    hasImage = true,
    imageBase64 = input.imageBase64,
    imageMediaType = input.mediaType,
)
```

转 Koog `Prompt`：

```kotlin
private fun BuiltPrompt.toKoogAgentPrompt() = prompt("companion-chat") {
    system(systemPrompt)
    if (hasImage && imageBase64 != null) {
        user {
            text(userMessage)
            image(
                ContentPart.Image(
                    content = AttachmentContent.Binary.Base64(imageBase64),
                    format = "base64",
                    mimeType = imageMediaType ?: "image/jpeg",
                )
            )
        }
    } else {
        user(userMessage)
    }
}
```

`LLModel` 必须声明 `LLMCapability.Vision.Image`，否则 Koog 会拒绝 `ContentPart.Image`。

### 3.5 M4：vision memory 持久化

`SendMessageUseCase` 在发 `UserInput.Vision` 之前**额外**起一个 fire-and-forget coroutine：

```kotlin
scope.launch {
    runCatching {
        memoryRepository.saveVisionMemory(
            summary = trimmed,
            imageBase64 = pendingImage.imageBase64,
            imageMediaType = pendingImage.mediaType,
            sourceMessageId = userMsg.id,
        )
    }.onFailure { AppLogger.warn(...) }  // 失败仅 log,不影响主流程
}
UserInput.Vision(...)
```

设计目的：vision 记忆是"看过的图"清单，**不**与"对话结论"竞争同一个 LLM turn；fire-and-forget 让主对话不被它拖慢。

---

## 4. Koog Graph Strategy

> **这一节是 Agent 编排层的"心脏"**。所有 streaming + tool + structured 都是这一段 DSL 决定的。

### 4.1 策略名与节点

```kotlin
private fun streamingSingleRunStrategy() = strategy<String, String>("single_run_streaming_tools") {
    val nodeCallLLM by nodeLLMRequestStreamingAndSendResults<String>()
    val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = false)
    val nodeSendToolResult by node<List<ReceivedToolResult>, List<Message.Response>> { results ->
        llm.writeSession {
            appendPrompt { tool { results.forEach { result(it) } } }
            requestLLMStreaming().toList().toMessageResponses().also { responses ->
                appendPrompt { messages(responses) }
            }
        }
    }

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(nodeCallLLM forwardTo nodeFinish onMultipleAssistantMessages { true }
        transformed { it.joinToString("\n") { it.content } })

    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(nodeSendToolResult forwardTo nodeFinish onMultipleAssistantMessages { true }
        transformed { it.joinToString("\n") { it.content } })
}
```

### 4.2 边（Edge）含义

| 边 | 触发条件 | 行为 |
|----|---------|------|
| `nodeStart → nodeCallLLM` | 进入 | 第一次 LLM 请求（流式） |
| `nodeCallLLM → nodeExecuteTool` | LLM 返回 tool_call | 串行执行所有 tool（`parallelTools = false`） |
| `nodeCallLLM → nodeFinish` | LLM 返回纯文本 | 聚合 assistant messages 退出 |
| `nodeExecuteTool → nodeSendToolResult` | tool 执行完 | 把结果写回 prompt |
| `nodeSendToolResult → nodeExecuteTool` | LLM 再次要 tool | 循环 |
| `nodeSendToolResult → nodeFinish` | LLM 返回纯文本 | 聚合退出 |

**`maxIterations = 12`** — 硬上限防止无限 tool 循环。

### 4.3 `parallelTools = false` 的产品决定

tool 串行而非并行 — 出于两个原因：
1. 多数 tool 是状态修改类（`create_local_reminder`），并行会出 race condition
2. 串行让 EventHandler 的 `onToolCallStarting / onToolCallCompleted` 一对一可读，UI 上能看到 tool A 结束才 tool B 开始

### 4.4 EventHandler：把 Koog 内部 hook 转 Aura 事件

```kotlin
.install {
    install(EventHandler.Feature) {
        onToolCallStarting { ctx ->
            toolCallRecorder.start(...)
            observer?.onToolUpdated(AgentToolCall(..., ToolCallStatus.STARTED))
        }
        onToolCallCompleted { ctx ->
            toolCallRecorder.succeed(...)
            observer?.onToolUpdated(AgentToolCall(..., ToolCallStatus.SUCCEEDED))
        }
        onToolCallFailed / onToolValidationFailed { ctx -> ... }
        onLLMStreamingFrameReceived { ctx ->
            when (val frame = ctx.streamFrame) {
                is StreamFrame.TextDelta    -> observer?.onTextDelta(frame.text)
                is StreamFrame.TextComplete -> observer?.onTextComplete(frame.text)
                else -> Unit
            }
        }
    }
}
```

**`TextComplete` 兜底**：GLM/Kimi 等 Anthropic 兼容端点**不一定**支持流式 — 一次性返回整个 text，但仍然触发 `TextComplete`。`runEvents` 内部的 observer 会把 `TextComplete` 转换成"一次性" `TextDelta`：

```kotlin
override fun onTextComplete(text: String) {
    if (!hasStreamingText && text.isNotBlank()) {
        hasStreamingText = true
        trySend(KoogAgentEvent.TextDelta(text))   // 当成一次性 delta 推
    }
}
```

这意味着 **即使后端只给非流式响应，UI 体验仍然是"渐进显示"** — 由 `SendMessageUseCase` 的 90ms 批量渲染做节流（见 §8）。

---

## 5. 事件流与类型

### 5.1 类型树

```
KoogAgentEvent (sealed)                    AgentEvent (sealed, Runtime 输出)
├── TextDelta(String)                      ├── Streaming(String delta)
├── ToolCallUpdated(AgentToolCall)         ├── ToolCallUpdated(AgentToolCall)
├── ToolStarted(String)                    ├── ToolStarted(String)
└── ToolFinished(String)                   ├── ToolFinished(String)
                                          ├── MemorySaved(Int)
                                          ├── Complete(ParsedOutput)
                                          └── Error(AgentError)
```

**`KoogAgentEvent`** = 来自 Koog 内部的 raw event，UI 不直接看。
**`AgentEvent`** = 经过 `CompanionRuntime` 加工后的事件，加了 `MemorySaved`（reflection 之后追加）和 `Complete` / `Error`（流结束 + 解析完成）。

### 5.2 完整事件流

```
User: "今天有点累,帮我记一下明天上午 9 点提醒"
 │
 ▼
[1] SendMessageUseCase.sendMessage
    └─ emit Assistant 占位 (isStreaming = true)
[2] CompanionRuntime.send
    ├─ memoryRepository.selectPromptContext → 注入到 system prompt
    ├─ promptBuilder.build → BuiltPrompt(allowTools = true)
    ├─ koogAgentFactory.create(config) → KoogPromptExecutorWrapper
    ├─ messageRepository.sendMessage(USER)
    └─ agent.runEvents(prompt).collect:
         │
         ├─ [KoogEvent] onLLMStreamingFrameReceived → TextDelta
         │     ▼
         │   [AgentEvent] Streaming("今天")
         │     ▼
         │   SendMessageUseCase: pendingStreamingContent.append + scheduleStreamingRender
         │     ▼
         │   UI: 90ms 后一次性 flush,Markdown chunker 渲染
         │
         ├─ [KoogEvent] onToolCallStarting → ToolCallUpdated(STARTED)
         │     ▼
         │   [AgentEvent] ToolCallUpdated
         │     ▼
         │   toolDisplayRegistry.label → "create_local_reminder 准备执行…"
         │     ▼
         │   PresenceController.reactionFor(PresenceEvent.ToolChanged(STARTED))
         │
         ├─ [KoogEvent] onToolCallCompleted → ToolCallUpdated(SUCCEEDED)
         │     ▼
         │   [AgentEvent] ToolCallUpdated
         │     ▼
         │   ToolCallRecorder.succeed → Room 落库 → toolCallRepository.observeBySession
         │     ▼
         │   ChatViewModel 第 7 个 collector: presenceReaction 更新
         │
         ├─ [KoogEvent] onLLMStreamingFrameReceived → TextDelta("已为你设置…")
         │     ▼
         │   … (流式继续)
         │
         ├─ Koog strategy: nodeSendToolResult → nodeFinish (没更多 tool)
         │     ▼
         ▼ runEvents 流结束
[3] CompanionRuntime:
    ├─ rawResponse 累加 = "已为你设置明天 9 点的提醒…[mood:calm][intensity:0.3][affinity:0.05]"
    ├─ outputParser.parse → ParsedOutput(textReply, emotionSignal, interactionSignal, actions)
    ├─ emotionMachine.feed → mood 更新
    ├─ relationshipModel.update → affinity 更新
    ├─ conversationReflection.reflectAndSave → runStructured 写记忆
    │     ▼
    │   [AgentEvent] MemorySaved(count = 1) → UI 显示 "已记住 1 条"
    ├─ messageRepository.saveAssistantMessage
    └─ [AgentEvent] Complete(parsed)
         ▼
    SendMessageUseCase: flushStreamingContent + 更新 CompanionStatus → AgentStateDao
```

---

## 6. Tool 系统

### 6.1 9 个内置 tool

| Tool | 类别 | 用途 |
|------|------|------|
| `SearchMemoryTool` | memory | 在 Room 记忆库里向量检索 |
| `SearchRecordsTool` | memory | 检索消息历史 |
| `SearchSummariesTool` | memory | 检索摘要 |
| `GetCurrentTimeTool` | context | 返回当前时间（让 LLM 知道"现在"） |
| `GetRecentInteractionContextTool` | context | 拉近 N 条对话 |
| `GetUserContextSettingsTool` | context | 读 5 个 capability 偏好 |
| `GetDeviceStatusTool` | context | 设备电量/网络（API 29+） |
| `GetWeatherTool` | context | 调外部 weather provider |
| `CreateLocalReminderTool` | action | 写本地 Reminder + AlarmManager |

### 6.2 Tool 注册：双源

`CompanionToolRegistry.create()` 合并两个来源：

```kotlin
val builder = ToolRegistry.builder()
    .tool(searchMemoryTool)
    .tool(searchRecordsTool)
    // ...9 个内置 tool
    .tool(createLocalReminderTool)

addRemoteMcpTools(builder)   // ← 远程 MCP
return builder.build()
```

`addRemoteMcpTools` 遍历所有 `enabled=true && isReady=true` 的 MCP server，**对每个 server 单独**调 `listTools`：

- 任何单个 server 失败 → 仅 log warn，不影响其他 server
- 用 `runBlocking` 包 `mcpServerListRepository.readAll()` — 这是 **Hilt entry point**，必须能同步返回 ToolRegistry

**已知妥协**：`ToolRegistry.create()` 用 `runBlocking` 读 MCP server 列表。在 `koogAgentFactory.create(config)` 时同步调用 — 整个 agent 构建路径会**潜在阻塞**直到 MCP listTools 返回。这是当前实现的 trade-off：

- ✅ 优点：ToolRegistry 一次构建、agent 生命周期内不变；MCP server 列表改变需要重启 agent。
- ⚠️ 风险：MCP server 慢 / 不可达 → 整个聊天页首条消息会卡 1-5 秒。
- 📋 后续：缓存 + 异步预热 + 单独的 MCP Tool Registry manager。

### 6.3 Tool 调用持久化

`ToolCallRecorder` 写在 `core/tools/ToolCallRecorder.kt` —— 把每次 tool call 写到 Room (`tool_calls` 表)：

```kotlin
suspend fun start(sessionId, callId, toolName, argumentsJson) {
    dao.insert(ToolCallEntity(id = callId, status = "RUNNING", ...))
}
suspend fun succeed(callId, resultJson) { dao.updateResult(..., status = "SUCCESS", ...) }
suspend fun fail(callId, errorMessage) { dao.updateResult(..., status = "FAILED", ...) }
```

**`callId` 来源**：`context.toolCallId ?: context.eventId` — 优先用 Koog 自己的 tool_call id，缺则用 event id 兜底。

**写入触发点**：`EventHandler.Feature.onToolCallStarting/Completed/Failed/ValidationFailed` 4 个 hook。

### 6.4 Tool 状态 → Presence

`ChatViewModel` 的第 7 个 collector：

```kotlin
toolCallRepository.observeBySession(DEFAULT_SESSION_ID).collect { calls ->
    val latestCall = calls.firstOrNull()
    val reaction = latestCall?.let {
        presenceController.reactionFor(PresenceEvent.ToolChanged(name, status))
    }
    val accepted = reaction?.takeIf { shouldShowPresenceReaction(it, state) }
    state.copy(presenceReaction = accepted)
    if (accepted) clearPresenceReactionLater()
}
```

Presence 反应用 `PresenceReactionPolicy.shouldShow` 节流（同类型 reaction 1s 内不重复展示）。

---

## 7. Memory Reflection

### 7.1 触发点

`CompanionRuntime.send()` 在 `emotionMachine.feed + relationshipModel.update` **之后**调一次：

```kotlin
val savedMemoryCount = runCatching {
    conversationReflection.reflectAndSave(
        input = ConversationReflectionInput(
            userInput = input,
            assistantReply = finalParsed.textReply,
            sourceMessageIds = listOfNotNull(userMessageId, assistantMessageId),
        ),
        config = config,
        agent = agent,    // ← 同一个 agent 实例,调 runStructured
    ).savedMemoryCount
}.getOrDefault(0)
if (savedMemoryCount > 0) {
    trySend(AgentEvent.MemorySaved(savedMemoryCount))
}
```

**注意**：reflection **不**走 chat 主 turn 的 graph strategy — 它直接调 `agent.runStructured(prompt, serializer, examples)`，绕开 streaming / tool loop。

### 7.2 流程

```
LlmConversationReflection.reflectAndSave(input, config, agent):
  prompt = BuiltPrompt(
    systemPrompt = SystemPersona.reflectionSystemPrompt.replace("{{now_millis}}", now),
    userMessage = SystemPersona.reflectionUserTemplate
      .replace("{{input_type}}", "Vision" / "Text" / "Speech")
      .replace("{{user_message}}", userInput.content)
      .replace("{{assistant_reply}}", assistantReply),
    allowTools = false,   // ← 关键:不允许调 tool,否则递归
  )
  response = agent.runStructured(
    prompt = prompt,
    serializer = ReflectionResponse.serializer(),
    examples = [emptyReflectionExample, saveReflectionExample],
  )
  for (memory in response.memories.filter { it.shouldSave }.take(3)) {
    memoryRepository.saveMemory(SaveMemoryRequest(...))
  }
```

### 7.3 关键决策

- **`allowTools = false`**：reflection LLM 是"打分员"，不该再调 tool 拉数据 — 看到的应该就是 (user, assistant) pair 本身
- **`MAX_MEMORIES_PER_TURN = 3`**：硬上限，一次最多存 3 条
- **`runCatching` 包裹**：reflection 失败不阻塞主 turn — 主 turn 仍然 emit `Complete(parsed)`，只是不 emit `MemorySaved`
- **同 agent 实例**：用同一个 `KoogAgentWrapper`（即同一个 `MultiLLMPromptExecutor` + 同一个 `AnthropicMessagesLLMClient`），省一个 HTTP 连接
- **few-shot 来自 yml**：`SystemPersona.reflectionExamples["reflection_empty"]` 和 `["reflection_save"]` 两个示例；yml 解析失败时降级到 hard-coded fallback

---

## 8. 流式 UX 集成

### 8.1 90ms 批量 + 48 字符阈值

`SendMessageUseCase` 拿到 `AgentEvent.Streaming(delta)` 时：

```kotlin
is AgentEvent.Streaming -> {
    resetIdleTimer()
    assistantContent += event.delta
    pendingStreamingContent.append(event.delta)
    scheduleStreamingRender()      // ← 看下面
}
```

`scheduleStreamingRender` 决定何时 flush：

```kotlin
fun scheduleStreamingRender() {
    // 阈值触发:已攒 48 字符就立即 flush
    if (pendingStreamingContent.length >= STREAMING_RENDER_BATCH_CHARS) {
        flushStreamingContent(); return
    }
    // 否则 90ms 定时 flush
    if (streamingRenderJob?.isActive == true) return
    streamingRenderJob = scope.launch {
        delay(STREAMING_RENDER_BATCH_MS)   // 90ms
        flushStreamingContent()
    }
}
```

**为什么不直接 setState**：高频 setState（每 token 一次）会触发 Compose 重组 + Markdown 重解析，掉帧。90ms ≈ 11 fps，是肉眼"流畅"的最低线，又不至于太卡。

### 8.2 30s 空闲超时

```kotlin
fun resetIdleTimer() {
    timedOut = false
    idleTimeoutJob?.cancel()
    idleTimeoutJob = scope.launch {
        delay(STREAMING_IDLE_TIMEOUT_MS)   // 30s
        timedOut = true
        AppLogger.warn("streaming_idle_timeout", ...)
    }
}
```

每次收到新 delta 重置；30s 没收新 delta → `timedOut = true` → 流结束后 `finishWithError`。

### 8.3 Markdown chunker

`StreamingMarkdownChunker` 把 90ms 内的 raw text 按 ``` ` ```、`#`、空行切成 "committed blocks" + "draft"，目的是：
- 已经稳定的 block（前面有换行） → 立即解析 markdown 渲染
- 还在同一行/同一 code fence 内的 → 当 draft，等下一 batch

`MessageBubble` 收到 `renderBlocks` + `renderDraft` 后分别渲染 — 用户能看到"前面内容已成型、后面还在打字"的效果。

### 8.4 错误恢复

```kotlin
is AgentEvent.Error -> {
    AppLogger.warn("agent_error_received", ...)
    finishWithError(formatError(event.error))
}
```

`finishWithError` 行为：
- 如果 assistant 已经有部分内容（`assistantContent.isNotBlank()`）→ 保留消息，把 `isStreaming = false`，`toolStatus = "回复未完整完成"`
- 如果 assistant 完全空 → 移除占位消息

→ 用户能"看到部分回复 + 知道中断了"，而不是"消息消失"。

---

## 9. 取消与错误处理

### 9.1 三层取消

| 触发 | 路径 |
|------|------|
| 用户按返回 / 离开聊天页 | `NavHost` 弹出 `ChatScreen` → `collectAsStateWithLifecycle` 停止 → `ViewModel.onCleared()` → `viewModelScope.cancel()` → `runtime.send` 的 `callbackFlow` 取消 → Koog `agent.run` 抛 `CancellationException` → HTTP 连接关闭 |
| 用户切到其他 app（onStop） | `collectAsStateWithLifecycle` 暂停收集（**不**取消协程）；回到前台时继续 |
| 进程死亡 | 同 §9.3 |

### 9.2 异常分类

```kotlin
val error = when (e) {
    is SocketTimeoutException -> AgentError.NetworkTimeout
    else -> AgentError.ApiError(e.message ?: "Unknown error")
}
```

| 错误 | 来源 | UI 表现 |
|------|------|---------|
| `NetworkTimeout` | OkHttp 60s call timeout | "Network timed out. Check your connection." |
| `ApiError(msg)` | HTTP 非 2xx / 解析失败 | msg（原始） |
| `RateLimited` | 预留，目前未触发 | "Too many requests. Try again later." |
| `ParseError` | `outputParser` 解析空 / raw 为空 | "Empty model response" / "Empty assistant reply" |

### 9.3 进程死亡

- **聊天历史** ✅ Room 自动恢复（`MessageRepository.getMessagesBySession` 重订阅）
- **mood / intensity / relationship** ✅ AgentStateDao 在每次 `Complete` 后写
- **输入框草稿** ⚠️ 当前用 `_uiState.inputText` 持有，进程死亡丢失 — 后续可挂 `SavedStateHandle`
- **进行中的 LLM 请求** ❌ 不可恢复（设计如此），但用户感知是"消息卡住一会儿后消失"，不会 crash

---

## 10. 关键文件速查

| 文件 | 路径 | 作用 |
|------|------|------|
| ChatViewModel | `feature/chat/ChatViewModel.kt` | UI 状态编排（8 个 collector） |
| SendMessageUseCase | `feature/chat/usecase/SendMessageUseCase.kt` | Flow\<AgentEvent\> → ChatUiState 翻译 |
| CompanionRuntime | `core/companion/CompanionRuntime.kt` | Pipeline 编排（10 步） |
| KoogAgentFactoryImpl | `core/companion/KoogAgentFactoryImpl.kt` | Provider 路由 + AIAgent 构造 |
| KoogAgentFactory | `core/companion/KoogAgentFactory.kt` | 接口 + KoogAgentEvent sealed |
| PromptBuilder | `core/prompt/PromptBuilder.kt` | BuiltPrompt 组装 |
| SystemPersona | `core/prompt/templates/SystemPersona.kt` | YAML persona/工具/reflection 模板 |
| OutputParser | `core/companion/OutputParser.kt` | regex 解析 `[mood:..][intensity:..]..` |
| LlmConversationReflection | `core/companion/ConversationReflection.kt` | runStructured 写记忆 |
| KoogPromptExecutorFactory | `core/llm/KoogPromptExecutorFactory.kt` | 创建 `MultiLLMPromptExecutor` |
| AnthropicMessagesLLMClient | `core/llm/AnthropicMessagesLLMClient.kt` | 自研 OkHttp + SSE 实现 |
| LlmConnectivityChecker | `core/llm/LlmConnectivityChecker.kt` | 设置页 "Test connection" 按钮 |
| CompanionToolRegistry | `core/tools/CompanionToolRegistry.kt` | 9 内置 + 远程 MCP tool 注册 |
| ToolCallRecorder | `core/tools/ToolCallRecorder.kt` | tool call 写 Room（4 个状态） |
| ConfigRepository | `data/repository/ConfigRepository.kt` | provider / model / apiKey / baseUrl |
| MessageRepository | `data/repository/MessageRepository.kt` | USER/ASSISTANT 消息读写 |
| MemoryRepository | `data/repository/MemoryRepository.kt` | 记忆库 + selectPromptContext |
| ToolCallRepository | `data/repository/ToolCallRepository.kt` | tool_calls 表的 Flow 订阅 |

---

## 11. 已验证约束

> 来源：`./gradlew.bat testDebugUnitTest` 于 2026-06-14 通过（41 个测试，0 失败）

| 约束 | 验证方式 | 状态 |
|------|---------|------|
| `MultiLLMPromptExecutor` + `AnthropicMessagesLLMClient` 可发 / 收 Anthropic Messages | `LLM_API_KEY` + `LLM_BASE_URL` 配置后端到端 | ✅ |
| `AIAgent.builder()` + `strategy("single_run_streaming_tools")` + EventHandler 完整流式 + tool | `KoogAgentFactoryImplTest` 模拟 executor | ✅ |
| `runStructured` 走 `executor.executeStructured` 反射 | `LlmConversationReflectionTest` 验证序列化 | ✅ |
| `maxIterations = 12` 硬上限防 tool loop | unit test + manual | ✅ |
| Tool call 状态经 `ToolCallRecorder` 落 Room | `ToolCallRecorderTest` | ✅ |
| 90ms 批量 + 30s 空闲超时 | `SendMessageUseCaseTest` | ✅ |
| `allowTools = false` reflection 不递归调 tool | `LlmConversationReflectionTest` | ✅ |
| Vision `ContentPart.Image` + `LLMCapability.Vision.Image` | manual（图片输入已可用） | ✅ |
| `LlmProvider.LOCAL_QWEN` 走 `ReactiveCompanion` 不进 Koog | `KoogAgentFactoryImplTest` | ✅ |
| LlmConnectivityChecker 区分 200/401/网络失败 | `LlmConnectivityCheckerTest` | ✅ |

---

## 附录 A：与 [koog-android-integration.md](./koog-android-integration.md) 的边界

| 关注点 | koog-android-integration.md | agent-architecture.md |
|--------|---------------------------|----------------------|
| Koog 框架本身（KMP、版本、官方推荐、Android 立场） | ✅ | — |
| Koog API 签名（jar 提取的精确签名） | ✅（见 koog-api-reference.md） | — |
| 我们用了 Koog 的哪几个 API（AIAgent.builder / EventHandler.Feature / strategy DSL） | 简表 | ✅ 详细使用 + 模式 |
| KG-750 runBlocking 死锁规避 | ✅ | — |
| Dispatcher 规则 | ✅ | — |
| Koog 已知 API bug | ✅ | — |
| Provider 路由（云端 vs 本地） | — | ✅ |
| Graph strategy 设计 / Edge 表 | — | ✅ |
| EventHandler 在 Aura 中的具体 hook | — | ✅ |
| Prompt 拼装顺序 / Vision / 占位符策略 | — | ✅ |
| Tool 注册 + 持久化 + Presence 联动 | — | ✅ |
| Memory reflection（runStructured 二次调用） | — | ✅ |
| 流式 UX 集成（90ms / 30s / Markdown chunker） | — | ✅ |
| UI / StateFlow / Compose | — | ✅（在 architecture.md） |
| 数据层（Room / DataStore） | — | ✅（在 architecture.md） |

