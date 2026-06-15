# Koog v0.8.0 ↔ Android 集成参考

> **版本**: Koog 0.8.0 | 项目: Aura Companion App | 最后更新: 2026-06-15
>
> 本文是 **Koog SDK 集成参考**：版本、官方对 Android 的立场、我们用了哪些 API、线程 / 生命周期规则、已知 SDK 问题。
>
> **Aura 编排层**（Provider 路由、graph strategy、event 翻译、tool 持久化、reflection、流式 UX 节流）见 [`docs/agent-architecture.md`](./agent-architecture.md)。本文不复述。
>
> 本文档基于以下材料交叉验证：
> - [Koog GitHub README](https://github.com/koog-ai/koog)（官方源码仓库）
> - [Koog 官方文档站](https://docs.koog.ai)（Quickstart / Agents / Prompt）
> - `javap -p -s` 从 Gradle 缓存 JAR 提取的完整 API 签名（见 `koog-api-reference.md`）
> - DroidKaigi 2025 演讲「Koog on Android」的公开摘要

---

## 目录

1. [Koog 对 Android 的官方立场](#1-koog-对-android-的官方立场)
2. [当前项目集成状态审计](#2-当前项目集成状态审计)
3. [我们用到的 Koog API 表面](#3-我们用到的-koog-api-表面)
4. [线程与调度器规则](#4-线程与调度器规则)
5. [Android 生命周期集成模式](#5-android-生命周期集成模式)
6. [已知问题与规避策略](#6-已知问题与规避策略)
7. [检查清单](#7-检查清单)
8. [附录 A：关键文件路径](#附录-a关键文件路径)

---

## 1. Koog 对 Android 的官方立场

### 1.1 KMP 多平台支持

Koog 是一个 **Kotlin Multiplatform (KMP)** 框架，官方声明支持以下目标平台：

| 平台 | 支持状态 | 说明 |
|------|---------|------|
| JVM | ✅ 完整支持 | Android 运行时即 JVM |
| Android | ✅ 一等公民 | 通过 JVM target 原生运行 |
| JS/WasmJS | ✅ 支持 | Web 端 |
| iOS | ✅ 支持 | Kotlin/Native |

**关键结论**: Koog 在 Android 上不是"勉强能用"，而是**原生目标平台之一**。所有 API 在 JVM target 下均可直接调用，无需任何 platform-specific shim。

### 1.2 官方推荐的 Android 集成方式

根据 GitHub README 和 DroidKaigi 2025 公开资料，Koog 团队推荐：

```
┌─────────────────────────────────────┐
│           Application / Activity     │
│         (Hilt @AndroidEntryPoint)    │
└──────────┬──────────────────────────┘
           │ DI 注入
   ┌───────▼────────┐
   │  ViewModel      │  ← viewModelScope 管理协程生命周期
   │  (ChatViewModel) │
   └───────┬────────┘
           │ 调用
   ┌───────▼────────┐
   │  Runtime Layer  │  ← 业务编排层（你的 CompanionRuntime）
   │  (单例/Singleton)│
   └───────┬────────┘
           │ 创建 + 执行
   ┌───────▼────────┐
   │  AIAgent        │  ← Koog 核心（每次请求创建或复用 Session）
   │  (Functional)    │
   └───────┬────────┘
           │ LLM 调用
   ┌───────▼────────┐
   │  PromptExecutor │  ← 我们用自研 AnthropicMessagesLLMClient
   └─────────────────┘
```

**原则**:
- **不要在 Activity/Fragment 中直接创建 AIAgent 或 LLM Client**
- **ViewModel 是协程生命周期的边界** — 使用 `viewModelScope.launch {}`
- **Runtime 层封装 Koog 细节** — ViewModel 只知道 `Flow<AgentEvent>`，不知道 AIAgent 存在

### 1.3 Anthropic 兼容层

Koog 自带 `AnthropicLLMClient`，但我们**没有用** Koog 官方的 client，原因：

1. GLM / Kimi 的 Anthropic 兼容端点与官方 API 有细节差异（SSE 事件格式、`tool_use` 块的 input 增量等），需要我们控制报文结构
2. 想要复用 OkHttp / 共用 HTTP client 配置 / 自定义错误码映射

我们改用自研的 `AnthropicMessagesLLMClient`（`core/llm/AnthropicMessagesLLMClient.kt`），包成 Koog `LLMClient` 接口，包进 `MultiLLMPromptExecutor`：

```kotlin
MultiLLMPromptExecutor(AnthropicMessagesLLMClient(apiKey, baseUrl))
```

`AnthropicMessagesLLMClient` 实现 Koog 的 `LLMClient` 三个方法：

- `execute(prompt, model, tools): List<Message.Response>` — 一次性
- `executeStreaming(prompt, model, tools): Flow<StreamFrame>` — SSE 流
- `moderate / models` — stub 即可

| 提供商 | Base URL | 协议 |
|--------|----------|------|
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v1` | Anthropic Messages 兼容 |
| Moonshot Kimi | `https://api.moonshot.cn/v1` | Anthropic Messages 兼容 |

> 当前项目 GLM / Kimi base URL 在 `ConfigRepository` 中按 provider 维护，`.env` / `BuildConfig.LLM_BASE_URL` 提供默认。

---

## 2. 当前项目集成状态审计

> Last verified: 2026-06-15. 详细编排逻辑（Pipeline / graph strategy / 事件翻译）见 [`docs/agent-architecture.md`](./agent-architecture.md)。

### 2.1 我们用到的 Koog 入口

| 入口 | 位置 | 状态 |
|------|------|------|
| `ai.koog.agents.core.agent.AIAgent` | `KoogAgentFactoryImpl.createAgent()` | ✅ `AIAgent.builder()` + 自定义 strategy |
| `ai.koog.agents.core.dsl.builder.strategy { }` | `streamingSingleRunStrategy()` | ✅ `nodeLLMRequestStreamingAndSendResults` + `nodeExecuteMultipleTools` + `nodeSendToolResult` |
| `ai.koog.agents.core.tools.ToolRegistry` | `CompanionToolRegistry.create()` | ✅ 9 内置 + 远程 MCP 工具 |
| `ai.koog.agents.features.eventHandler.feature.EventHandler` | `install { install(EventHandler.Feature) { … } }` | ✅ `onToolCallStarting/Completed/Failed/ValidationFailed` + `onLLMStreamingFrameReceived` |
| `ai.koog.prompt.executor.model.PromptExecutor` | `KoogPromptExecutorFactory.create()` | ✅ 包成 `MultiLLMPromptExecutor` |
| `ai.koog.prompt.executor.model.executeStructured` | `KoogPromptExecutorWrapper.runStructured()` | ✅ 用于 memory reflection |
| `ai.koog.prompt.executor.clients.LLMClient` | `AnthropicMessagesLLMClient : LLMClient()` | ✅ 自研 OkHttp + SSE |
| `ai.koog.prompt.dsl.prompt { }` | `BuiltPrompt.toKoogAgentPrompt()` | ✅ `system {}` / `user { text + image }` |
| `ai.koog.prompt.llm.LLModel` + `LLMProvider` + `LLMCapability` | `KoogPromptExecutorWrapper.model` | ✅ `LLMCapability.Vision.Image` 启用图片 |
| `ai.koog.prompt.streaming.StreamFrame` | `onLLMStreamingFrameReceived` 回调 | ✅ `TextDelta` / `TextComplete` |

### 2.2 我们 **没有** 用的 Koog 入口

- `ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient`（官方 client）— 见 §1.3
- `AIAgentHelper.functionalAgent()` 的一行接入 — 我们需要 graph 定制 + event 翻译
- `SingleRunStrategy` 预设 — 我们自写 `streamingSingleRunStrategy`
- `Agent` 的 M0 老接口（pre-Agent-Core）— 当前项目使用 Agent Core 的 `AIAgent`

### 2.3 当前已验证

- ✅ `MainActivity` 只负责 `setContent`，不直接构造 LLM client
- ✅ `ChatViewModel` 通过 `SendMessageUseCase` 委托给 `CompanionRuntime`
- ✅ `KoogAgentFactoryImpl` 走 `AIAgent.builder()` + 自定义 strategy + `EventHandler.Feature`
- ✅ `CompanionRuntime.send()` 返回 `Flow<AgentEvent>`，支持 `Streaming / ToolCallUpdated / ToolStarted / ToolFinished / MemorySaved / Complete / Error`
- ✅ 工具调用状态经 `ToolCallRecorder` 写 Room，UI 上能看到 `MessageBubble` 的 tool status
- ✅ 视觉输入经 `LLMCapability.Vision.Image` + `ContentPart.Image(AttachmentContent.Binary.Base64)` 完整跑通
- ✅ LLM 连通性检查（`LlmConnectivityChecker`）能在设置页 "Test connection" 按钮里区分 200 / 401 / 网络失败
- ✅ 本地模型分流（`LlmProvider.LOCAL_QWEN` 走 `ReactiveCompanion`，不进 Koog）
- ✅ `./gradlew.bat testDebugUnitTest` 于 2026-06-15 通过（**372 测试 / 0 失败**；含 11 个 M4 vision memory 用例）

### 2.4 仍待补

- ⏳ 进程死亡后的输入框草稿（`SavedStateHandle` 接入）
- ⏳ EmotionMachine / RelationshipModel 序列化到 DataStore（进程死亡后情感/关系状态归零）
- ⏳ GLM / Kimi 在生产端的流式端到端手动验证（连通性检查 OK，但完整 streaming 链路在真机还需补一次手动跑）

---

## 3. 我们用到的 Koog API 表面

> 这一节是 **API 速查**，每条都标了 Aura 的使用位置。
> 完整签名见 [`docs/koog-api-reference.md`](./koog-api-reference.md)（基于 `javap` 提取的 JAR 签名）。

### 3.1 AIAgent 构造

```kotlin
AIAgent.builder()
    .promptExecutor(executor)              // MultiLLMPromptExecutor
    .llmModel(model)                        // LLModel(provider, id, capabilities)
    .prompt(prompt)                         // ai.koog.prompt.Prompt
    .toolRegistry(toolRegistry)             // 9 内置 + 远程 MCP,或 ToolRegistry.EMPTY
    .maxIterations(12)                      // 硬上限防 tool loop
    .id("companion-agent-${provider}")      // 仅用于日志
    .graphStrategy(streamingSingleRunStrategy())  // 自定义 strategy
    .install {
        install(EventHandler.Feature) {
            onToolCallStarting { ctx -> ... }
            onToolCallCompleted { ctx -> ... }
            onToolCallFailed { ctx -> ... }
            onToolValidationFailed { ctx -> ... }
            onLLMStreamingFrameReceived { ctx -> ... }
        }
    }
    .build()
```

**关键 trade-off**：

| 选择 | 理由 |
|------|------|
| `.graphStrategy(...)` 而不是 `SingleRunStrategy` | 需要"tool result 后再次 LLM 决定 tool 还是 finish"的循环 |
| `EventHandler.Feature` 而不是 `agent.onEachEvent` | 后者是 `Flow<AgentEvent>` 旧 API（pre-Agent-Core），当前是 Agent Core 的 install DSL |
| `toolRegistry = ToolRegistry.EMPTY if hasImage \|\| !allowTools` | Vision 输入 / 反射时禁 tool，避免 LLM 在 vision 上下文里调 tool 出错 |

### 3.2 Graph Strategy

```kotlin
strategy<String, String>("single_run_streaming_tools") {
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

**Edge 表**：

| 边 | 触发 | 行为 |
|----|------|------|
| `nodeStart → nodeCallLLM` | 进入 | 第一次 LLM 流式请求 |
| `nodeCallLLM → nodeExecuteTool` | LLM 返回 tool_call | 串行执行（`parallelTools = false`） |
| `nodeCallLLM → nodeFinish` | LLM 返回纯文本 | 聚合 assistant messages 退出 |
| `nodeExecuteTool → nodeSendToolResult` | tool 执行完 | 结果写回 prompt |
| `nodeSendToolResult → nodeExecuteTool` | LLM 再次要 tool | 循环 |
| `nodeSendToolResult → nodeFinish` | LLM 返回纯文本 | 聚合退出 |

**详细推理和与 CompanionRuntime 的对接**见 [`docs/agent-architecture.md` §4](./agent-architecture.md#4-koog-graph-strategy)。

### 3.3 BuiltPrompt → Prompt 转换

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

### 3.4 Structured Output（reflection 用）

```kotlin
val response = executor.executeStructured(
    prompt = structuredPrompt.toKoogAgentPrompt(),
    model = model,
    serializer = ReflectionResponse.serializer(),
    examples = examples,   // List<ReflectionResponse>
).getOrThrow().data
```

`examples` 用于 few-shot 提示，从 yml 加载；`serializer` 必须是 `@Serializable` 类型（`kotlinx.serialization`）。

---

## 4. 线程与调度器规则

### 4.1 核心规则

| 操作 | 推荐 Dispatcher | 原因 |
|------|----------------|------|
| `agent.run()` / `client.executeStreaming()` | **`Dispatchers.IO`** | LLM 调用是 I/O 密集型网络操作 |
| UI 状态更新 (`_uiState.update {}`) | **`Dispatchers.Main`** (自动切回) | Compose State 只能在主线程读写 |
| Room 数据库操作 | **`Dispatchers.IO`** | Room suspend 函数已内部切换，但显式指定更安全 |
| DataStore 读写 | **`Dispatchers.IO`** | 同上 |

### 4.2 绝对禁止的操作

```kotlin
// ❌ 禁止：在主线程执行 LLM 调用
withContext(Dispatchers.Main) { agent.run(prompt) }

// ❌ 禁止：runBlocking 在 Android 主线程（已知死锁 bug KG-750）
runBlocking { agent.run(prompt) }

// ❌ 禁止：自建 CoroutineScope 无 lifecycle 管理
private val scope = CoroutineScope(Dispatchers.Main)
```

### 4.3 已知 Bug：KG-750 (Deadlock)

**来源**: Koog GitHub Issues #750

**现象**: 当 `runBlocking` 与 `ExecutorService` 在特定线程组合下使用时，
会导致线程死锁。症状为应用卡死（ANR）。

**触发条件**:
- 在主线程或其他受限 dispatcher 上调用 `runBlocking`
- 内部使用了 `ExecutorService` based 的调度器

**规避方法**:
1. **永远不要在 Android 上使用 `runBlocking`** — 当前项目唯一例外是 `CompanionToolRegistry.addRemoteMcpTools` 的 `runBlocking { mcpServerListRepository.readAll() }`，但它跑在 Hilt 提供的工具创建入口里，**不**与 LLM call 共线程，已在 §4.4 标注
2. 所有 LLM 调用使用 `suspend fun` + `viewModelScope.launch {}`
3. 如需同步等待（极少场景），使用 `runInterruptible` 或自定义 Channel

### 4.4 `runBlocking` 例外：MCP tool 列表加载

```kotlin
private fun addRemoteMcpTools(builder: ToolRegistryBuilder) {
    val servers = runBlocking { mcpServerListRepository.readAll() }   // ← 唯一例外
    // ... listTools / tool 注册
}
```

- **位置**: `CompanionToolRegistry.addRemoteMcpTools`
- **原因**: `ToolRegistry.create()` 是 `fun create(): ToolRegistry`（非 suspend），而 `mcpServerListRepository.readAll()` 是 suspend + Room
- **安全条件**: 在 `koogAgentFactory.create(config)` 时同步执行，调用方在 `CompanionRuntime.send()` 内位于 `Dispatchers.IO` 上下文，**不**在主线程
- **已知风险**: MCP server 慢 / 不可达会阻塞 ToolRegistry 构造（agent 创建路径），影响首条消息延迟
- **后续方案**: 缓存 + 异步预热 + 单独 ToolRegistryManager

### 4.5 正确的 Dispatcher 使用模式

```kotlin
// ✅ ViewModel 标准模式
class ChatViewModel(private val runtime: CompanionRuntime) : ViewModel() {
    fun sendMessage(text: String) {
        viewModelScope.launch {                    // 默认 Dispatchers.Main.immediate
            runtime.send(UserInput.Text(text)).collect { event ->
                _uiState.update { ... }            // Main 线程,Compose 安全
            }
        }
    }
}

// ✅ Runtime 编排层 (主循环)
class CompanionRuntime(...) {
    open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
        val job = launch(Dispatchers.IO) {         // ← 显式 IO
            agent.runEvents(prompt).collect { event -> ... }
        }
        job.join()
    }
}
```

---

## 5. Android 生命周期集成模式

### 5.1 三层生命周期边界

```
┌─────────────────────────────────────────────────────┐
│  Activity / Fragment                                 │
│  生命周期: onCreate → onResume → onPause → onDestroy │
│  职责: 仅 setContent {} + collectAsStateWithLifecycle│
│  ⚠️ 不持有任何协程 Scope                              │
├─────────────────────────────────────────────────────┤
│  ViewModel                                          │
│  生命周期: 创建于 Activity 重建之间                    │
│  协程: viewModelScope (onCleared() 时自动取消)       │
│  职责: 发起调用 + 持有 uiState Flow                   │
├─────────────────────────────────────────────────────┤
│  CompanionRuntime (Singleton)                        │
│  生命周期: 应用级别 (Application)                     │
│  协程: 由调用方提供 scope (不自带)                    │
│  职责: 编排 Agent + 情感机 + 持久化                   │
└─────────────────────────────────────────────────────┘
```

### 5.2 取消处理

当用户离开聊天页面时：

```
用户按返回键 / 导航离开
    │
    ▼
NavHost 弹出 ChatScreen Composable
    │
    ▼
collectAsStateWithLifecycle 自动停止收集
    │ (lifecycle <= STARTED 时暂停)
    ▼
ViewModel.onCleared() → viewModelScope.cancel()
    │
    ▼
正在运行的 runtime.send() 协程被取消
    │
    ▼
callbackFlow 收到 CancellationException
    │
    ▼
LLM 调用被中断（底层 HTTP 连接关闭）
    │
    ▼
资源自动释放 ✅
```

**关键点**: 不需要手动取消逻辑。`viewModelScope` + `callbackFlow` 的组合天然支持结构化并发。

### 5.3 进程死亡恢复 (Process Death)

Android 系统可能在后台杀死应用进程。重启时：

| 数据 | 恢复方式 | 当前状态 |
|------|---------|---------|
| 聊天消息历史 | Room DB (`MessageRepository`) | ✅ 已实现 |
| 用户偏好设置 | DataStore (`AppPreferences`) | ✅ 已实现 |
| mood / intensity / relationship | AgentStateDao（每次 `Complete` 落） | ✅ 已实现 |
| Tool call 状态 | ToolCallDao（每次 hook 落） | ✅ 已实现 |
| 当前对话状态 (输入框文字) | `SavedStateHandle` | ⚠️ 待实现 |
| 进行中的 LLM 请求 | **不可恢复** | — 设计如此 |

**进程死亡时的 LLM 请求**: 这是正常行为。恢复后应显示一条系统消息：
"之前的回复因应用切换丢失了"，而不是尝试恢复中断的流。

---

## 6. 已知问题与规避策略

### 6.1 问题汇总表

| ID | 问题 | 影响 | 规避/解决方案 | 状态 |
|----|------|------|--------------|------|
| P1 | GLM base URL 依赖 `.env` / `BuildConfig.LLM_BASE_URL` | 未配置时会报 `LLM_BASE_URL is not configured` | 在 `.env` 中配置 `LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v1` | 需运行环境配置 |
| P2 | MainActivity 直接 LLM 调用反模式 | 绕过架构 | 已改为 `ChatViewModel` 驱动 | 已修复 |
| P3 | 历史上的 KoogAgentFactoryImpl stub | 所有 AI 回复为空 | 已替换为真实 `AIAgent.builder()` + 自定义 strategy | 已修复 |
| P4 | 进程死亡时输入框草稿丢失 | 旋转屏幕 / 进程死亡后输入框为空 | `SavedStateHandle` 接入 | 低优先级 |
| P5 | MCP server 慢 / 不可达 → ToolRegistry 构造阻塞 | 首条消息延迟 1-5s | 缓存 + 异步预热 | 中优先级 |
| KG-750 | `runBlocking` + `ExecutorService` 死锁 | ANR | 全项目禁用 `runBlocking`（§4.4 例外已标注） | 已规避 |

### 6.2 Anthropic 兼容端点的注意事项

智谱和 Kimi 的 Anthropic 兼容端点**并非 100% 兼容**，已知差异：

| 特性 | 标准 Anthropic API | 智谱兼容 | Kimi 兼容 |
|------|-------------------|---------|-----------|
| streaming | SSE | ✅ | ✅ |
| `tool_use` | ✅ | ⚠️ 可能不支持 | ⚠️ 可能不支持 |
| `cache_control` | ✅ | ❌ | ❌ |
| thinking / extended thinking | ✅ | ❌ | ❌ |
| 图片输入 (base64) | ✅ | ✅ | ✅ |

**建议**: 如果未来需要 `tool_use` 功能，需验证目标端点是否支持。
当前纯聊天 + tool 场景需在真机端到端验证。

### 6.3 `execute() vs executeStreaming()` 的 API 验证

从 JAR 提取的签名（`koog-api-reference.md` 第 13 节）：

```java
// LLMClient (基类)
public abstract java.util.List<ai.koog.prompt.message.Message$Response>
    execute(ai.koog.prompt.Prompt, ai.koog.prompt.llm.LLModel, java.util.List<ToolDescriptor>);

public abstract kotlinx.coroutines.flow.Flow<ai.koog.prompt.streaming.StreamFrame>
    executeStreaming(ai.koog.prompt.Prompt, ai.koog.prompt.llm.LLModel, java.util.List<ToolDescriptor>);
```

两个方法均已在 JAR 中确认存在。

---

## 7. 检查清单

### 7.1 本地运行前

- [ ] 确认 `.env` 文件中 `LLM_API_KEY` 已配置有效 API Key
- [ ] 确认 `.env` 文件中 `LLM_BASE_URL` 已配置有效 Anthropic Messages 兼容端点
- [ ] 确认网络权限 `<uses-permission android:name="android.permission.INTERNET" />` 已在 `AndroidManifest.xml` 中声明
- [ ] 确认明文流量允许（如果使用 HTTP 而非 HTTPS）：`android:usesCleartextTraffic="true"` （仅 debug）

### 7.2 已完成的 Koog 集成检查

- [x] `MainActivity` 只负责 `setContent`
- [x] `ChatViewModel` 通过 `SendMessageUseCase` 委托给 `CompanionRuntime`
- [x] `KoogAgentFactoryImpl` 创建真实 `AIAgent`（自定义 graph strategy + `EventHandler.Feature`）
- [x] `CompanionRuntime` 支持 `Streaming / ToolCallUpdated / ToolStarted / ToolFinished / MemorySaved / Complete / Error` 7 类事件
- [x] 工具调用状态经 `ToolCallRecorder` 写 Room 并显示在聊天 UI
- [x] `runStructured` 用于 memory reflection
- [x] LLM 连通性检查区分 200/401/网络失败
- [x] 本地模型（`LlmProvider.LOCAL_QWEN`）走 `ReactiveCompanion` 不进 Koog
- [x] `./gradlew.bat testDebugUnitTest` 于 2026-06-15 通过（**372 测试 / 0 失败**；含 11 个 M4 vision memory 用例）

### 7.3 后续待补

- [ ] 进程死亡后的输入框草稿（`SavedStateHandle`）
- [ ] 真机/模拟器手动验证 GLM/Kimi 端到端回复（含流式 + tool 链路）
- [ ] MCP server 列表异步预热
- [ ] 切换到 Agent Core 最新 API（跟踪 Koog 上游 release notes）

---

## 附录 A：关键文件路径

| 文件 | 路径 | 作用 |
|------|------|------|
| Agent 工厂接口 | `core/companion/KoogAgentFactory.kt` | `KoogAgentWrapper` / `KoogAgentFactory` / `KoogAgentEvent` sealed |
| Agent 工厂实现 | `core/companion/KoogAgentFactoryImpl.kt` | **Provider 路由 + AIAgent 构造 + graph strategy** |
| Runtime 编排 | `core/companion/CompanionRuntime.kt` | **Pipeline 编排**（详见 `agent-architecture.md` §1） |
| LLM Client | `core/llm/AnthropicMessagesLLMClient.kt` | 自研 OkHttp + SSE 实现 Anthropic Messages 协议 |
| Executor 工厂 | `core/llm/KoogPromptExecutorFactory.kt` | 把 client 包进 `MultiLLromptExecutor` |
| 连通性检查 | `core/llm/LlmConnectivityChecker.kt` | 设置页 "Test connection" 按钮 |
| Prompt 构建 | `core/prompt/PromptBuilder.kt` | BuiltPrompt 组装 |
| Tool 注册 | `core/tools/CompanionToolRegistry.kt` | 9 内置 + 远程 MCP tool |
| Tool 持久化 | `core/tools/ToolCallRecorder.kt` | tool call 写 Room（4 个状态） |
| 数据模型 | `core/companion/model/CoreModels.kt` | `UserInput` / `AgentEvent` / `AgentToolCall` / `ParsedOutput` |
| Output 解析 | `core/companion/OutputParser.kt` | regex 解析 `[mood:..][intensity:..]..` |
| Memory Reflection | `core/companion/ConversationReflection.kt` | `runStructured` 写记忆 |
| 配置仓库 | `data/repository/ConfigRepository.kt` | `LlmConfig` (provider/url/key/model) |
| Chat ViewModel | `feature/chat/ChatViewModel.kt` | UI 层状态编排（8 个 collector） |
| SendMessageUseCase | `feature/chat/usecase/SendMessageUseCase.kt` | `Flow<AgentEvent>` → `ChatUiState` 翻译 |
| 编排层文档 | [`docs/agent-architecture.md`](./agent-architecture.md) | **Provider 路由 / Graph Strategy / Tool / Reflection / 流式 UX** |
| 顶层架构 | [`docs/architecture.md`](./architecture.md) | 项目分层 + 数据层 + Agent Core |
| 完整 API 签名 | [`docs/koog-api-reference.md`](./koog-api-reference.md) | JAR 提取的精确 API 签名 |
| 版本目录 | `gradle/libs.versions.toml` | `koog = "0.8.0"` |

---

## 附录 B：依赖关系图

```
                    ┌──────────────┐
                    │  Activity     │
                    │ (setContent)  │
                    └──────┬───────┘
                           │ state collection
                    ┌──────▼───────────┐
                    │ ChatViewModel    │ ◀── viewModelScope
                    │   .uiState       │
                    └──────┬───────────┘
                           │ sendMessage(text, pendingImage)
                    ┌──────▼──────────────┐
                    │ SendMessageUseCase   │  ← 90ms 批量 / 30s 空闲 / markdown chunker
                    └──────┬──────────────┘
                           │ runtime.send(UserInput)
                    ┌──────▼──────────────────┐
                    │   CompanionRuntime       │ ◀── Singleton
                    │  Pipeline (10 步)        │   ├─ MemoryRepository
                    │                           │   ├─ ConversationContextBuilder
                    │                           │   ├─ PromptBuilder
                    │                           │   ├─ ConfigRepository
                    │                           │   ├─ MessageRepository
                    │                           │   ├─ OutputParser
                    │                           │   ├─ EmotionStateMachine
                    │                           │   ├─ RelationshipModel
                    │                           │   └─ ConversationReflection
                    └──────┬──────────────────┘
                           │ koogAgentFactory.create(config)
                    ┌──────▼──────────────┐
                    │  KoogAgentFactoryImpl  │
                    │  - LOCAL_QWEN → ReactiveCompanion
                    │  - else → KoogPromptExecutorWrapper
                    └──────┬──────────────┘
                           │ executorFactory.create(config)
                    ┌──────▼──────────────────┐
                    │  MultiLLMPromptExecutor  │
                    │  └─ AnthropicMessagesLLMClient (自研)
                    └──────┬──────────────────┘
                           │ HTTP POST /v1/messages  (SSE)
                    ┌──────▼──────────────────┐
                    │   GLM-5v-turbo / Kimi 2.6 │
                    └──────────────────────────┘
```

详见 [`docs/agent-architecture.md` §1](./agent-architecture.md#1-分层与依赖关系)。
