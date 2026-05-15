# Koog v0.8.0 <-> Android 集成指南

> **版本**: Koog 0.8.0 | 项目: Aura Companion App | 最后更新: 2026-05-15
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
3. [真实 Agent 实现要点](#3-真实-agent-实现要点)
4. [线程与调度器规则](#4-线程与调度器规则)
5. [Android 生命周期集成模式](#5-android-生命周期集成模式)
6. [流式输出在 Chat UI 中的集成](#6-流式输出在-chat-ui-中的集成)
7. [当前实现参考](#7-当前实现参考)
8. [已知问题与规避策略](#8-已知问题与规避策略)
9. [检查清单](#9-检查清单)

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
   │  AnthropicLLMClient │  ← 或其他 PromptExecutor 实现
   └─────────────────────┘
```

**原则**:
- **不要在 Activity/Fragment 中直接创建 AIAgent 或 LLM Client**
- **ViewModel 是协程生命周期的边界** — 使用 `viewModelScope.launch {}`
- **Runtime 层封装 Koog 细节** — ViewModel 只知道 `Flow<AgentEvent>`，不知道 AIAgent 存在

### 1.3 Anthropic 兼容层

Koog 的 `AnthropicLLMClient` 实现了 Anthropic Messages API 协议。本项目使用的智谱 GLM 和 Moonshot Kimi 均提供 **Anthropic 兼容端点**：

| 提供商 | Base URL | 兼容协议 |
|--------|----------|---------|
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v1` | Anthropic Messages API |
| Moonshot Kimi | `https://api.moonshot.cn/v1` | Anthropic Messages API |

> 当前项目通过自定义 `AnthropicMessagesLLMClient` 适配 Koog `PromptExecutor`，GLM 默认 base URL 由 `.env` / `BuildConfig.LLM_BASE_URL` 提供，Kimi base URL 在 `ConfigRepository` 中固定为 `https://api.moonshot.cn/v1`。

---

## 2. 当前项目集成状态审计

### 2.1 架构分层现状

| 层 | 文件 | 状态 | 说明 |
|----|------|------|------|
| UI 层 | `MainActivity.kt` | ✅ 正常 | 只负责 `setContent`，通过 Hilt 获取 `ChatViewModel` |
| UI 层 | `ChatScreen.kt` / `MessageBubble.kt` | ✅ 正常 | Compose UI，展示消息、输入框、工具状态 |
| ViewModel | `ChatViewModel.kt` | ✅ 正常 | 正确使用 viewModelScope + CompanionRuntime |
| Runtime | `CompanionRuntime.kt` | ✅ 已接入 | 构建 prompt、注入记忆、调用 Agent、解析输出、更新情绪/关系 |
| Agent 工厂 | `KoogAgentFactoryImpl.kt` | ✅ 已接入 | 使用 Koog `AIAgent`，支持流式文本与工具事件 |
| LLM client | `AnthropicMessagesLLMClient.kt` | ✅ 已实现 | Anthropic Messages 兼容请求、SSE streaming、tool schema、Vision content |
| Agent 接口 | `KoogAgentFactory.kt` | ✅ 设计合理 | 抽象层设计良好 |
| 配置 | `ConfigRepository.kt` | ✅ 可用 | DataStore + BuildConfig 组合读取 provider/key/model |
| Prompt | `PromptBuilder.kt` | ✅ 正常 | 正确构建 BuiltPrompt |
| DI | `AppModule.kt` | ✅ 正常 | Hilt 绑定完整 |

### 2.2 当前已验证事项

- `MainActivity` 已清理为 ViewModel 驱动，不再直接创建 LLM client。
- `KoogAgentFactoryImpl` 已不再是 stub，当前通过 `AIAgent.builder()` 构建真实 Agent。
- `CompanionRuntime.send()` 已返回 `Flow<AgentEvent>`，支持 `Streaming`、`Complete`、`Error` 与工具事件。
- `testDebugUnitTest` 和 `assembleDebug` 已在 2026-05-15 验证通过。

### 2.3 仍待完善

- 设置页尚未实现，用户不能在 UI 中修改 API Key / Provider / Model。
- CameraX 依赖和 Vision 数据结构已预留，但拍照/选图 UI 尚未实现。
- WorkManager pulse、语音、通知、角色表情层尚未实现。
- 进程死亡后的输入框草稿、情绪/关系状态恢复仍需补齐。

---

## 3. 真实 Agent 实现要点

> 当前项目已经采用真实 Koog `AIAgent` 集成；本节记录实现要点，历史上的 stub 替换任务已完成。

### 3.1 接口适配分析

需要桥接的两个接口体系：

```
你的抽象层                          Koog 实际 API
┌──────────────────┐              ┌─────────────────────────────┐
│ KoogAgentWrapper │              │ FunctionalAIAgent            │
│  run(prompt)      │─── 适配 ──▶│  .run(prompt: Prompt): String │
│  : String         │              │  .runStreaming(prompt): Flow  │
└──────────────────┘              └─────────────────────────────┘

BuiltPrompt (你的)                  Prompt (Koog)
├─ systemPrompt: String             ├─ messages: List<PromptMessage>
├─ userMessage: String              ├─ system: String?
├─ hasImage: Boolean                └─ tools: List<ToolDescriptor>
├─ imageBase64: String?
└─ imageMediaType: String?

LlmConfig (你的)                   AnthropicClientSettings (Koog)
├─ provider: LlmProvider            ├─ baseUrl: String
├─ baseUrl: String                  ├─ apiKey: String
├─ apiKey: String                   ├─ model: String?
├─ modelName: String                └─ timeout: Duration?
```

### 3.2 两种集成策略对比

#### 策略 A：简单包装（推荐起步）

用 `AIAgentHelper.functionalAgent()` 快速创建 Functional Agent，
将 `BuiltPrompt` 转换为 Koog `Prompt`，调用 `agent.run()` 获取完整响应。

**优点**: 最少代码量，5 分钟可跑通
**缺点**: 无流式输出，用户需等待完整响应
**适用**: 第一阶段验证，确认端到端通路

#### 策略 B：流式集成（生产目标）

使用 `AnthropicLLMClient.executeStreaming()` 直接获取 `Flow<StreamFrame>`，
在 CompanionRuntime 层将 StreamFrame 转换为 `AgentEvent.Streaming`。

**优点**: 流式输出，用户体验好
**缺点**: 需要改造 CompanionRuntime.send() 的返回类型语义
**适用**: 生产环境

**建议**: 先实现策略 A 验证通路，再升级到策略 B。

### 3.3 BuiltPrompt → Prompt 转换

Koog 的 `prompt {}` DSL 是构建 `Prompt` 的标准方式：

```kotlin
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.dsl.system
import ai.koog.prompt.dsl.user
import ai.koog.prompt.dsl.image

// 将 BuiltPrompt 转为 Koog Prompt
fun BuiltPrompt.toKoogPrompt(): Prompt {
    return prompt("chat") {
        system(systemPrompt)
        if (hasImage && imageBase64 != null) {
            user {
                text(userMessage)
                image(imageBase64!!, imageMediaType ?: "image/jpeg")
            }
        } else {
            user(userMessage)
        }
    }
}
```

> **验证依据**: `koog-api-reference.md` 第 13 节记录了 `prompt {}` DSL 的完整签名：
> `system(String)`, `user(String)`, `user(lambda)` (支持 text+image 组合)。

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
1. **永远不要在 Android 上使用 `runBlocking`**
2. 所有 LLM 调用使用 `suspend fun` + `viewModelScope.launch {}`
3. 如需同步等待（极少场景），使用 `runInterruptible` 或自定义 Channel

### 4.4 正确的 Dispatcher 使用模式

```kotlin
// ✅ 正确：ViewModel 中的标准模式
class ChatViewModel(
    private val runtime: CompanionRuntime,
) : ViewModel() {

    fun sendMessage(text: String) {
        viewModelScope.launch {
            // 自动运行在 Main，但 runtime.send() 内部会切换到 IO
            runtime.send(UserInput.Text(text)).collect { event ->
                // collect 回调在 Main（viewModelScope 的 Dispatcher）
                _uiState.update { /* 更新 UI */ }
            }
        }
    }
}

// ✅ 正确：Runtime 层的 IO 切换
class CompanionRuntime(...) {
    suspend fun send(input: UserInput): Flow<AgentEvent> = flow {
        // flow 构建器继承调用者的上下文
        // 但 LLM 调用应在 IO 上执行
        val result = withContext(Dispatchers.IO) {
            agent.run(prompt)
        }
        emit(AgentEvent.Complete(result))
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
flow {} 构建器收到 CancellationException
    │
    ▼
LLM 调用被中断（底层 HTTP 连接关闭）
    │
    ▼
资源自动释放 ✅
```

**关键点**: 不需要手动取消逻辑。`viewModelScope` + `flow` 的组合天然支持结构化并发。

### 5.3 进程死亡恢复 (Process Death)

Android 系统可能在后台杀死应用进程。重启时：

| 数据 | 恢复方式 | 当前状态 |
|------|---------|---------|
| 聊天消息历史 | Room DB (`MessageRepository`) | ✅ 已实现 |
| 用户偏好设置 | DataStore (`AppPreferences`) | ✅ 已实现 |
| 当前对话状态 (输入框文字) | `SavedStateHandle` | ⚠️ 待实现 |
| 正在进行中的 LLM 请求 | **不可恢复** | — 设计如此 |
| EmotionMachine 状态 | 可序列化到 DataStore | ⚠️ 待实现 |
| RelationshipModel 状态 | 可序列化到 DataStore | ⚠️ 待实现 |

**进程死亡时的 LLM 请求**: 这是正常行为。恢复后应显示一条系统消息：
"之前的回复因应用切换丢失了"，而不是尝试恢复中断的流。

---

## 6. 流式输出在 Chat UI 中的集成

### 6.1 Koog StreamFrame 类型层次

```
StreamFrame (sealed interface)
├── TextDelta(text: String)          // 文本增量 ← 主要关注
├── TextComplete(fullText: String)   // 文本完成
├── ToolCall(id, name, arguments)    // 工具调用
├── ToolCallResult(id, output)       // 工具结果
└── End                               // 流结束
```

> 来源: `koog-api-reference.md` 第 13 节 + JAR javap 提取

### 6.2 流式集成架构

```
用户发送 "你好"
    │
    ▼
ChatViewModel.sendMessage("你好")
    │
    ▼
CompanionRuntime.send(UserInput.Text("你好"))
    │
    ├── emit(AgentEvent.Streaming("你"))          → UI 显示 "你"
    ├── emit(AgentEvent.Streaming("好"))          → UI 显示 "你好"
    ├── emit(AgentEvent.Streaming("！"))          → UI 显示 "你好！"
    ├── emit(AgentEvent.Streaming("我是"))        → UI 显示 "你好！我是"
    │   ... (持续流式)
    │
    └── emit(AgentEvent.Complete(parsedOutput))   → UI 最终化，isStreaming=false
```

### 6.3 CompanionRuntime 流式改造

当前的 `CompanionRuntime.send()` 返回 `Flow<AgentEvent>`，已预留流式扩展点。
需要将内部的 `agent.run()` (同步返回 String) 替换为流式调用：

```kotlin
// 改造后的 CompanionRuntime.send() (伪代码，详见第 7 节完整实现)
open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
    val prompt = promptBuilder.build(input, emotionCtx, relationCtx)
    val config = configRepository.getCurrentLlmConfig().first()

    // 使用流式执行
    client.executeStreaming(prompt.toKoogPrompt(), llmModel).collect { frame ->
        when (frame) {
            is StreamFrame.TextDelta -> trySend(AgentEvent.Streaming(frame.text))
            is StreamFrame.End -> {
                val parsed = outputParser.parse(accumulatedText)
                emotionMachine.feed(parsed.emotionSignal)
                relationshipModel.update(parsed.interactionSignal)
                trySend(AgentEvent.Complete(parsed))
            }
            is StreamFrame.ErrorFrame -> trySend(AgentEvent.Error(...))
            else -> {}
        }
    }
    close()
}
```

### 6.4 ChatViewModel 中的消费模式（已正确实现）

当前 `ChatViewModel.kt` 的消费逻辑已经是正确的：

```kotlin
runtime.send(UserInput.Text(trimmed)).collect { event ->
    when (event) {
        is AgentEvent.Streaming -> {
            // 追加文本到 assistant 消息
            assistantContent += event.delta
            _uiState.update { /* 更新 messages 中对应的消息 content */ }
        }
        is AgentEvent.Complete -> {
            // 最终化：用 parsed.textReply 替换（可能包含后处理修正）
            _uiState.update { /* isStreaming = false */ }
        }
        is AgentEvent.Error -> {
            // 移除 placeholder，显示错误
            _uiState.update { /* error = ... */ }
        }
    }
}
```

**无需修改 ChatViewModel** — 它已经正确消费 `Flow<AgentEvent>`。

---

## 7. 当前实现参考

当前实现位于 `app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt`：

- `KoogAgentFactoryImpl.create(config)` 创建 `KoogPromptExecutorWrapper`。
- `KoogPromptExecutorWrapper` 使用 `AIAgent.builder()` 配置 prompt executor、LLM model、tool registry 和 event handler。
- `runEvents(prompt)` 通过 `callbackFlow` 将 Koog streaming frame 和 tool call 生命周期转换为项目内 `KoogAgentEvent`。
- `BuiltPrompt.toKoogAgentPrompt()` 将 system prompt、user text 和可选 base64 image 转为 Koog prompt DSL。
- 工具调用通过 `ToolCallRecorder` 写入 Room，并同步推送给聊天 UI。

下面的代码片段属于历史实现参考，实际代码请以当前源码为准。

### 7.1 新增依赖导入

`KoogAgentFactoryImpl` 需要新增以下 import：

```kotlin
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.dsl.system
import ai.koog.prompt.dsl.user
import ai.koog.prompt.dsl.image
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.streaming.StreamFrame
```

### 7.2 策略 A：非流式实现（快速验证版）

```kotlin
package com.xiaoqi.companion.core.companion

import com.xiaoqi.companion.core.prompt.BuiltPrompt
import com.xiaoqi.companion.data.repository.LlmConfig
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.dsl.system
import ai.koog.prompt.dsl.user
import ai.koog.prompt.dsl.image
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 BuiltPrompt 转换为 Koog Prompt DSL 格式
 */
private fun BuiltPrompt.toKoogPrompt() = prompt("chat") {
    system(systemPrompt)
    if (hasImage && imageBase64 != null) {
        user {
            text(userMessage)
            image(imageBase64!!, imageMediaType ?: "image/jpeg")
        }
    } else {
        user(userMessage)
    }
}

@Singleton
class KoogAgentFactoryImpl @Inject constructor() : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper =
        RealKoogAgentWrapper(config)
}

class RealKoogAgentWrapper(private val config: LlmConfig) : KoogAgentWrapper {

    private val client by lazy {
        AnthropicLLMClient(
            apiKey = config.apiKey,
            settings = AnthropicClientSettings(baseUrl = config.baseUrl),
        )
    }

    private val model = LLModel(
        id = config.modelName,
        provider = LLMProvider.Anthropic,
    )

    override suspend fun run(prompt: BuiltPrompt): String {
        val koogPrompt = prompt.toKoogPrompt()
        return client.execute(koogPrompt, model)
    }
}
```

> **注意**: `client.execute(prompt, model)` 是同步（非流式）执行方法。
> 根据 API 参考，此方法阻塞直到完整响应返回。由于外层已有 `suspend fun` 包装
> 且会在 `Dispatchers.IO` 上调用，不会阻塞主线程。

### 7.3 策略 B：流式实现（生产目标版）

流式版本需要同时改造 `KoogAgentWrapper` 接口和 `CompanionRuntime`：

**Step 1**: 扩展 KoogAgentWrapper 接口

```kotlin
// core/companion/KoogAgentFactory.kt — 新增流式接口
interface KoogAgentWrapper {
    suspend fun run(prompt: BuiltPrompt): String
    /** 流式执行，返回原始 StreamFrame 流 */
    fun runStreaming(prompt: BuiltPrompt): kotlinx.coroutines.flow.Flow<StreamFrame>
}
```

**Step 2**: 实现流式 Wrapper

```kotlin
class StreamingKoogAgentWrapper(private val config: LlmConfig) : KoogAgentWrapper {

    private val client by lazy {
        AnthropicLLMClient(
            apiKey = config.apiKey,
            settings = AnthropicClientSettings(baseUrl = config.baseUrl),
        )
    }

    private val model = LLModel(
        id = config.modelName,
        provider = LLMProvider.Anthropic,
    )

    override suspend fun run(prompt: BuiltPrompt): String {
        // 非流式 fallback
        val koogPrompt = prompt.toKoogPrompt()
        return client.execute(koogPrompt, model)
    }

    override fun runStreaming(prompt: BuiltPrompt): Flow<StreamFrame> = callbackFlow {
        val koogPrompt = prompt.toKoogPrompt()
        client.executeStreaming(koogPrompt, model).collect { frame ->
            trySend(frame)
        }
        close()
    }.flowOn(Dispatchers.IO)
}
```

**Step 3**: 改造 CompanionRuntime 使用流式

```kotlin
// core/companion/CompanionRuntime.kt — 流式版本
open suspend fun send(input: UserInput): Flow<AgentEvent> = callbackFlow {
    var accumulatedText = ""

    try {
        val prompt = promptBuilder.build(
            input = input,
            emotionContext = emotionMachine.getContext(),
            relationshipContext = relationshipModel.contextModifier(),
        )
        val config = configRepository.getCurrentLlmConfig().first()
        val agent = koogAgentFactory.create(config)

        messageRepository.sendMessage(sessionId = "default", content = input.content)

        agent.runStreaming(prompt).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> {
                    accumulatedText += frame.text
                    trySend(AgentEvent.Streaming(frame.text))
                }
                is StreamFrame.End -> {
                    val parsed = outputParser.parse(accumulatedText)
                    emotionMachine.feed(parsed.emotionSignal)
                    relationshipModel.update(parsed.interactionSignal)
                    trySend(AgentEvent.Complete(parsed))
                }
                else -> { /* ToolCall 等 frame 暂不处理 */ }
            }
        }
    } catch (e: Exception) {
        val error = when (e) {
            is java.net.SocketTimeoutException -> AgentError.NetworkTimeout
            is java.net.io.IOException -> AgentError.ApiError(e.message ?: "网络错误")
            else -> AgentError.ApiError(e.message ?: "未知错误")
        }
        trySend(AgentEvent.Error(error))
    } finally {
        close()
    }
}
```

### 7.4 关于 execute() vs executeStreaming() 的 API 验证

从 JAR 提取的签名（`koog-api-reference.md` 第 13 节）：

```java
// AnthropicLLMClient
public java.lang.String execute(ai.koog.prompt.Prompt, ai.koog.prompt.llm.LLModel);
public kotlinx.coroutines.flow.Flow<ai.koog.prompt.streaming.StreamFrame>
    executeStreaming(ai.koog.prompt.Prompt, ai.koog.prompt.llm.LLModel);
```

两个方法均已在 JAR 中确认存在：
- `execute()`: 同步返回 `String`
- `executeStreaming()`: 返回 `Flow<StreamFrame>`

---

## 8. 已知问题与规避策略

### 8.1 问题汇总表

| ID | 问题 | 影响 | 规避/解决方案 | 状态 |
|----|------|------|--------------|------|
| P1 | GLM base URL 依赖 `.env` / `BuildConfig.LLM_BASE_URL` | 未配置时会报 `LLM_BASE_URL is not configured` | 在 `.env` 中配置 `LLM_BASE_URL=https://open.bigmodel.cn/api/paas/v1` | 需运行环境配置 |
| P2 | MainActivity 直接 LLM 调用反模式 | 绕过架构 | 已改为 `ChatViewModel` 驱动 | 已修复 |
| P3 | 历史上的 KoogAgentFactoryImpl stub | 所有 AI 回复为空 | 已替换为真实 Koog `AIAgent` | 已修复 |
| P4 | 进程死亡时 EmotionMachine/RelationshipModel 状态丢失 | 重启后情感和关系上下文归零 | 序列化到 DataStore | 低优先级 |
| P5 | SavedStateHandle 未接入 | 旋转屏幕/进程死亡后输入框内容丢失 | 在 ViewModel 中添加 | 低优先级 |
| KG-750 | runBlocking + ExecutorService 死锁 | ANR | 全项目禁用 runBlocking | 已规避（设计中未使用） |

### 8.2 Anthropic 兼容端点的注意事项

智谱和 Kimi 的 Anthropic 兼容端点**并非 100% 兼容**，已知差异：

| 特性 | 标准 Anthropic API | 智谱兼容 | Kimi 兼容 |
|------|-------------------|---------|-----------|
| streaming | SSE | ✅ | ✅ |
| tool_use | ✅ | ⚠️ 可能不支持 | ⚠️ 可能不支持 |
| cache_control | ✅ | ❌ | ❌ |
| thinking / extended thinking | ✅ | ❌ | ❌ |
| 图片输入 (base64) | ✅ | ✅ | ✅ |

**建议**: 如果未来需要 tool_use 功能，需验证目标端点是否支持。
当前纯聊天场景不受影响。

---

## 9. 检查清单

### 本地运行前

- [ ] 确认 `.env` 文件中 `LLM_API_KEY` 已配置有效 API Key
- [ ] 确认 `.env` 文件中 `LLM_BASE_URL` 已配置有效 Anthropic Messages 兼容端点
- [ ] 确认网络权限 `<uses-permission android:name="android.permission.INTERNET" />` 已在 AndroidManifest.xml 中声明
- [ ] 确认明文流量允许（如果使用 HTTP 而非 HTTPS）：`android:usesCleartextTraffic="true"` （仅 debug）

### 已完成的 Koog 集成检查

- [x] `MainActivity` 只负责 `setContent`
- [x] `ChatViewModel` 通过 `CompanionRuntime` 发送消息
- [x] `KoogAgentFactoryImpl` 创建真实 `AIAgent`
- [x] `CompanionRuntime` 支持 streaming / complete / error / tool events
- [x] 工具调用状态写入 Room 并显示在聊天 UI

### 后续待补

- [ ] 设置页配置 API key/provider/model
- [ ] 真机/模拟器手动验证 GLM/Kimi 端到端回复
- [ ] CameraX Vision 输入接入 `UserInput.Vision`
- [ ] 进程死亡后的状态恢复

---

## 附录 A：关键文件路径速查

| 文件 | 路径 | 作用 |
|------|------|------|
| Agent 工厂接口 | `core/companion/KoogAgentFactory.kt` | 定义 `KoogAgentWrapper` / `KoogAgentFactory` |
| Agent 工厂实现 | `core/companion/KoogAgentFactoryImpl.kt` | **本文档主要修改目标** |
| Runtime 编排 | `core/companion/CompanionRuntime.kt` | 调用工厂 → Agent → 解析 → 发事件 |
| 数据模型 | `core/companion/model/CoreModels.kt` | UserInput / AgentEvent / ParsedOutput |
| Prompt 构建 | `core/prompt/PromptBuilder.kt` | BuiltPrompt 构建 |
| 配置仓库 | `data/repository/ConfigRepository.kt` | LlmConfig (provider/url/key/model) |
| Chat ViewModel | `feature/chat/ChatViewModel.kt` | UI 层状态管理 |
| MainActivity | `MainActivity.kt` | Activity 入口，委托给 `ChatViewModel` |
| Build Config | `app/build.gradle.kts` | BuildConfig 字段定义 |
| 版本目录 | `gradle/libs.versions.toml` | `koog = "0.8.0"` |
| API 参考 | `docs/koog-api-reference.md` | JAR 提取的完整 API 签名 |

---

## 附录 B：依赖关系图（集成后）

```
                    ┌──────────────┐
                    │  Activity     │
                    │ (setContent)  │
                    └──────┬───────┘
                           │ state collection
                    ┌──────▼───────┐
                    │ChatViewModel │ ◀── viewModelScope
                    │  .uiState    │
                    └──────┬───────┘
                           │ send(UserInput)
                    ┌──────▼──────────────┐
                    │  CompanionRuntime    │ ◀── Singleton
                    │  ┌────────────────┐  │
                    │  │PromptBuilder    │  │
                    │  │KoogAgentFactory│──┼──▶ RealKoogAgentWrapper
                    │  │OutputParser     │  │     │
                    │  │EmotionMachine   │  │     ▼
                    │  │RelationshipModel│  │  AnthropicLLMClient
                    │  │MessageRepo      │  │     │
                    │  └────────────────┘  │     ▼
                    └──────────────────────┘  GLM / Kimi API
```
