# Koog Agents API Reference (v0.8.0)

> 从本地 Gradle 缓存 JAR (`javap -p -s`) 提取，来源：`ai.koog:koog-agents:0.8.0`

---

## 目录

1. [架构概览](#1-架构概览)
2. [核心 Agent](#2-核心-agent)
3. [Builder 体系](#3-builder-体系)
4. [Strategy 策略](#4-strategy-策略)
5. [Service 服务管理](#5-service-服务管理)
6. [Config 配置](#6-config-配置)
7. [Context 上下文](#7-context-上下文)
8. [Tool 工具系统](#8-tool-工具系统)
9. [Pipeline 管道与拦截器](#9-pipeline-管道与拦截器)
10. [Session 会话](#10-session-会话)
11. [Environment 环境](#11-environment-环境)
12. [State 状态机](#12-state-状态机)
13. [Prompt & LLM](#13-prompt--llm)
14. [内置工具 (agents-ext)](#14-内置工具-agents-ext)
15. [工厂方法速查](#15-工厂方法速查)

---

## 1. 架构概览

```
┌─────────────────────────────────────────────────────┐
│                   AIAgentHelper                      │
│              (顶层工厂 / DSL 入口)                     │
└──────────┬──────────────────────┬───────────────────┘
           │                      │
    ┌──────▼──────┐        ┌─────▼─────┐
    │ AIAgentBuilder│       │AIAgentService│
    │   (构建 Agent) │       │ (生命周期管理) │
    └──────┬──────┘        └─────┬─────┘
           │                      │
     ┌─────▼─────┐          ┌────▼────┐
     │ FunctionalAgentBuilder│  │ GraphAgentBuilder │
     └─────┬─────┘          └────┬────┘
           │                     │
     ┌─────▼─────┐         ┌────▼────┐
     │FunctionalAIAgent│      │ GraphAIAgent   │
     └─────┬─────┘         └────┬────┘
           │                    │
     ┌─────▼────────────────────▼─────┐
     │         AIAgentBase            │
     │  run() / createSession()       │
     └──────────────┬────────────────┘
                    │
           ┌────────▼────────┐
           │AIAgentRunSession│
           │   (单次执行)      │
           └────────┬────────┘
                    │
     ┌──────────────▼──────────────────┐
     │  AIAgentContext                  │
     │  ├─ AIAgentLLMContext (LLM 调用)  │
     │  ├─ AIAgentFunctionalContext      │
     │  └─ AIAgentGraphContext           │
     └──────────────┬──────────────────┘
                    │
     ┌──────────────▼──────────────────┐
     │  ToolRegistry ← Tool<TArgs,TResult>│
     │  ├─ ReadFileTool / WriteFileTool  │
     │  ├─ EditFileTool                 │
     │  ├─ ExecuteShellCommandTool      │
     │  ├─ AskUser / SayToUser / ExitTool│
     │  └─ AIAgentTool (子 Agent 作为工具)│
     └─────────────────────────────────┘
```

**模块依赖关系：**

| 模块 | Maven 坐标 | 职责 |
|------|-----------|------|
| `agents-core` | `ai.koog:agents-core-jvm:0.8.0` | Agent 核心：接口、Builder、策略、Session、Pipeline |
| `agents-ext` | `ai.koog:agents-ext-jvm:0.8.0` | 内置工具：文件、Shell、交互 |
| `agents-tools` | `ai.koog:agents-tools-jvm:0.8.0` | Tool 基类、ToolRegistry、ToolDescriptor |
| `prompt-model` | `ai.koog:prompt-model-jvm:0.8.0` | Prompt DSL、Message 类型 |
| `prompt-llm` | `ai.koog:prompt-llm-jvm:0.8.0` | LLModel、LLMProvider |

---

## 2. 核心 Agent

### `AIAgent<Input, Output>` — Agent 抽象基类

```kotlin
abstract class AIAgent<Input, Output> : Closeable {
    // --- 标识 ---
    abstract fun getId(): String
    abstract fun getAgentConfig(): AIAgentConfig

    // --- 运行（多种重载）---
    // 最简调用
    fun run(input: Input): Output

    // 指定 session ID
    fun run(input: Input, sessionId: String): Output

    // 协程挂起版本（核心）
    abstract suspend fun run(input: Input, sessionId: String): Output

    // 同步版本（指定 Executor）
    fun run(input: Input, sessionId: String, executor: ExecutorService): Output

    // --- Session 管理 ---
    abstract fun createSession(sessionId: String): AIAgentRunSession<Input, Output, *>

    // --- 工厂方法 ---
    companion object {
        fun builder(): AIAgentBuilder
    }
}
```

### `AIAgentBase<Input, Output, TContext>` — 具体实现基类

```kotlin
abstract class AIAgentBase<Input, Output, TContext : AIAgentContext>
    : AIAgent<Input, Output>

// 内部实现:
// - createSession() → 构建 AIAgentRunSessionImpl
// - run() → 创建 session → 执行 → 返回结果
```

### `FunctionalAIAgent<Input, Output>` — 函数式策略 Agent

```kotlin
class FunctionalAIAgent<Input, Output>(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentFunctionalStrategy<Input, Output>,  // 核心策略
    toolRegistry: ToolRegistry,
    name: String,
    clock: Clock,
    installFeatures: (FeatureContext) -> Unit             // Feature 安装回调
) : AIAgentBase<Input, Output, AIAgentFunctionalContext>

// 属性:
val promptExecutor: PromptExecutor
val strategy: AIAgentFunctionalStrategy<Input, Output>
val toolRegistry: ToolRegistry
val pipeline: AIAgentFunctionalPipeline
```

### `GraphAIAgent<Input, Output>` — 图式策略 Agent

```kotlin
class GraphAIAgent<Input, Output>(
    // 类似 FunctionalAIAgent，但使用 AIAgentGraphStrategy
) : AIAgentBase<Input, Output, AIAgentGraphContext>

// 额外能力:
// - copyWithTools(tools): 复制上下文并替换工具列表
// - fork(): 分叉上下文（用于并行节点）
// - replace(context): 替换上下文
```

---

## 3. Builder 体系

### `AIAgentBuilder` — 入口 Builder

```kotlin
class AIAgentBuilder : AIAgentBuilderCommon<AIAgentBuilder> {

    // === 三种策略 ===

    // 函数式策略：传入一个 BiFunction<Context, Input, Output>
    fun <Input, Output> functionalStrategy(
        name: String = "functional",
        strategy: BiFunction<AIAgentFunctionalContext, Input, Output>
    ): FunctionalAgentBuilder<Input, Output>

    // 图式策略：用 DSL 构建节点图
    fun <Input, Output> graphStrategy(
        name: String = "graph",
        init: BuilderChainAction<GraphStrategyBuilder, AIAgentGraphStrategy<Input, Output>>
    ): GraphAgentBuilder<Input, Output>

    // 规划器策略（Planner）
    fun <Input, Output> plannerStrategy(
        name: String = "planner",
        init: BuilderChainAction<AIAgentPlannerStrategyBuilder, TypedAgentPlannerStrategyBuilder<Input, Output>>
    ): PlannerAgentBuilder<Input, Output>

    // === 安装 Feature ===
    fun <TConfig : FeatureConfig> install(
        feature: AIAgentGraphFeature<TConfig, *>,
        configure: ConfigureAction<TConfig>
    ): GraphAgentBuilder<String, String>

    // === 构建 ===
    fun build(): AIAgent<String, String>
}
```

### `FunctionalAgentBuilder<Input, Output>` — 函数式 Agent 构建器

```kotlin
class FunctionalAgentBuilder<Input, Output>(
    strategy: AIAgentFunctionalStrategy<Input, Output>,
    promptExecutor: PromptExecutor,
    toolRegistry: ToolRegistry,
    name: String,
    config: AIAgentConfig,
    clock: Clock,
    featureInstallers: List<(FeatureContext) -> Unit>
) {

    // 安装 Feature
    fun <TConfig : FeatureConfig, TFeature> install(
        feature: AIAgentFunctionalFeature<TConfig, TFeature>,
        configure: ConfigureAction<TConfig>
    ): FunctionalAgentBuilder<Input, Output>

    // 构建 Agent 实例
    fun build(): AIAgent<Input, Output>
}
```

### `AIAgentBuilderCommon<Self>` — 公共 Builder 能力

```kotlin
abstract class AIAgentBuilderCommon<Self> : AIAgentBuilderBase<Self> {
    // 直接传入已构建的策略对象
    fun <Input, Output> graphStrategy(
        strategy: AIAgentGraphStrategy<Input, Output>
    ): GraphAgentBuilder<Input, Output>

    fun <Input, Output> functionalStrategy(
        strategy: AIAgentFunctionalStrategy<Input, Output>
    ): FunctionalAgentBuilder<Input, Output>
}
```

**典型用法：**

```kotlin
val agent = AIAgent.builder()
    .functionalStrategy<String, String>("my-agent") { ctx, input ->
        // 自定义处理逻辑
        "Hello, $input!"
    }
    .install(MyFeature) { config ->
        config.someParam = "value"
    }
    .build()

val result = agent.run("world")
```

---

## 4. Strategy 策略

### `AIAgentFunctionalStrategy<Input, Output>` — 函数式策略接口

```kotlin
interface AIAgentFunctionalStrategy<Input, Output>
    : AIAgentStrategy<Input, Output, AIAgentFunctionalContext>

// 继承自 AIAgentStrategy，核心方法:
suspend fun execute(context: AIAgentFunctionalContext, input: Input): Output
```

### `AIAgentSimpleStrategies` — 预置简单策略

```kotlin
object AIAgentSimpleStrategies {

    // 单次运行模式（串行工具调用）
    fun singleRunStrategy(): AIAgentGraphStrategy<String, String>

    // 单次运行 + 并行工具调用能力
    fun singleRunStrategy(toolCalls: ToolCalls): AIAgentGraphStrategy<String, String>
}
```

### `AIAgentGraphStrategy<Input, Output>` — 图式策略

```kotlin
interface AIAgentGraphStrategy<Input, Output>
    : AIAgentStrategy<Input, Output, AIAgentGraphContext>

// 由 DSL 构建，包含多个 AIAgentNode 组成的有向图
```

---

## 5. Service 服务管理

### `AIAgentService<Input, Output, TAgent>` — Agent 生命周期管理

```kotlin
abstract class AIAgentService<Input, Output, TAgent : AIAgent<Input, Output>> {

    // --- 核心属性 ---
    abstract val promptExecutor: PromptExecutor
    abstract val agentConfig: AIAgentConfig
    abstract val toolRegistry: ToolRegistry

    // --- Agent 创建 ---
    suspend fun createAgent(
        name: String,
        tools: ToolRegistry = this.toolRegistry,
        config: AIAgentConfig = this.agentConfig,
        clock: Clock
    ): TAgent

    // 创建并立即运行
    suspend fun createAgentAndRun(
        input: Input,
        sessionId: String,
        tools: ToolRegistry = this.toolRegistry,
        config: AIAgentConfig = this.agentConfig,
        clock: Clock
    ): Output

    // --- Agent 查询与管理 ---
    fun listActiveAgents(): List<TAgent>
    fun listInactiveAgents(): List<TAgent>
    fun listFinishedAgents(): List<TAgent>
    fun agentById(id: String): TAgent
    fun removeAgent(agent: TAgent): Boolean
    fun removeAgentWithId(id: String): Boolean

    // --- 工厂方法 ---
    companion object {
        fun builder(): AIAgentServiceBuilder
    }
}
```

### `FunctionalAIAgentService` — 函数式 Service

```kotlin
class FunctionalAIAgentService : ...
// 与 AIAgentService 功能一致，绑定 FunctionalAIAgent
```

### `createAgentTool()` — 将 Service 包装为 Tool

```kotlin
// 扩展函数：把一个 AIAgentService 变成可被其他 Agent 调用的 Tool
fun <Input, Output> AIAgentService<Input, Output, *>.createAgentTool(
    name: String,
    description: String,
    inputDescription: String,
    inputType: TypeToken<Input>,
    outputType: TypeToken<Output>,
    parentAgentId: String? = null,
    clock: Clock
): Tool<AIAgentTool.AgentToolInput<Input>, AIAgentTool.AgentToolResult<Output>>
```

---

## 6. Config 配置

### `AIAgentConfig` — Agent 核心配置

```kotlin
class AIAgentConfig(
    val prompt: Prompt,                          // System prompt + 参数
    val model: LLModel,                           // LLM 模型定义
    val maxAgentIterations: Int,                  // 最大迭代次数（防无限循环）
    val missingToolsConversionStrategy: MissingToolsConversionStrategy,  // 缺失工具处理
    val responseProcessor: ResponseProcessor,      // 响应处理器
    val serializer: JSONSerializer                // 序列化器
) {
    // 可选线程池
    var strategyExecutorService: ExecutorService
    var llmRequestExecutorService: ExecutorService

    // Builder 入口
    companion object {
        fun builder(): InitialAIAgentBuilder
    }

    // data-class 风格 copy
    fun copy(
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        maxAgentIterations: Int = this.maxAgentIterations,
        missingToolsConversionStrategy: MissingToolsConversionStrategy = this.missingToolsConversionStrategy,
        responseProcessor: ResponseProcessor = this.responseProcessor,
        serializer: JSONSerializer = this.serializer
    ): AIAgentConfig
}
```

**构造函数重载（按参数数量递减）：**

```kotlin
// 最完整（含 Executor）
AIAgentConfig(prompt, model, maxIter, strategyExec, llmExec, missingTools, responseProc, serializer)

// 不含 Executor
AIAgentConfig(prompt, model, maxIter, missingTools, responseProc, serializer)

// 不含 serializer
AIAgentConfig(prompt, model, maxIter, strategyExec, llmExec, missingTools, responseProc)

// 仅必要参数
AIAgentConfig(prompt, model, maxIter, strategyExec, llmExec)
AIAgentConfig(prompt, model, maxIter, strategyExec)
```

---

## 7. Context 上下文

### `AIAgentContext` — 上下文根接口

```kotlin
interface AIAgentContext {
    // --- 环境信息 ---
    val environment: AIAgentEnvironment
    val agentId: String
    val runId: String
    val agentInput: Any
    val config: AIAgentConfig
    val strategyName: String
    val parentContext: AIAgentContext?

    // --- Pipeline ---
    val pipeline: AIAgentPipeline

    // --- LLM 上下文 ---
    val llm: AIAgentLLMContext

    // --- 状态管理 ---
    val stateManager: AIAgentStateManager
    val storage: AIAgentStorage

    // --- 执行信息 ---
    var executionInfo: AgentExecutionInfo

    // --- KV 存储 ---
    fun store(key: AIAgentStorageKey<*>, value: Any)
    fun <T> get(key: AIAgentStorageKey<*>): T
    fun remove(key: AIAgentStorageKey<*>): Boolean

    // --- 历史消息 ---
    suspend fun history(): List<Message>

    // --- 工具调用检测 ---
    fun containsToolCalls(responses: List<Message.Response>): Boolean
}
```

### `AIAgentFunctionalContext` — 函数式上下文

```kotlin
class AIAgentFunctionalContext(
    environment: AIAgentEnvironment,
    agentId: String,
    runId: String,
    agentInput: Any,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    strategyName: String,
    pipeline: AIAgentFunctionalPipeline,   // 特化为 FunctionPipeline
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentContext?
) : AIAgentFunctionalContextBase<AIAgentFunctionalPipeline>
```

### `AIAgentGraphContext` — 图式上下文

```kotlin
class AIAgentGraphContext(
    environment: AIAgentEnvironment,
    agentId: String,
    agentInputType: TypeToken,               // 额外保留输入类型信息
    agentInput: Any,
    config: AIAgentConfig,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    runId: String,
    strategyName: String,
    pipeline: AIAgentGraphPipeline,          // 特化为 GraphPipeline
    executionInfo: AgentExecutionInfo,
    parentContext: AIAgentGraphContextBase?
) : AIAgentGraphContextBase {

    // 图式特有能力：
    fun copyWithTools(tools: List<ToolDescriptor>): AIAgentContext
    suspend fun fork(): AIAgentGraphContextBase          // 分叉（用于并行）
    suspend fun replace(newContext: AIAgentContext)       // 替换上下文
}
```

### `AIAgentLLMContext` — LLM 调用上下文

```kotlin
class AIAgentLLMContext(
    val tools: List<ToolDescriptor>,
    val toolRegistry: ToolRegistry,
    val prompt: Prompt,
    val model: LLModel,
    val responseProcessor: ResponseProcessor,
    val promptExecutor: PromptExecutor,
    val environment: AIAgentEnvironment,
    val config: AIAgentConfig,
    val clock: Clock
) : AIAgentLLMContextCommon {

    // 会话读写
    fun <T> writeSession(block: (AIAgentLLMWriteSession) -> T): T
    fun <T> readSession(block: (AIAgentLLMReadSession) -> T): T

    // copy 方法（支持协程挂起）
    suspend fun copy(/* ... */): AIAgentLLMContext
    fun copy(/* ... 非挂起版 */): AIAgentLLMContext
}
```

---

## 8. Tool 工具系统

### `Tool<TArgs, TResult>` — 工具抽象基类

```kotlin
abstract class Tool<TArgs, TResult>(
    private val argsType: TypeToken,          // 输入类型 Token
    private val resultType: TypeToken,        // 输出类型 Token
    private val descriptor: ToolDescriptor,   // 工具描述（给 LLM 看）
    private val metadata: Map<String, String> // 元数据
) {

    // --- 核心方法（必须实现）---
    abstract suspend fun execute(args: TArgs): TResult

    // --- 编解码 ---
    fun decodeArgs(json: JSONObject, serializer: JSONSerializer): TArgs
    fun decodeResult(json: JSONElement, serializer: JSONSerializer): TResult
    fun encodeArgs(args: TArgs, serializer: JSONSerializer): JSONObject
    fun encodeResult(result: TResult, serializer: JSONSerializer): JSONElement

    // --- 字符串化 ---
    fun encodeArgsToString(args: TArgs, ser: JSONSerializer): String
    fun encodeResultToString(result: TResult, ser: JSONSerializer): String

    // --- 属性 ---
    val name: String              // = descriptor.name
    val descriptor: ToolDescriptor

    // --- 构造函数重载 ---
    // 用 KSerializer（自动生成 schema）
    Tool(argsSer: KSerializer<TArgs>, resultSer: KSerializer<TResult>, desc: ToolDescriptor)
    // 用 name + description（自动生成 schema）
    Tool(argsType: TypeToken, resultType: TypeToken, name: String, description: String, schemaConfig: JsonSchemaConfig)
}
```

### `SimpleTool<TArgs>` — 简化工具（返回 String）

```kotlin
abstract class SimpleTool<TArgs> : Tool<TArgs, String> {
    // 结果自动序列化为纯字符串
    override fun encodeResultToString(result: String, ser: JSONSerializer): String
}
```

### `ToolDescriptor` — 工具描述（供 LLM 理解）

```kotlin
class ToolDescriptor(
    val name: String,                              // 工具名（如 "read_file"）
    val description: String,                       // 功能描述（自然语言）
    val requiredParameters: List<ToolParameterDescriptor>,  // 必填参数
    val optionalParameters: List<ToolParameterDescriptor>,  // 可选参数
    val cacheControl: CacheControl                 // 缓存控制
) {
    fun withCacheControl(cc: CacheControl): ToolDescriptor
    fun copy(/* ... */): ToolDescriptor
}
```

### `ToolRegistry` — 工具注册表

```kotlin
class ToolRegistry(private val _tools: List<Tool<*, *>>) {

    val tools: List<Tool<*, *>>

    // 查找
    fun getToolOrNull(name: String): Tool<*, *>?
    fun getTool(name: String): Tool<*, *>           // 找不到抛异常
    fun <T : Tool<*, *>> getTool(): T              // 按类型获取

    // 组合
    operator fun plus(other: ToolRegistry): ToolRegistry
    fun add(tool: Tool<*, *>)
    fun addAll(vararg tools: Tool<*, *>)

    companion object {
        val EMPTY: ToolRegistry
        fun builder(): ToolRegistryBuilder
    }
}
```

### `AIAgentTool<Input, Output>` — 子 Agent 作为工具

```kotlin
class AIAgentTool<Input, Output>(
    private val agentService: AIAgentService<Input, Output, *>,
    val agentName: String,
    val agentDescription: String,
    val inputDescription: String,
    val inputType: TypeToken<Input>,
    val outputType: TypeToken<Output>,
    val parentAgentId: String?
) : Tool<AgentToolInput<Input>, AgentToolResult<Output>> {

    // 内部实现 execute：
    // 1. 反序列化 Input
    // 2. 通过 agentService.createAgentAndRun() 执行
    // 3. 包装为 AgentToolResult(successful, errorMessage?, result?)

    // --- 数据类 ---
    data class AgentToolInput<Input>(val input: Input)
    data class AgentToolResult<Output>(
        val successful: Boolean,
        val errorMessage: String?,
        val result: Output?
    )
}
```

---

## 9. Pipeline 管道与拦截器

### `AIAgentPipeline` — 管道（事件总线 + Feature 管理）

```kotlin
abstract class AIAgentPipeline(config: AIAgentConfig, clock: Clock)
    : AIAgentPipelineAPI {

    // ========== 拦截器注册 ==========

    // --- Agent 生命周期 ---
    fun interceptEnvironmentCreated(feature, interceptor: TransformInterceptor<EnvCtx, Env>)
    fun interceptAgentStarting(feature, interceptor: Interceptor<AgentStartingContext>)
    fun interceptAgentCompleted(feature, interceptor: Interceptor<AgentCompletedContext>)
    fun interceptAgentExecutionFailed(feature, interceptor: Interceptor<AgentFailedContext>)
    fun interceptAgentClosing(feature, interceptor: Interceptor<AgentClosingContext>)

    // --- Strategy 生命周期 ---
    fun interceptStrategyStarting(feature, interceptor: Interceptor<StrategyStartingContext>)
    fun interceptStrategyCompleted(feature, interceptor: Interceptor<StrategyCompletedContext>)

    // --- LLM 调用 ---
    fun interceptLLMCallStarting(feature, interceptor: Interceptor<LLMCallStartingContext>)
    fun interceptLLMCallCompleted(feature, interceptor: Interceptor<LLMCallCompletedContext>)

    // --- 流式输出 ---
    fun interceptLLMStreamingStarting(feature, interceptor: Interceptor<StreamingStartingContext>)
    fun interceptLLMStreamingFrameReceived(feature, interceptor: Interceptor<FrameReceivedContext>)
    fun interceptLLMStreamingFailed(feature, interceptor: Interceptor<StreamingFailedContext>)
    fun interceptLLMStreamingCompleted(feature, interceptor: Interceptor<StreamingCompletedContext>)

    // --- 工具调用 ---
    fun interceptToolCallStarting(feature, interceptor: Interceptor<ToolCallStartingContext>)
    fun interceptToolValidationFailed(feature, interceptor: Interceptor<ValidationFailedContext>)
    fun interceptToolCallFailed(feature, interceptor: Interceptor<ToolCallFailedContext>)
    fun interceptToolCallCompleted(feature, interceptor: Interceptor<ToolCallCompletedContext>)

    // ========== Feature 管理 ==========
    fun <TFeature> feature(kClass: KClass<TFeature>, default: TFeature): TFeature
    fun <TConfig : FeatureConfig, TImpl> install(key: AIAgentStorageKey<TImpl>, config: TConfig, impl: TImpl)
    suspend fun uninstall(key: AIAgentStorageKey<*>): Unit

    // ========== 事件触发（内部使用）==========
    suspend fun onAgentStarting(/* ... */)
    suspend fun onAgentCompleted(/* ... */)
    suspend fun onAgentExecutionFailed(/* ... */)
    // ... (共 16 个 on* 方法对应上述拦截点)
}
```

### `AIAgentFunctionalPipeline` — 函数式管道

```kotlin
class AIAgentFunctionalPipeline(config: AIAgentConfig, clock: Clock)
    : AIAgentPipeline {

    // 额外：安装 FunctionalFeature
    fun <TConfig : FeatureConfig, TFeature> install(
        feature: AIAgentFunctionalFeature<TConfig, TFeature>,
        configure: (TConfig) -> Unit
    )
}
```

**拦截点全景图：**

```
Agent 启动
  ├─ EnvironmentCreated    → 可替换 Environment 实现
  ├─ AgentStarting         → Agent 开始执行
  │
  │  ┌─ Strategy 循环 ─────────────────────┐
  │  │                                    │
  │  ├─ StrategyStarting                  │
  │  │                                    │
  │  ├─ LLMCallStarting                   │
  │  ├─ LLMCallCompleted                  │
  │  │                                    │
  │  ├─ [流式] StreamingStarting          │
  │  │        ├─ FrameReceived (多次)      │
  │  │        ├─ StreamingFailed           │
  │  │        └─ StreamingCompleted        │
  │  │                                    │
  │  ├─ ToolCallStarting (每个工具)        │
  │  ├─ ToolValidationFailed              │
  │  ├─ ToolCallFailed                    │
  │  └─ ToolCallCompleted                 │
  │                                    │
  ├─ StrategyCompleted                  │
  │  └────────────────────────────────────┘
  │
  ├─ AgentCompleted (成功)
  ├─ AgentExecutionFailed (异常)
  └─ AgentClosing (清理资源)
```

---

## 10. Session 会话

### `AIAgentRunSession<Input, Output, TContext>` — 运行会话接口

```kotlin
interface AIAgentRunSession<Input, Output, TContext : AIAgentContext> {
    val pipeline: AIAgentPipeline
    val context: TContext
    suspend fun run(input: Input): Output
}
```

### `AIAgentRunSessionImpl` — 默认实现

```kotlin
class AIAgentRunSessionImpl<Input, Output, TContext>(
    val id: String,
    val agent: AIAgent<Input, Output>,
    val strategy: AIAgentStrategy<Input, Output, TContext>,
    val sessionPipeline: AIAgentPipeline,
    val ctxBuilder: (Input, String, String) -> TContext   // 上下文构建函数
) : AIAgentRunSession<Input, Output, TContext> {

    private var state: AIAgentState<Output>
    private lateinit var ctx: TContext

    suspend fun run(input: Input): Output {
        // 1. 构建 context
        // 2. withPreparedPipeline { 触发 AgentStarting → StrategyStarting → ... }
        // 3. 返回结果或抛异常
    }

    private suspend fun withPreparedPipeline(
        context: AIAgentContext,
        phase: String,
        pipeline: AIAgentPipeline,
        block: suspend () -> T
    ): T
}
```

---

## 11. Environment 环境

### `AIAgentEnvironment` — Agent 运行环境接口

```kotlin
interface AIAgentEnvironment {
    // 执行单个工具调用
    suspend fun executeTool(call: Message.Tool.Call): ReceivedToolResult

    // 批量执行工具调用
    suspend fun executeTools(calls: List<Message.Tool.Call>): List<ReceivedToolResult>

    // 错误报告
    suspend fun reportProblem(throwable: Throwable)
}

// ReceivedToolResult — 工具执行返回值包装
// （具体定义在 agents-core 中）
```

---

## 12. State 状态机

### `AIAgentState<Output>` — Agent 状态接口

```kotlin
interface AIAgentState<Output> {
    fun copy(): AIAgentState<Output>
}
```

**内置状态实现（sealed hierarchy）：**

```
AIAgentState<Output>
├── NotStarted        // 未启动
├── Starting          // 正在启动
├── Running           // 运行中
├── Finished(output)  // 正常完成，携带输出
└── Failed            // 执行失败
```

### `AIAgentStateManager` — 线程安全状态管理

```kotlin
class AIAgentStateManager(initialState: AIAgentState = NotStarted) {
    private var state: AIAgentState
    private val mutex: Mutex                       // 协程互斥锁

    // 带锁的状态访问
    suspend fun <T> withStateLock(
        block: suspend (AIAgentState) -> T
    ): T

    // 深拷贝（用于 context forking）
    suspend fun copy(): AIAgentStateManager
}
```

---

## 13. Prompt & LLM

### `Prompt` — 提示词容器

```kotlin
class Prompt(
    val messages: List<Message>,    // 消息列表（system/user/assistant/tool）
    val id: String,                 // Prompt ID
    val params: LLMParams           // LLM 参数（temperature 等）
) {
    companion object {
        val Empty: Prompt                           // 空 Prompt

        // Builder 入口
        fun builder(id: String, clock: Clock = ...): PromptBuilder
        fun builder(id: String): PromptBuilder
    }

    // --- 派生 ---
    fun withMessages(transform: (List<Message>) -> List<Message>): Prompt
    fun withParams(params: LLMParams): Prompt
    fun withUpdatedParams(block: (LLMParamsUpdateContext) -> Unit): Prompt

    // --- 元信息 ---
    fun latestTokenUsage(): Int
    fun totalTimeSpent(): Long
}
```

### `LLModel` — LLM 模型定义

```kotlin
class LLModel(
    val provider: LLMProvider,           // 提供商（OpenAI/Anthropic/...）
    val id: String,                      // 模型 ID（如 "gpt-4o", "claude-sonnet"）
    val capabilities: List<LLMCapability>, // 能力列表
    val contextLength: Long?,            // 上下文窗口大小
    val maxOutputTokens: Long?           // 最大输出 token 数
) {
    fun supports(capability: LLMCapability): Boolean

    // 构造函数重载（参数递减）：
    // LLModel(provider, id, capabilities, contextLength, maxOutputTokens)
    // LLModel(provider, id, capabilities, contextLength)
    // LLModel(provider, id, capabilities)
    // LLModel(provider, id)
}
```

---

## 14. 内置工具 (agents-ext)

### 文件操作工具

#### `ReadFileTool<Path>` — 读文件

```kotlin
class ReadFileTool<Path>(
    private val fs: FileSystemProvider.ReadOnly<Path>
) : Tool<ReadFileTool.Args, ReadFileTool.Result> {

    data class Args(
        @LLMDescription("要读取的文件路径")
        val path: String
    )

    data class Result(val content: String)

    suspend fun execute(args: Args): Result
}
```

#### `WriteFileTool<Path>` — 写文件

```kotlin
class WriteFileTool<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>
) : Tool<WriteFileTool.Args, WriteFileTool.Result> {

    data class Args(
        @LLMDescription("文件路径")
        val path: String,
        @LLMDescription("要写入的内容")
        val content: String
    )

    data class Result(val success: Boolean, val message: String)

    suspend fun execute(args: Args): Result
}
```

#### `EditFileTool<Path>` — 编辑文件（Patch）

```kotlin
class EditFileTool<Path>(
    private val fs: FileSystemProvider.ReadWrite<Path>
) : Tool<EditFileTool.Args, EditFileTool.Result> {

    companion object {
        val toolName: String = "edit_file"
        val toolDescription: MarkdownContent  // 自动生成
        val descriptor: ToolDescriptor         // 自动生成
    }

    data class Args(
        @LLMDescription("文件路径")
        val path: String,
        @LLMDescription("要查找的旧文本")
        val oldText: String,
        @LLMDescription("替换为的新文本")
        val newText: String
    )

    sealed class Result {
        data class Success(val applied: PatchApplyResult.Success) : Result()
        data class Failure(val originalNotFound: Boolean) : Result()
    }

    suspend fun execute(args: Args): Result
}
```

#### `ListDirectoryTool<Path>` — 列目录

```kotlin
class ListDirectoryTool<Path>(
    // fs: FileSystemProvider.ReadOnly<Path>
) : Tool<ListDirectoryTool.Args, ListDirectoryTool.Result> {
    data class Args(@LLMDescription("目录路径") val path: String)
    data class Result(val entries: List<TextFileEntry>)
}
```

### Shell 工具

#### `ExecuteShellCommandTool` — 执行 Shell 命令

```kotlin
class ExecuteShellCommandTool(
    private val executor: ShellCommandExecutor,
    private val confirmationHandler: ShellCommandConfirmationHandler
) : Tool<ExecuteShellCommandTool.Args, ExecuteShellCommandTool.Result> {

    data class Args(
        @LLMDescription("要执行的 shell 命令")
        val command: String
    )

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    suspend fun execute(args: Args): Result
}
```

**相关类型：**

```kotlin
// Shell 命令执行器接口
interface ShellCommandExecutor {
    suspend fun execute(command: String): ExecutionResult
    data class ExecutionResult(val exitCode: Int, val stdout: String, val stderr: String)
}

// JVM 默认实现
class JvmShellCommandExecutor : ShellCommandExecutor { ... }

// 命令确认处理器（安全机制）
interface ShellCommandConfirmationHandler
class BraveModeConfirmationHandler : ShellCommandConfirmationHandler  // 全部放行
class PrintShellCommandConfirmationHandler : ShellCommandConfirmationHandler  // 打印确认

// 确认结果
sealed class ShellCommandConfirmation {
    data class Approved : ShellCommandConfirmation()
    data class Denied : ShellCommandConfirmation()
}
```

### 交互工具（Singleton）

#### `AskUser` — 向用户提问

```kotlin
object AskUser : SimpleTool<AskUser.Args> {
    data class Args(
        @LLMDescription("向用户提出的问题")
        val question: String
    )
    suspend fun execute(args: Args): String   // 返回用户的回答
}
```

#### `SayToUser` — 向用户说话

```kotlin
object SayToUser : SimpleTool<SayToUser.Args> {
    data class Args(
        @LLMDescription("要对用户说的内容")
        val message: String
    )
    suspend fun execute(args: Args): String   // 返回确认消息
}
```

#### `ExitTool` — 结束对话

```kotlin
object ExitTool : SimpleTool<ExitTool.Args> {
    data class Args(
        @LLMDescription("结束原因")
        val reason: String
    )
    suspend fun execute(args: Args): String
}
```

### 搜索工具

#### `RegexSearchTool` — 正则搜索

```kotlin
class RegexSearchTool : Tool<RegexSearchTool.Args, RegexSearchTool.Result> {
    data class Args(
        val pattern: String,     // 正则表达式
        val path: String,        // 文件路径
        val content: String? = null  // 可选：直接搜索内容
    )
    data class ContentMatch(
        val line: Int,
        val content: String
    )
    data class Result(val matches: List<ContentMatch>)
}
```

---

## 15. 工厂方法速查

### `AIAgentHelper` — 顶层工厂（推荐入口）

```kotlin
object AIAgentHelper {

    // === Builder 入口 ===
    fun builder(): AIAgentBuilder

    // === 快捷创建（函数式）===
    operator fun <Input, Output> invoke(
        promptExecutor: PromptExecutor,
        config: AIAgentConfig,
        strategy: AIAgentFunctionalStrategy<Input, Output>,
        toolRegistry: ToolRegistry,
        name: String,
        clock: Clock,
        installFeatures: (FunctionalAIAgent.FeatureContext) -> Unit = {}
    ): FunctionalAIAgent<Input, Output>

    // === 快捷创建（图式，简化版）===
    operator fun invoke(
        promptExecutor: PromptExecutor,
        model: LLModel,
        responseProcessor: ResponseProcessor,
        strategy: AIAgentGraphStrategy<String, String>,
        toolRegistry: ToolRegistry,
        name: String,
        systemPrompt: String,
        temperature: Double? = null,
        maxIterations: Int = 10,
        maxTokens: Int = 4096,
        installFeatures: (GraphAIAgent.FeatureContext) -> Unit = {}
    ): AIAgent<String, String>

    // === 快捷创建（图式，完整版）===
    operator fun <Input, Output> invoke(
        promptExecutor: PromptExecutor,
        model: LLModel,
        responseProcessor: ResponseProcessor,
        strategy: AIAgentGraphStrategy<Input, Output>,
        toolRegistry: ToolRegistry,
        name: String,
        clock: Clock,
        systemPrompt: String,
        temperature: Double? = null,
        maxIterations: Int = 10,
        maxTokens: Int = 4096,
        installFeatures: (GraphAIAgent.FeatureContext) -> Unit = {}
    ): AIAgent<Input, Output>
}
```

### `AIAgentService` companion — Service 工厂

```kotlin
// 快捷创建 GraphAIAgentService
operator fun AIAgentService.Companion.invoke(
    promptExecutor: PromptExecutor,
    config: AIAgentConfig,
    strategy: AIAgentGraphStrategy<String, String>,
    toolRegistry: ToolRegistry,
    installFeatures: (GraphAIAgent.FeatureContext) -> Unit = {}
): GraphAIAgentService<String, String>
```

### `AIAgentToolKt` — Agent→Tool 转换

```kotlin
// 扩展函数：将任意 AIAgentService 包装为 Tool
fun <Input, Output> AIAgentService<Input, Output, *>.createAgentTool(
    name: String,
    description: String,
    inputDescription: String,
    inputType: TypeToken<Input>,
    outputType: TypeToken<Output>,
    parentAgentId: String? = null,
    clock: Clock
): Tool<AIAgentTool.AgentToolInput<Input>, AIAgentTool.AgentToolResult<Output>>
```

---

## 附录：关键类型速查表

| 类型 | 包路径 | 用途 |
|------|--------|------|
| `AIAgent<I,O>` | `ai.koog.agents.core.agent` | Agent 接口 |
| `AIAgentBuilder` | `ai.koog.agents.core.agent` | Builder 入口 |
| `AIAgentService<I,O,A>` | `ai.koog.agents.core.agent` | 生命周期管理 |
| `AIAgentConfig` | `ai.koog.agents.core.agent.config` | Agent 配置 |
| `AIAgentContext` | `ai.koog.agents.core.agent.context` | 运行时上下文 |
| `AIAgentLLMContext` | `ai.koog.agents.core.agent.context` | LLM 调用上下文 |
| `AIAgentFunctionalStrategy<I,O>` | `ai.koog.agents.core.agent` | 函数式策略 |
| `AIAgentGraphStrategy<I,O>` | `ai.koog.agents.core.agent.entity` | 图式策略 |
| `AIAgentSimpleStrategies` | `ai.koog.agents.core.agent` | 预置简单策略 |
| `AIAgentPipeline` | `ai.koog.agents.core.feature.pipeline` | 事件管道 |
| `AIAgentRunSession<I,O,C>` | `ai.koog.agents.core.agent.session` | 执行会话 |
| `AIAgentEnvironment` | `ai.koog.agents.core.environment` | 运行环境 |
| `AIAgentState<O>` | `ai.koog.agents.core.agent` | 状态接口 |
| `AIAgentStateManager` | `ai.koog.agents.core.agent.entity` | 状态管理器 |
| `Tool<A,R>` | `ai.koog.agents.core.tools` | 工具基类 |
| `SimpleTool<A>` | `ai.koog.agents.core.tools` | 简化工具基类 |
| `ToolDescriptor` | `ai.koog.agents.core.tools` | 工具描述 |
| `ToolRegistry` | `ai.koog.agents.core.tools` | 工具注册表 |
| `AIAgentTool<I,O>` | `ai.koog.agents.core.agent` | 子 Agent 工具 |
| `Prompt` | `ai.koog.prompt.dsl` | 提示词 |
| `LLModel` | `ai.koog.prompt.llm` | LLM 定义 |
| `PromptExecutor` | `ai.koog.prompt.executor.model` | Prompt 执行器 |
| `ResponseProcessor` | `ai.koog.prompt.processor` | 响应处理器 |
| `JSONSerializer` | `ai.koog.serialization` | JSON 序列化 |
| `TypeToken<T>` | `ai.koog.serialization` | 类型令牌 |
| `FileSystemProvider` | `ai.koog.rag.base.files` | 文件系统抽象 |

---

> **文档生成时间**: 2026-05-12
> **数据源**: `javap -p -s` 反编译自 Gradle 缓存 (`~/.gradle/caches/modules-2/files-2.1/ai.koog/`)
> **Koog 版本**: 0.8.0
