# Aura · 兜底 / 兼容性 / 降级设计清单

> 本文档梳理 Aura 项目里所有"宽容一点"的设计——**降级路径、默认值兜底、跨 SDK / 跨协议 / 跨设备的兼容层、对外能力缺失时的替身**。
>
> 目标读者：维护者 / 重构者。读完后能回答"如果 X 不可用，会发生什么？日志在哪？UI 上看到什么？有没有静默失败？"
>
> 字段约定：
>
> - **触发**：什么情况会进入该分支
> - **兜底**：实际行为（默认 / 跳过 / 抛错 / 退到旧 API）
> - **位置**：文件 + 符号
> - **风险**：可能掩盖的 bug 或后续重构要注意的点
>
> 关联文档：[architecture.md](./architecture.md) · [roadmap.md](./roadmap.md) · [engineering-standards.md](./engineering-standards.md)

---

## 一、快速索引（按模块）

| 模块 | 兜底 / 兼容主题 | 关键位置 |
| --- | --- | --- |
| LLM 协议兼容 | 把 GLM / Kimi / 自定义全部用 Anthropic Messages 协议打 | `AnthropicMessagesLLMClient`、`ConfigRepository` |
| 配置与默认值 | 模型 / BaseURL / Provider 切换时回退 | `DefaultLlmValues`、`ConfigRepository` |
| 主管线兜底 | 空响应 / 解析失败 / 反思失败 / 网络异常分类 | `CompanionRuntime` |
| 输出解析 | 标签缺失时的默认情绪/亲和/强度 | `OutputParser` |
| 上下文构造 | Token 估算 + 中心裁剪 + 空消息占位 | `ConversationContextBuilder`、`MemoryRepository` |
| 记忆系统 | 过期过滤 / 敏感过滤 / 相似合并 / JSON 容错 | `MemoryRepository` |
| MCP 远程工具 | 协议版本协商 / 404 自动重连 / 通知失败容错 | `McpHttpClient`、`CompanionToolRegistry` |
| 本地 LLM (MNN) | JNI 库懒加载 + 失败容错 + 路径多候选 | `NativeMnnLlmBridge`、`MnnLocalQwenEngine`、`LocalQwenModelLocator` |
| 模型下载 | 断点续传 / partial 切换失败回退 / 进度估算降级 | `LocalQwenModelDownloader` |
| 提示词系统 | YAML 解析容错 / 模板字段缺失 / 占位符 `[MISSING:key]` / fallback yml | `PromptConfigLoader`、`SystemPersona`、`PromptBuilder` |
| Reminder | SDK 版本分支 / 精确闹钟权限缺失降级 / `delay_too_small_minute` 显式拒 | `AndroidReminderScheduler`、`CreateLocalReminderTool`、`ContextPermissionReader`、`ReminderNotificationPoster` |
| Context Provider | 权限缺失 → null / 设备服务不可用 → "other" / weather `"未知天气"` | `CurrentLocationProvider`、`DeviceStatusProvider`、`OpenMeteoWeatherProvider` |
| 工具执行 | limit clamp / 非法 type 返错 / 多种 disabled 分支 | `SearchMemoryTool`、`CreateLocalReminderTool`、`QueryHealthDataTool` |
| UI 兜底 | 流式批渲染 / idle 超时 / 错误时保留部分回复 / JSON 解析 | `ChatViewModel`、`SendMessageUseCase`、`StreamingMarkdownChunker`、`DataTransparencySection` |
| Presence | mood 同义词扩展 / intensity 规范化 / 多别名 tool 识别 / 反应节流 | `PresenceController`、`PresenceReactionPolicy` |
| View 兼容性 | `ViewCompat` IME insets / 微信 IME 卡顿检测 | `ChatScreen`、`ChatInputBar` |
| Health 多源链 🆕 | HC 误报兜底 + SensorManager 兜底 + 1.5s 超时 + 防抖互斥 | `HealthConnectDataSource`、`SensorManagerHealthSource`、`HealthSyncManager` |
| DreamLoop 调度 🆕 | 电量约束 / 周期档位 / 空模型输出 retry / 7 天跨模态 evidence | `DreamLoopWorker`、`DreamLoopScheduler`、`DreamDataCollector`、`LocalQwenExecutor` |
| Insight 校验 🆕 | 4 道校验：缺 evidence / 真实存在门槛 / 信心度低 / 与近期重复 | `InsightValidator`、`InsightRepository` |
| Memory Vision 🆕 | `imageBase64` + MIGRATION_7_8 + `saveVisionMemory` fire-and-forget | `MemoryRepository`、`MemoryEntity`、`SendMessageUseCase` |
| Reminder 通知 🆕 | 无权限静默 / 工作约束 / 取消双通道 | `ReminderNotificationPoster`、`ReminderNotificationWorker` |
| 隐私面板 🆕 | 条数展示 + JSON 导出 (SAF) + 3 清空按钮 + Bipass 二次确认 | `DataTransparencySection` |
| 连通性检查 🆕 | `GET /v1/models` 区分 200 / 401-403 / 不可达 | `LlmConnectivityChecker` |
| Onboarding 🆕 | 5 问全可选可跳过 / 模板表单不入 LLM | `OnboardingScreen`、`OnboardingViewModel` |
| Memory Room 操作 🆕 | 长按菜单：置顶 / 取消置顶 / 归档 / 取消归档 / 删除（runCatching 模式） | `MemoryRoomScreen`、`ChatViewModel` |
| Settings 实时保存 🆕 | api_key 不依赖 Save 按钮 / Save 永久置顶 | `SettingsUseCase.updateSettingsApiKey`、`SettingsScreen` |
| 日志体系 🆕 | `AppLogger` 统一入口 + 15 `LogTags` + `LogFieldSanitizer` 自动 hash 敏感字段 + Debug/Release 双树 | `core/logging/` |

---

## 二、LLM 协议兼容层

### 2.1 统一走 Anthropic Messages 协议

- **触发**：用户选择 GLM / Kimi / 自定义等任意非本地 provider
- **兜底**：所有远端 provider 都通过 `AnthropicMessagesLLMClient` 调用；GLM / Kimi 的 baseUrl 已在 `DefaultLlmValues` 中固定为 Anthropic 兼容端点（`GLM_BASE_URL` / `KIMI_BASE_URL`）
- **位置**：
  - 客户端：[`AnthropicMessagesLLMClient`](../app/src/main/java/com/xiaoqi/companion/core/llm/AnthropicMessagesLLMClient.kt)
  - 默认值表：[`DefaultLlmValues`](../app/src/main/java/com/xiaoqi/companion/data/repository/ConfigRepository.kt)
- **风险**：在 `ConfigRepositoryImpl.getCurrentLlmConfig()` 中 `resolvedBaseUrl = DefaultLlmValues.defaultBaseUrl(provider)` —— **用户设置的 `baseUrl` 永远被覆盖**。如果未来要允许 GLM 用 OpenAI 兼容端点，需要先重写这一段。

### 2.2 `max_tokens` 三级回退

- **触发**：`Prompt.params.maxTokens` 与 `model.maxOutputTokens` 都缺失
- **兜底**：`prompt.params.maxTokens ?: model.maxOutputTokens?.toInt() ?: DEFAULT_MAX_TOKENS (4096)`
- **位置**：[`AnthropicMessagesLLMClient.buildRequestBody`](../app/src/main/java/com/xiaoqi/companion/core/llm/AnthropicMessagesLLMClient.kt)
- **风险**：当模型实际支持更少 token 时会被服务端截断，行为依赖 provider 自己的 stop_reason。

### 2.3 `moderate` / `models()` 占位实现

- **触发**：任何 LLM 接口校验（如 Koog 调用链上）
- **兜底**：
  - `moderate(prompt, model)` → `ModerationResult(false, emptyMap())`
  - `models()` → `emptyList()`
- **位置**：[`AnthropicMessagesLLMClient`](../app/src/main/java/com/xiaoqi/companion/core/llm/AnthropicMessagesLLMClient.kt)
- **风险**：禁用 moderation 后无法拦截违规 prompt；如果未来接 OpenAI / Gemini，需要替换这两个方法而不是继续返回空。

### 2.4 SSE 解析多分支兜底

- **触发**：`content_block_delta` 的 `delta.type` 未知 / 整个 SSE 数据无法解析为 JSON
- **兜底**：
  - 未知 `deltaType` → `ParsedSse()`（空）
  - `parseSseData` 顶层 `runCatching { }.getOrNull()` 失败时返回 `ParsedSse()`
- **位置**：[`AnthropicMessagesLLMClient.parseSseData`](../app/src/main/java/com/xiaoqi/companion/core/llm/AnthropicMessagesLLMClient.kt)
- **风险**：静默丢帧，只在最终 `responseLength` 与 `streamedText` 不一致时才能看到差异。

### 2.5 ContentPart 未知类型兜底

- **触发**：模型 / 工具返回了 `Text` / `Image` 之外的 `ContentPart`（例如未来新增的 Audio / File）
- **兜底**：`else -> part.toString()` 作为 text 块
- **位置**：[`AnthropicMessagesLLMClient.toAnthropicContent`](../app/src/main/java/com/xiaoqi/companion/core/llm/AnthropicMessagesLLMClient.kt)
- **风险**：会把对象 hash 之类当文本发给 Anthropic，应在测试里至少有一条非 Text/Image 覆盖。

---

## 三、配置与默认值

### 3.1 Provider 切换 → 默认模型/BaseURL 回填

- **触发**：用户在 Settings 切换 provider
- **兜底**：`SettingsUseCase.updateSettingsProvider` 用 `DefaultLlmValues.defaultModel(value)` 与 `defaultBaseUrl(value)` 覆盖当前 `settingsModelName` / `settingsBaseUrl`
- **位置**：[`SettingsUseCase`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SettingsUseCase.kt)、[`ChatMappers.defaultBaseUrl`](../app/src/main/java/com/xiaoqi/companion/feature/chat/mapper/ChatMappers.kt)
- **风险**：用户在原 provider 下修改的 modelName 会被立刻清空。

### 3.2 非法 modelName 强制回退

- **触发**：DataStore 里的 modelName 不在 `DefaultLlmValues.modelOptions(provider)` 白名单
- **兜底**：
  - `ConfigRepositoryImpl.getCurrentLlmConfig()` 用 `takeIf { it in modelOptions } ?: defaultModel`
  - `SettingsUseCase.saveSettings()` 同样回退
- **位置**：[`ConfigRepository`](../app/src/main/java/com/xiaoqi/companion/data/repository/ConfigRepository.kt)、[`SettingsUseCase`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SettingsUseCase.kt)
- **风险**：用户输入的"自建模型名"如果不在白名单会被静默改写，缺少提示。

### 3.3 `LOCAL_QWEN` 不需要 key / baseUrl

- **触发**：provider 为本地模型
- **兜底**：`LlmConfigStatus.isReady` 只看 `modelName.isNotBlank()`，且 `missingReason` 永远返回 `null`；`toChatConfigStatus` 在 provider == LOCAL_QWEN 且 `isReady` 时再覆盖一层：把 `isReady` 改写为 `false` 并设 `detail = "正在检查本地模型"`，由 UI 在模型下载完成后翻转
- **位置**：[`LlmConfigStatus`](../app/src/main/java/com/xiaoqi/companion/data/repository/ConfigRepository.kt)、[`ChatMappers.toChatConfigStatus`](../app/src/main/java/com/xiaoqi/companion/feature/chat/mapper/ChatMappers.kt)
- **风险**：本地模型"安装未完成"是通过 `withLocalQwenDownloadState` 在 UI 层叠加的，不在 LLMConfigStatus 里 —— 见 §10。

### 3.4 API Key 缺失直接拦 UI（warn 日志已删）

- **触发**：provider 非 LOCAL_QWEN 且 apiKey 为空
- **兜底**：`ConfigRepositoryImpl` **当前不再打 `api_key_missing` warn**，直接返 `LlmConfig`；`LlmConfigStatus.isReady` 返 `false` + `missingReason="缺少 API Key"`，由 `SendMessageUseCase` 在 `isReady=false` 时直接拦截发送
- **位置**：[`ConfigRepository`](../app/src/main/java/com/xiaoqi/companion/data/repository/ConfigRepository.kt)、[`SendMessageUseCase.invoke`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **风险**：任何绕过 `ChatConfigStatus` 的发送路径都会把空 key 发到 LLM；后续要让 `CompanionRuntime.send` 也检查 `isReady` 才能彻底闭环。

### 3.5 中文提示走字符串拼接（已重写）

- **位置**：[`missingText` / `modelNameText`](../app/src/main/java/com/xiaoqi/companion/data/repository/ConfigRepository.kt)
- **现状**：直接用 `private fun missingText(subject: String) = "缺失 $subject"` / `private fun modelNameText() = "模型名称"`（普通字符串模板）
- **历史**：早期版本用 `charArrayOf(0x7f3a.toChar(), 0x5c11.toChar())` 显式 char 序列构造中文，已清理。

---

## 四、CompanionRuntime 主管线兜底

### 4.1 空响应 → ParseError 事件

- **触发**：模型没回任何 token（`rawResponse.isBlank()`）
- **兜底**：`trySend(AgentEvent.Error(ParseError("Empty model response")))`
- **位置**：[`CompanionRuntime.send`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)

### 4.2 解析后正文空但 raw 非空 → 回退到 raw

- **触发**：`OutputParser.parse` 抽干所有 `[mood:..]` 标签后 `textReply.isBlank()` 但 `rawResponse.isNotBlank()`
- **兜底**：`parsed.copy(textReply = rawResponse.trim())`（日志 `empty_parsed_reply_using_raw_fallback`）
- **位置**：[`CompanionRuntime.send`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)
- **风险**：会把 `[mood:happy][intensity:0.7]...` 当文本展示给用户，但情绪 / 动作 / 标签解析都丢了。

### 4.3 二次仍空 → ParseError

- **触发**：raw 为空 + 解析空
- **兜底**：`AgentEvent.Error(ParseError("Empty assistant reply"))`
- **位置**：[`CompanionRuntime.send`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)

### 4.4 网络异常分类

- **触发**：上游抛 `SocketTimeoutException` / 其它
- **兜底**：
  - `SocketTimeoutException` → `AgentError.NetworkTimeout`
  - 其它 → `AgentError.ApiError(e.message ?: "Unknown error")`
- **位置**：[`CompanionRuntime.send`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)
- **风险**：`ApiError` 会吞掉 `CancellationException` —— 见 [common_pitfalls]。

### 4.5 反思失败不阻断主对话

- **触发**：`reflectAndSave` 抛任意异常（包含 LOCAL_QWEN 的 `UnsupportedOperationException`）
- **兜底**：`runCatching { }.getOrDefault(0)`，savedMemoryCount 视为 0
- **位置**：[`CompanionRuntime.send`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)
- **风险**：本地模型永远不会保存记忆，但用户层面没有任何提示；目前 LOG 只在 `conversation_reflection_failed`。

### 4.6 单 sessionId 硬编码

- **位置**：[`CompanionRuntime.DEFAULT_SESSION_ID = "default"`](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)
- **解释**：项目仍处于"单聊"阶段，多会话属于 roadmap 未做项。
- **风险**：UI / Repository / AgentStateDao 都按 `"default"` 写死，加新会话需要扫一遍。

---

## 五、OutputParser 标签解析兜底

### 5.1 情绪 / 强度 / 亲和 / 动作的默认值

- **触发**：模型输出不含 `[mood:..]` / `[intensity:..]` / `[affinity:..]` / `[topics:..]` / `[action:..]`
- **兜底**：
  - mood → `"neutral"`
  - intensity → `0.5f`
  - affinity → `0f`
  - topics → `emptyList()`
  - actions → `emptyList()`
- **位置**：[`OutputParser.parse`](../app/src/main/java/com/xiaoqi/companion/core/companion/OutputParser.kt)
- **风险**：若模型完全没学会结构化标签，UI 上看到的是"清冷 0.5 中性" → 容易被误判为系统行为。

### 5.2 标签全部抽离后 `cleanText` 作为正文

- **触发**：任意输入
- **兜底**：`allTagRegex.replace(text, "").trim()` 提取正文
- **位置**：[`OutputParser`](../app/src/main/java/com/xiaoqi/companion/core/companion/OutputParser.kt)
- **风险**：模型若把标签写错位置（如 `[mood`）会一并被剔除，但解析不到 mood。

### 5.3 空输入返回 `ParsedOutput()`

- **触发**：`raw.isNullOrEmpty()`
- **兜底**：返回全默认值的 `ParsedOutput`（仅 `parse_empty_input` 日志）
- **位置**：[`OutputParser.parse`](../app/src/main/java/com/xiaoqi/companion/core/companion/OutputParser.kt)

---

## 六、上下文构造 & 记忆兜底

### 6.1 Token 数估算（字符数 / 3）

- **触发**：所有 prompt 拼装时
- **兜底**：`((text.length + 2) / 3).coerceAtLeast(1)`
- **位置**：[`estimateTokens`](../app/src/main/java/com/xiaoqi/companion/core/companion/ConversationContextBuilder.kt)、[`MemoryRepository.estimateTokens`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：中文 + emoji + 标点都会让真实 token 数偏离 50% 以上，可能浪费 prompt 预算。

### 6.2 对话窗口超长截断（已改中心裁剪）

- **触发**：单条历史消息超过 `rawTokenBudget` 且 selected 为空
- **兜底**：`ConversationContextBuilder.truncateToTokenBudget` 改中心裁剪：保留前 1/3 + `...` + 后 2/3；`MemoryRepository.truncateToTokenBudget` 仍是尾部 + `...`（待重构）
- **位置**：[`ConversationContextBuilder.truncateToTokenBudget`](../app/src/main/java/com/xiaoqi/companion/core/companion/ConversationContextBuilder.kt)、[`MemoryRepository.truncateToTokenBudget`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：MemoryRepository 的记忆截断仍是尾部裁剪，重要但靠前的 FACT 仍会被吞。

### 6.3 空消息占位

- **触发**：`message.content` 纯空白
- **兜底**：`ifBlank { "(empty message)" }`
- **位置**：[`ConversationContextBuilder.toPromptMessage`](../app/src/main/java/com/xiaoqi/companion/core/companion/ConversationContextBuilder.kt)

### 6.4 记忆搜索 limit clamp

- **触发**：LLM / tool 调用传入 `limit`
- **兜底**：`safeLimit = limit.coerceIn(1, MAX_MEMORY_RESULTS=50)`，候选 = `safeLimit * 4` 最多 200
- **位置**：[`MemoryRepository.searchMemories`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：clamp 是单向收紧，如果工具最初为"全部"传 999，会被压到 50。

### 6.5 记忆 token 预算内裁剪

- **触发**：候选记忆总长超过 `PROMPT_MEMORY_TOKEN_BUDGET (10_000)`
- **兜底**：按顺序加入，剩余空间不足时截断当前条目并停止
- **位置**：[`MemoryRepository.selectMemoriesWithinTokenBudget`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：候选是按 `relevant + important + recent` 合并去重后的顺序，重要但靠后的记忆会被截断。

### 6.6 过期 / 敏感记忆过滤

- **触发**：记忆的 `expiresAt <= now`，或 `sensitivity ∈ {private, sensitive}` 且不相关
- **兜底**：直接在 `filterUsableForPrompt` 里 `return@filter false`
- **位置**：[`MemoryRepository.filterUsableForPrompt`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：`sensitive` 要求"≥ 2 个 key term"，对短查询会把所有敏感记忆屏蔽，可能漏掉用户刻意召回的。

### 6.7 相似记忆合并（dedup）

- **触发**：保存时新内容与某已有记忆 jaccard ≥ `MERGE_THRESHOLD (0.72)`
- **兜底**：`mergeContent` / `mergeSource` / `mergeJsonStringLists` / `strongestSensitivity` 合并而非新增
- **位置**：[`MemoryRepository.saveMemory`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)、[`mergeContent` 等私有函数](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：阈值固定 0.72，没让用户调；高阈值漏合并，低阈值会误吞"主题相近但不同"的记忆。

### 6.8 `sourceMessageIds` JSON 解析容错

- **触发**：旧记忆的 `sourceMessageIds` 字段 JSON 损坏
- **兜底**：`runCatching { json.decodeFromString }.getOrDefault(emptyList())`
- **位置**：[`mergeJsonStringLists`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：错误被吞；新合并结果会失去旧来源。

### 6.9 `normalizeSensitivity` 容错

- **触发**：保存时 `sensitivity` 字符串不在白名单
- **兜底**：除 `"private" / "sensitive"` 之外都归一为 `"normal"`
- **位置**：[`normalizeSensitivity`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：typo 如 `"privte"` 会被静默改写为 `normal`。

### 6.10 `importance` / `confidence` 范围

- **触发**：任意保存路径
- **兜底**：`coerceIn(0f, 1f)`
- **位置**：[`MemoryRepository.saveMemory`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)

---

## 七、MCP 远程工具兼容

### 7.1 MCP 协议版本协商 + 兜底

- **触发**：服务端在 `initialize` 响应里返回 `protocolVersion`
- **兜底**：`McpHttpClient.protocolVersions[serverUrl]` 缺失时回退到 `MCP_PROTOCOL_VERSION = "2025-11-25"`
- **位置**：[`McpHttpClient.ensureInitialized`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)

### 7.2 会话 404 自动重连

- **触发**：非 initialize 请求拿到 `404` 且 `includeSession` 且 `retryOnInvalidSession=true`
- **兜底**：移除 `sessions[serverUrl]` 与 `protocolVersions[serverUrl]`，重新 `ensureInitialized` 后用 `retryOnInvalidSession=false` 重发同一 payload
- **位置**：[`McpHttpClient.postJson`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **风险**：重连后 id 仍用原 requestId —— 大多数实现不会出问题，但严格 JSON-RPC 服务端可能拒绝。

### 7.3 `notifications/initialized` 失败容错

- **触发**：初始化后通知服务端 "我准备好了" 失败
- **兜底**：`runCatching { postJson(...) }.onFailure { debug "mcp_initialized_notification_failed" }` —— 不抛错
- **位置**：[`McpHttpClient.ensureInitialized`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **风险**：部分实现会把"未收到 notifications"的服务置为不可用；目前只能看到 debug 日志。

### 7.4 SSE + JSON 双格式解析

- **触发**：`content-type: text/event-stream` 或 body 以 `data:` 开头
- **兜底**：`parseSseDataPayloads` 提取每条事件的 `data:` 行，再按 `expectedResponseId` 或 `error` key 选出正确的 JSON
- **位置**：[`McpHttpClient.parseHttpBody`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **风险**：SSE 多 payload 顺序与 id 匹配是手写状态机；服务端推送"通知帧"会被当 JSON 解析。

### 7.5 工具 schema 缺失 → 过滤掉（已不再静默兜底）

- **触发**：`tools/list` 某工具的 `inputSchema` 缺失
- **兜底**：`return null` + `mcp_tool_schema_missing` warn 日志 —— 工具被从 registry 里过滤掉，宁可丢也不让不可用工具进 registry
- **位置**：[`McpHttpClient.toToolSpecOrNull`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **历史**：早期版本兜底为 `{"type":"object"}`，已清理。

### 7.6 工具结果文本兜底

- **触发**：`tools/call` 返回的 `content` 里没有 `type=text` 块
- **兜底**：
  - 先尝试 `structuredContent`（若非 `JsonNull`）
  - 再兜底 `response.toString()`
- **位置**：[`McpHttpClient.toToolResultString`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)

### 7.7 `listTools` 失败不阻断主对话

- **触发**：构建 ToolRegistry 时远程 MCP `listTools` 抛错
- **兜底**：`runCatching { runBlocking { remoteMcpClient.listTools(...) } }.onFailure { warn }`
- **位置**：[`CompanionToolRegistry.addRemoteMcpTools`](../app/src/main/java/com/xiaoqi/companion/core/tools/CompanionToolRegistry.kt)
- **风险**：Agent 仍然可用，只是少了远程工具；用户 UI 上不会提示。

### 7.8 工具缓存

- **触发**：同一 serverUrl 重复 `listTools`
- **兜底**：`toolCache[serverUrl]?.let { return it }`
- **位置**：[`McpHttpClient.listTools`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **风险**：缓存无 TTL / 无失效，远程新增的工具要等进程重启或加 `refreshMcpTools` 才会出现。

### 7.9 空 URL 直接返回空列表

- **触发**：`serverUrl.isBlank()`
- **兜底**：`return@withContext emptyList()`
- **位置**：[`McpHttpClient.listTools`](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)

### 7.10 远程 MCP 设置读取的 `runBlocking`（重构为 Repository，仍 runBlocking）

- **位置**：[`CompanionToolRegistry.addRemoteMcpTools`](../app/src/main/java/com/xiaoqi/companion/core/tools/CompanionToolRegistry.kt)
- **解释**：在 `create()` 同步路径里 `runBlocking { mcpServerListRepository.readAll() }`；已从 `appPreferences.mcpServerName.first()` 重构到 `McpServerListRepository.readAll()`，但 `create()` 仍非 suspend，**阻塞 agent 调用线程的风险未根除**。
- **风险**：要让 `create()` 变 suspend 才能彻底消除阻塞。

---

## 八、本地 LLM (MNN) 兼容与兜底

### 8.1 JNI 库懒加载 + 失败容错

- **触发**：第一次 `loadLibrary()` 时
- **兜底**：`runCatching { System.loadLibrary("aura_mnn_llm") }.onFailure { error }.isSuccess` 记录到 `libraryLoaded`，下次直接返回缓存
- **位置**：[`JniNativeMnnLlmApi.loadLibrary`](../app/src/main/java/com/xiaoqi/companion/core/local/NativeMnnLlmBridge.kt)
- **风险**：加载失败只在日志里 `mnn_native_library_load_failed`，UI 不会主动告警；用户只会在切到 LOCAL_QWEN 后才看到 `IllegalStateException("...aura_mnn_llm is not available...")`。

### 8.2 `ensureNativeAvailable` 双层保护

- **触发**：`load` / `generate` / `release` 任意入口
- **兜底**：调用前检查 `native.loadLibrary()` 抛 `IllegalStateException`
- **位置**：[`NativeMnnLlmBridge.ensureNativeAvailable`](../app/src/main/java/com/xiaoqi/companion/core/local/NativeMnnLlmBridge.kt)

### 8.3 `release()` 二次保护

- **触发**：bridge 已经 release 或 native 不可用
- **兜底**：`if (instanceId != 0L && native.loadLibrary())` 才真正调 native release
- **位置**：[`NativeMnnLlmBridge.release`](../app/src/main/java/com/xiaoqi/companion/core/local/NativeMnnLlmBridge.kt)

### 8.4 Bridge 复用 / 切换

- **触发**：连续请求，可能切到不同模型
- **兜底**：
  - 同 configPath 复用 `bridge`
  - 不同 → 旧 `bridge.release()` + 新建 + `load(newConfigPath)`
- **位置**：[`MnnLocalQwenEngine.ensureBridgeLoaded`](../app/src/main/java/com/xiaoqi/companion/core/local/MnnLocalQwenEngine.kt)
- **风险**：跨模型切换时 JNI 实例被释放，期间如果有并发请求会 block 在 `bridgeMutex`。

### 8.5 模型目录多候选

- **触发**：`AppFilesLocalQwenModelLocator.findModelDir`
- **兜底**：依次查 `filesDir/models/{modelName}` 与 `getExternalFilesDir(models/{modelName})`，第一个有 `config.json` 的胜出
- **位置**：[`AppFilesLocalQwenModelLocator`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelLocator.kt)
- **风险**：移动到外置存储的模型如果被系统清理，下次启动会回退到内置目录。

### 8.6 `model_dir` 缺失 config.json → 抛错

- **触发**：模型目录存在但没有 `config.json`
- **兜底**：`throw IllegalStateException("Local Qwen model is missing config.json: ...")`
- **位置**：[`MnnLocalQwenEngine.stream`](../app/src/main/java/com/xiaoqi/companion/core/local/MnnLocalQwenEngine.kt)
- **风险**：错误信息用户不太能看懂，UI 透出 `IllegalStateException` 字符串。

### 8.7 `stream` 内部空 token 过滤

- **触发**：`onToken` 收到空字符串
- **兜底**：`if (token.isNotEmpty()) trySend(token)` —— 直接丢弃
- **位置**：[`MnnLocalQwenEngine.stream`](../app/src/main/java/com/xiaoqi/companion/core/local/MnnLocalQwenEngine.kt)

### 8.8 `ReactiveCompanion` 能力降级（替代 `LocalQwenAgentWrapper`）

- **触发**：本地模型走 `runStructured`（反思模块）或 vision prompt
- **兜底**：直接 `throw UnsupportedOperationException`；`KoogAgentFactoryImpl.create` 在 provider == LOCAL_QWEN 时返回 `ReactiveCompanion`（已替代旧 `LocalQwenAgentWrapper`）
- **位置**：[`ReactiveCompanion`](../app/src/main/java/com/xiaoqi/companion/core/local/ReactiveCompanion.kt)、[`KoogAgentFactoryImpl.create`](../app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt)
- **风险**：被 `CompanionRuntime.runCatching` 吞掉（§4.5），用户毫无感知。Roadmap 里需让 UI 显式提示 "Local Qwen 不会自动保存记忆"。

---

## 九、模型下载兜底

### 9.1 单文件断点续传 / 复用

- **触发**：某个 `requiredFile` 已在 `modelDir` 存在
- **兜底**：复制到 `partialDir` 计入进度，跳过下载
- **位置**：[`ModelScopeLocalQwenModelDownloader.download`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)

### 9.2 `partial → modelDir` 原子切换失败回退

- **触发**：`renameTo(modelDir)` 返回 false
- **兜底**：`partialDir.copyRecursively(modelDir, overwrite = true)` 然后 `partialDir.deleteRecursively()`
- **位置**：[`ModelScopeLocalQwenModelDownloader.download`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)
- **风险**：外置存储跨设备时可能两份并存，要靠 `modelDir.deleteRecursively()` 之前已经被调用来保证清理。

### 9.3 进度条降级

- **触发**：`totalBytes` 未知（catalog 表里没列）
- **兜底**：`byBytes` 为空时用 `byFiles`（已下载文件数 / 总文件数）
- **位置**：[`ModelScopeLocalQwenModelDownloader.progress`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)
- **风险**：文件大小差异巨大时，按"文件数"算的进度会与实际字节数差距明显。

### 9.4 进度上限 0.99

- **触发**：任何计算
- **兜底**：`(byBytes ?: byFiles).coerceIn(0f, 0.99f)`
- **位置**：[同上](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)
- **风险**：100% 仅在 UI 显式发 `Download complete` 时出现，下载真实结束时可能跳变较大。

### 9.5 模型大小预估表（私有 object）

- **触发**：下载时拿不到 `Content-Length`
- **兜底**：`LocalQwenModelDownloader` 内私有 `object LocalQwenCatalogSizes.estimatedTotalBytes` 给 0.8B/2B/4B 写死 600/1600/3200 MB；未知模型返回 `null`
- **位置**：[`LocalQwenModelDownloader.LocalQwenCatalogSizes`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)
- **风险**：加新模型忘记更新这个表，会让进度条完全依赖文件数；应改为读远端 manifest。

### 9.6 完整性校验

- **触发**：下载完成时
- **兜底**：`validateInstall` 检查 `requiredFiles` 全部存在；不满足则抛 `IOException("Local Qwen model download incomplete: ...")`
- **位置**：[`ModelScopeLocalQwenModelDownloader.validateInstall`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)

### 9.7 单文件 404

- **触发**：某个文件 HTTP 不成功
- **兜底**：`throw IOException("ModelScope download failed (${code}): $fileName")`
- **位置**：[`ModelScopeLocalQwenModelDownloader.download`](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelDownloader.kt)
- **风险**：会丢已下完的进度，下次重下需要从头来（目前没接 resume）。

---

## 十、提示词系统兼容

### 10.1 YAML 解析容错

- **触发**：`prompts/system_persona.yml` 缺字段 / 缩进 / 多行值
- **兜底**：
  - `key == "sections"` → 切换到 section 收集模式
  - section 字段缺失 → `fields["title"] ?: ""` / `fields["placeholder"] ?: ""`
  - 多行值用 `|` 缩进判断
  - 文件末尾未闭合的 multiline 块会被自动 flush
- **位置**：[`PromptConfigLoader`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptConfigLoader.kt)
- **风险**：写错的缩进会被静默吞掉，需要在测试里覆盖多场景。

### 10.2 Section 缺失 → 整段不渲染

- **触发**：`config.sections` 缺少 `emotion` / `memory` / `tools` 等
- **兜底**：`buildSectionRaw` 返回空串；`PromptBuilder` 仅在模板非空时 append
- **位置**：[`SystemPersona.buildSectionRaw`](../app/src/main/java/com/xiaoqi/companion/core/prompt/templates/SystemPersona.kt)、[`PromptBuilder.buildSystemPrompt`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptBuilder.kt)

### 10.3 占位符未匹配 → `[MISSING:key]`（已替换）

- **触发**：模板里有 `{{memories}}` 但调用方没传 `memories`
- **兜底**：未匹配占位符被替换为 `[MISSING:memories]`，避免 LLM 看到裸 `{{}}`
- **位置**：[`PromptBuilder.buildSystemPrompt`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptBuilder.kt)
- **历史**：早期版本保留原样，§18.3 清理项已落地。

### 10.4 `SystemPersona` 懒初始化

- **位置**：[`CompanionApplication.onCreate`](../app/src/main/java/com/xiaoqi/companion/CompanionApplication.kt) → `SystemPersona.init(this)`
- **解释**：模板在 Application 启动时一次性加载到静态字段；测试用 `initForTesting` 覆盖。
- **风险**：热更新 prompt 需要 `reload(context)`，目前没有调用入口。

### 10.5 总结 / 对话 / 记忆 section 标题（已挪到 yml）

- **位置**：[`SystemPersona.summariesTitle` / `recentTitle`](../app/src/main/java/com/xiaoqi/companion/core/prompt/templates/SystemPersona.kt)、[`PromptBuilder.buildSystemPrompt`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptBuilder.kt)
- **解释**：`## 会话摘要` / `## 最近对话` 等标题已挪到 `system_persona.yml`，由 `SystemPersona.applyConfig` 加载；默认值仍是中文；`PromptBuilder` 改用 `appendLine("## ${SystemPersona.summariesTitle}")` 拼接
- **风险**：以后做 i18n 时只需改 yml，不再动代码。

---

## 十一、Reminder 兼容

### 11.1 精确闹钟：API < S 视为可用

- **触发**：`Build.VERSION.SDK_INT < S`
- **兜底**：`canScheduleExactReminders()` 直接返回 `true`
- **位置**：[`AndroidReminderScheduler`](../app/src/main/java/com/xiaoqi/companion/core/reminder/AndroidReminderScheduler.kt)

### 11.2 `setExactAndAllowWhileIdle` 单一 API（已统一）

- **触发**：所有 reminder 调度
- **兜底**：当前统一用 `alarmManager.setExactAndAllowWhileIdle(...)`；minSdk=26 已远高于 M，旧的 `< M` 分支已删，`@Suppress("DEPRECATION")` 移除
- **位置**：[`AndroidReminderScheduler.scheduleExactAlarm`](../app/src/main/java/com/xiaoqi/companion/core/reminder/AndroidReminderScheduler.kt)
- **历史**：早期版本有 `< M` 旧 API 分支，§18.3 清理项已落地。

### 11.3 `delay < 1s` 强制拉长

- **触发**：`triggerAtMillis - now <= 0`
- **兜底**：`coerceAtLeast(MIN_DELAY_MILLIS=1000)`
- **位置**：[`AndroidReminderScheduler.schedule`](../app/src/main/java/com/xiaoqi/companion/core/reminder/AndroidReminderScheduler.kt)
- **风险**：用户立即提醒会被延后 1 秒；§18.4 建议 UI 不要再显示"1s 后"。

### 11.4 POST_NOTIFICATIONS 权限 API 分支

- **触发**：`Build.VERSION.SDK_INT < TIRAMISU`
- **兜底**：`hasPostNotifications()` 直接 true
- **位置**：[`AndroidContextPermissionReader`](../app/src/main/java/com/xiaoqi/companion/core/context/ContextPermissionReader.kt)

### 11.5 工具侧 `disabled` 多分支（已 7 种）

- **触发**：`create_local_reminder` 在 `appPreferences.reminderToolEnabled` / `notificationEnabled` / `POST_NOTIFICATIONS` / 触发时间 / 精确闹钟权限等任意环节失败
- **兜底**：返回 JSON `{"status":"disabled","reason":"..."}`，reason 见下：
  - `reminder_tool_disabled`（用户在设置里关了 reminder tool）
  - `notifications_disabled_in_settings`（总开关关）
  - `notification_permission_missing`
  - `missing_trigger_time`（delayMinutes 与 triggerAtEpochMillis 都没传）
  - `trigger_time_must_be_future`（过去时间）
  - `exact_alarm_permission_missing`
  - `delay_too_small_minute`（新增：delayMinutes < 1 时直接拒，不再静默 clamp，见 §11.6）
- **位置**：[`CreateLocalReminderTool.execute`](../app/src/main/java/com/xiaoqi/companion/core/tools/CreateLocalReminderTool.kt)
- **风险**：UI 只对 `exact_alarm_permission_missing` 弹权限提示，其它 reason 直接进对话流。

### 11.6 `delayMinutes < 1` → 直接拒（已不再静默 clamp）

- **位置**：[`CreateLocalReminderTool.execute`](../app/src/main/java/com/xiaoqi/companion/core/tools/CreateLocalReminderTool.kt)
- **解释**：当前实现 `if (it < MIN_DELAY_MINUTES) return@withContext disabled("delay_too_small_minute")`；0 / 负数直接返 `disabled` 让 LLM 看到 `delay_too_small_minute` reason 并自己重试
- **历史**：早期版本 `it.coerceAtLeast(1L) * MILLIS_PER_MINUTE` 静默延后 1 分钟，§18.3 清理项已落地。

### 11.7 取消时双通道

- **位置**：[`AndroidReminderScheduler.cancel`](../app/src/main/java/com/xiaoqi/companion/core/reminder/AndroidReminderScheduler.kt)
- **解释**：同时 `WorkManager.cancelWorkById` 与 `cancelExactAlarm(reminderId)`，因为创建时根据 `exact` 走的不同路径。

---

## 十二、Context Provider 兜底

### 12.1 位置：权限缺失 / 服务不可用 / 单 provider 失败

- **触发**：
  - 没有 `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
  - `getSystemService(LocationManager::class.java) == null`
  - 单个 provider `getLastKnownLocation` 抛错
- **兜底**：
  - 权限缺失 → `null`（`location_read_skipped/reason=permission_missing`）
  - 服务不可用 → `null`（`reason=service_unavailable`）
  - 单 provider 失败 → `runCatching { }.getOrNull()` 跳过该 provider，最后取所有 provider 中 `time` 最新的
- **位置**：[`AndroidCurrentLocationProvider`](../app/src/main/java/com/xiaoqi/companion/core/context/CurrentLocationProvider.kt)

### 12.2 电量 / 网络未知

- **触发**：`Intent.ACTION_BATTERY_CHANGED` 拿不到 / ConnectivityManager 没 cap
- **兜底**：
  - `batteryPercent` 拿不到 → `-1`
  - `networkType` cap 缺失 → `"none"`
  - 已知 transport 都不匹配 → `"other"`
- **位置**：[`AndroidDeviceStatusProvider`](../app/src/main/java/com/xiaoqi/companion/core/context/DeviceStatusProvider.kt)
- **风险**：UI 直接展示 -1% 电量或 `none` 类型，需要在 ChatMappers / UI 层拦截。

### 12.3 Weather code 未匹配 → `"未知天气"`

- **触发**：Open-Meteo 返回的 `weather_code` 不在表里
- **兜底**：`weatherLabel(code) = "未知天气"`（已是中文文案）
- **位置**：[`OpenMeteoWeatherProvider.weatherLabel`](../app/src/main/java/com/xiaoqi/companion/core/weather/OpenMeteoWeatherProvider.kt)
- **风险**：表仍不全；WMO Code 完整映射待补（§18.3 清理项）。

### 12.4 `observedAt` 缺失

- **位置**：[`OpenMeteoWeatherProvider.getByCoordinates`](../app/src/main/java/com/xiaoqi/companion/core/weather/OpenMeteoWeatherProvider.kt)
- **解释**：`current["time"]!!.jsonPrimitive.contentOrNull.orEmpty()`

### 12.5 Geocoding 找不到城市

- **位置**：[`OpenMeteoWeatherProvider.getByCity`](../app/src/main/java/com/xiaoqi/companion/core/weather/OpenMeteoWeatherProvider.kt)
- **解释**：`results` 为空 → `error("No weather location found for $city")`
- **风险**：会被 `GetWeatherTool` 透出，依赖 LLM 是否会重试。

---

## 十三、工具执行兜底

### 13.1 `SearchMemoryTool` limit clamp

- **位置**：[`SearchMemoryTool.execute`](../app/src/main/java/com/xiaoqi/companion/core/tools/SearchMemoryTool.kt)
- **解释**：`args.limit.coerceIn(1, MAX_RESULTS=50)`

### 13.2 `SearchMemoryTool` 非法 type

- **位置**：[`SearchMemoryTool.execute`](../app/src/main/java/com/xiaoqi/companion/core/tools/SearchMemoryTool.kt)
- **解释**：`MemoryType.entries.firstOrNull { it.name == type }` 找不到 → 返回 `{"status":"error","reason":"invalid_memory_type","type":"...","allowedTypes":"FACT,EPISODE,PROCEDURAL"}`
- **风险**：tool 失败会冒泡到 LLM，模型可能误以为没结果。

### 13.3 `ToolCallRecorder` 自身失败透出

- **位置**：[`ToolCallRecorder.start/succeed/fail`](../app/src/main/java/com/xiaoqi/companion/core/tools/ToolCallRecorder.kt)
- **解释**：所有方法 `try { } catch (e) { log + rethrow }`
- **风险**：Room 写失败会让 LLM 拿不到工具结果，链路中断。

---

## 十四、UI 兜底

### 14.1 流式期间不回放数据库消息

- **位置**：[`ChatViewModel.init` 中订阅 `messageRepository.getMessagesBySession`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **解释**：`if (state.isLoading || state.messages.any { it.isStreaming }) state else ...`
- **风险**：流式过程中收到 DB 更新会被忽略，可能出现"内存有，DB 也有，但 UI 只显示内存"。

### 14.2 配置未就绪 → 直接拦截发送

- **位置**：[`SendMessageUseCase.invoke`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：`if (!configStatus.isReady) { update { copy(error = ...) }; return }`
- **风险**：用户用 LOCAL_QWEN 但模型没下载完时，错误是 `models 配置未完成`（来自 `ConfigRepository.missingReason`），与"模型未安装"语义混在一起。

### 14.3 空消息 + 无图片 → 静默返回

- **位置**：[`SendMessageUseCase.invoke`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：`if (trimmed.isEmpty() && pendingImage == null) return`

### 14.4 图片消息无文本 → 占位

- **位置**：[`SendMessageUseCase`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：`userPrompt = trimmed.ifBlank { IMAGE_ONLY_PROMPT }` / `userDisplayContent = trimmed.ifBlank { "Shared a picture" }`

### 14.5 流式 30s 空闲超时

- **位置**：[`SendMessageUseCase.resetIdleTimer`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：`delay(STREAMING_IDLE_TIMEOUT_MS=30_000)` → `timedOut = true` → 最终 `finishWithError("Response timed out. Please try again.")`

### 14.6 流式批渲染 90ms

- **位置**：[`SendMessageUseCase.scheduleStreamingRender`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：`delay(STREAMING_RENDER_BATCH_MS=90L)` 后 flush；当 batch chars ≥ 48 时直接 flush
- **风险**：长 token 块会被拆碎，对动画稳定 / 不稳定各有利弊。

### 14.7 错误时保留部分回复

- **位置**：[`SendMessageUseCase.finishWithError`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：若 `assistantContent` 非空，保留该消息并打上 `"回复未完整完成"` toolStatus

### 14.8 AgentError → 友好文案

- **位置**：[`SendMessageUseCase.formatError`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **解释**：
  - `NetworkTimeout` → `"Network timed out. Check your connection."`
  - `RateLimited` → `"Too many requests. Try again later."`
  - `ApiError` → `error.message`
  - `ParseError` → `error.reason`

### 14.9 图片处理失败 → 用户可读

- **位置**：[`ChatViewModel.attachImage`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **解释**：`error = "图片处理失败，请换一张试试。"` —— 吞了 `imageProcessor` 异常

### 14.10 `extractIntensity` JSON 容错

- **位置**：[`ChatMappers.extractIntensity`](../app/src/main/java/com/xiaoqi/companion/feature/chat/mapper/ChatMappers.kt)
- **解释**：用正则从 `{"intensity":0.7}` 抽数字；失败回退 `0.5f` 中性

### 14.11 `CompanionStatus.after` mood 保留

- **位置**：[`ChatMappers.after`](../app/src/main/java/com/xiaoqi/companion/feature/chat/mapper/ChatMappers.kt)
- **解释**：`mood = mood.ifBlank { this.mood }` —— 模型没输出 mood 时不重置

### 14.12 Intensity / relationshipLevel 范围

- **位置**：[`CompanionStatus.after`](../app/src/main/java/com/xiaoqi/companion/feature/chat/mapper/ChatMappers.kt)
- **解释**：两者都 `coerceIn(0f, 1f)`

### 14.13 Reminder / Memory 取消 / 删除容错

- **位置**：[`ChatViewModel.cancelReminder` / `deleteMemory`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **解释**：`runCatching` 失败时设置 `error` 字符串，不抛给上层

### 14.14 Boolean 偏好写入容错

- **位置**：[`SettingsUseCase.updateBooleanPreference`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SettingsUseCase.kt)
- **解释**：DataStore 写失败 → `error = "Update setting failed. Please try again."`

### 14.15 Settings 校验

- **位置**：[`SettingsUseCase.saveSettings` / `saveMcpSettings`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SettingsUseCase.kt)
- **解释**：
  - model name 不在白名单 → 静默回退 `DefaultLlmValues.defaultModel(provider)`
  - 非 LOCAL_QWEN 且 baseUrl 空 → `settingsMessage = "Base URL 不能为空"`
  - MCP URL 非 http(s) → `settingsMessage = "MCP URL 必须以 http:// 或 https:// 开头"`（已是中文）

---

## 十五、Presence 状态推导兜底

### 15.1 mood 空串 → neutral

- **位置**：[`PresenceController.derive`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：`inputs.mood.ifBlank { "neutral" }.lowercase()`

### 15.2 intensity / relationshipLevel clamp

- **位置**：[`PresenceController.derive`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：`coerceIn(0f, 1f)`

### 15.3 mood 集合未匹配 → IDLE（已扩同义词）

- **位置**：[`PresenceController`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：mood 集合已扩：
  - `HAPPY` ← `{happy, joy, excited, warm, calm}`（含 `calm`）
  - `SAD` ← `{sad, lonely, anxious, worried, upset}`
  - `TIRED` ← `{tired, sleepy, exhausted, low}`（含 `sleepy`）
  - 其它 → `PresenceMode.IDLE`
- **风险**：仍有未收录的同义词（如 `joyful`）；§18.3 建议从 `system_persona.yml` 加载 mood 同义词。

### 15.4 memory 搜索工具多别名

- **位置**：[`PresenceController.isMemorySearch`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：`search_memory || search_records || search_summaries`
- **风险**：未来再加 `search_chats` 之类需要同步这里。

### 15.5 Reaction / label / detail 兜底

- **位置**：[`PresenceController.labelFor` / `detailFor` / `accent`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：
  - `REMEMBERING` → label = `""`（**空 label 是有意为之**：避免和 detail 重复；后续可改成 "Remembering" 之类的提示，见 [优化历史]）
  - 其它 mode 都有常量 label/detail/accent
  - `SLEEPING` 复用 `TIRED` 的 "Resting" / "resting"
- **风险**：`""` 标签如果传到 UI 组件可能撑不开高度，需要在 Compose 侧有最小高度处理。

### 15.6 Reaction 优先级

- **位置**：[`PresenceController.derive`](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **解释**：error > remembering > searching > thinking > speaking > listening > mood 分支 > IDLE
- **风险**：分支多但行为由输入决定，单测覆盖即可。

### 15.7 Reaction 重复抑制

- **位置**：[`ChatViewModel.shouldShowPresenceReaction` + `PresenceReactionPolicy`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **解释**：`lastPresenceReactionAtMillis` 记录每个 reaction 的最近展示时间，由 `PresenceReactionPolicy.shouldShow` 决定是否接受
- **风险**：与具体策略耦合，需查看 `PresenceReactionPolicy` 的冷却逻辑。

---

## 十六、View 兼容

### 16.1 IME insets 用 `ViewCompat`

- **位置**：[`ChatScreen`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatScreen.kt)、[`ChatInputBar`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatInputBar.kt)
- **解释**：用 `ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())` 而非 Compose `WindowInsets.ime` 扩展属性 —— 后者是 `@Composable`，不能在 `snapshotFlow` 的非 Composable lambda 里调用
- **风险**：注释里写了原因，重构者容易"现代化"踩坑。

---

## 十七、其它 / 杂项

### 17.1 资源 / 资产回退（已加 fallback 资产）

- **YAML 资产路径**：[`PromptConfigLoader.ASSET_PATH = "prompts/system_persona.yml"`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptConfigLoader.kt)
  - 主资产失败时自动回退到 `FALLBACK_ASSET_PATH = "prompts/system_persona.default.yml"`，由 `parseWithFallback` 统一处理
  - **位置**：[`PromptConfigLoader.parseWithFallback`](../app/src/main/java/com/xiaoqi/companion/core/prompt/PromptConfigLoader.kt)
- **历史**：早期版本资产缺失直接抛 `FileNotFoundException`，§18.3 清理项已落地。

### 17.2 工具调用 callId 兜底

- **位置**：[`KoogAgentFactoryImpl.createAgent` 的 `onToolCallStarting` 等回调](../app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt)
- **解释**：`val callId = context.toolCallId?.ifBlank { context.eventId } ?: context.eventId`
- **风险**：`eventId` 与 `toolCallId` 都为空时只能拿到空串，写到 DB 会被主键冲突。

### 17.3 `agent.runStructured` 失败透出

- **位置**：[`KoogAgentFactoryImpl.runStructured`](../app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt)
- **解释**：`executor.executeStructured(...).getOrThrow().data`
- **风险**：reflect 失败时直接抛到 `CompanionRuntime.send` 的 `catch (e: Exception)` 兜底（§4.4），但不会写 `agent_error_received` 日志，只打 `agent_run_failed`。

### 17.4 `MAX_AGENT_ITERATIONS = 12`

- **位置**：[`KoogAgentFactoryImpl`](../app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt)
- **解释**：防止 agent 在 tool 循环里卡死
- **风险**：复杂多步任务会被截断，模型收不到 finish 信号

### 17.5 Tool 注册时无图 / 禁用工具

- **位置**：[`KoogAgentFactoryImpl.createAgent`](../app/src/main/java/com/xiaoqi/companion/core/companion/KoogAgentFactoryImpl.kt)
- **解释**：`if (prompt.hasImage || !prompt.allowTools) ToolRegistry.EMPTY else toolRegistry.create()`
- **风险**：vision prompt / 反思 prompt 拿不到任何工具，LLM 不能用 search_memory 补上下文 —— 反思 prompt 故意如此

### 17.6 `moderation` / `models` 在 `AnthropicMessagesLLMClient` 留空

- 见 §2.3

---

## 十八、保留 vs 清理审计

这一节把"是否要保留这个兼容层"做一次正面表态。判定标准：

- **必留**：产品功能 / 跨 SDK / 跨协议 / 跨设备的硬约束；没有它会真出线上问题。
- **可清理**：实现依赖不存在、或历史遗留、或被新代码架空；删除后能直接看到错误，反而更好修。
- **可重构**：兜底本身合理，但当前实现方式过时 / 不安全 / 难读；保留逻辑，重写代码。

### 18.1 快速裁决表

> 标号对应前文章节。`✅ 必留` / `♻️ 可重构` / `🧹 可清理`。

| # | 兜底点 | 裁决 | 理由（简） |
| --- | --- | --- | --- |
| 2.1 | 统一走 Anthropic Messages | ✅ | 多 provider 共享一套协议，是核心抽象 |
| 2.2 | maxTokens 三级回退 | ✅ | 防止 null 透传 |
| 2.3 | `moderate` / `models()` 返回空 | 🧹 | 应明确抛 `NotImplementedError` 或 warn，否则被当"通过"用 |
| 2.4 | SSE 多分支兜底 | ✅ | 协议层必须容忍未知帧 |
| 2.5 | ContentPart 未知 → toString | ♻️ | 保留兜底，但加 warn 日志 |
| 3.1 | Provider 切换回填 | ✅ | 核心 UX 期望 |
| 3.2 | 非法 modelName 静默回退 | ♻️ | 保留回退但 UI 必须弹提示 |
| 3.3 | LOCAL_QWEN 免 key/url | ✅ | 设计如此 |
| 3.4 | API Key 缺失只 warn | 🧹 | 应直接拦 UI，不再让空 key 流到 LLM |
| 3.5 | `char` 数组构造中文 | 🧹 | 直接用普通字符串 |
| 4.1 | 空响应 → ParseError | ✅ | 必要的错误反馈 |
| 4.2 | 解析空回退 raw | ♻️ | 保留兜底，但若多次触发应视为模型能力问题并提示 |
| 4.3 | 二次空 → ParseError | ✅ | |
| 4.4 | 网络异常分类 | ✅ | |
| 4.5 | 反思失败不阻断 | ♻️ | 保留但应让用户在 ChatViewModel 看到"本轮未保存记忆" |
| 4.6 | 单 sessionId 硬编码 | 🧹 | roadmap 项，重构者提前扫一遍 |
| 5.1 | 标签缺失默认值 | ✅ | 模型不可控的必然兜底 |
| 5.2 | 抽干标签作为正文 | ✅ | |
| 5.3 | 空输入返空 | ✅ | |
| 6.1 | Token 估算（字符数/3） | ✅ | 本地不做真实 tokenize 是合理 trade-off |
| 6.2 | 超长截断取尾 | 🧹 | 应改中心裁剪（头+尾） |
| 6.3 | 空消息占位 | ✅ | |
| 6.4 | limit clamp | ✅ | |
| 6.5 | token 预算内裁剪 | ✅ | |
| 6.6 | 过期/敏感过滤 | ✅ | |
| 6.7 | 相似记忆合并 | ♻️ | 阈值写死不便调整；保留逻辑，把阈值挪到 ConfigRepository |
| 6.8 | JSON 解析容错 | ✅ | |
| 6.9 | sensitivity 容错 | ♻️ | 保留归一化但加 warn |
| 6.10 | importance/confidence clamp | ✅ | |
| 7.1 | 协议版本协商 | ✅ | |
| 7.2 | 404 自动重连 | ✅ | |
| 7.3 | notifications 失败容错 | ♻️ | 保留 debug 日志，但同时让 Settings → MCP 面板能显示"初始化告警" |
| 7.4 | SSE+JSON 双解析 | ✅ | |
| 7.5 | tool schema 缺失兜底 | 🧹 | 应抛错：缺 schema 的工具根本不可用 |
| 7.6 | tool result 文本兜底 | ✅ | |
| 7.7 | listTools 失败不阻断 | ♻️ | 保留但 MCP 面板要能看到"远程工具加载失败" |
| 7.8 | 工具缓存 | ♻️ | 保留但加"刷新"按钮 |
| 7.9 | 空 URL 返空 | ✅ | |
| 7.10 | `runBlocking` 读偏好 | 🧹 | 应让 `create()` 变 suspend |
| 8.1 | JNI 懒加载 | ✅ | |
| 8.2 | `ensureNativeAvailable` | ✅ | |
| 8.3 | `release` 二次保护 | ✅ | |
| 8.4 | bridge 复用 | ✅ | |
| 8.5 | 模型目录多候选 | ✅ | |
| 8.6 | 缺失 config.json 抛错 | ✅ | |
| 8.7 | 空 token 过滤 | ✅ | |
| 8.8 | `runStructured` / Vision 抛错 | ♻️ | 保留抛错，但在 Settings 显式标"Local Qwen 不会自动保存记忆" |
| 9.1 | 断点续传 | ✅ | |
| 9.2 | partial 切换失败回退 | ✅ | |
| 9.3 | 进度条降级 | ♻️ | 保留降级，但 UI 文案要区分"按字节" / "按文件" |
| 9.4 | 进度上限 0.99 | ✅ | |
| 9.5 | catalog 大小表 | 🧹 | 应从远端 manifest 拿；写死列表每次新增都要改 |
| 9.6 | 完整性校验 | ✅ | |
| 9.7 | 单文件 404 重下 | 🧹 | 应支持 Range resume / 多镜像 |
| 10.1 | YAML 容错 | ✅ | |
| 10.2 | section 缺失 → 不渲染 | 🧹 | 应 assert + warn，告知维护者模板残缺 |
| 10.3 | 占位符未匹配保留 | 🧹 | 应替换为 `[MISSING:name]` 之类，避免 LLM 看到裸 `{{}}` |
| 10.4 | SystemPersona 懒初始化 | ✅ | |
| 10.5 | 中文硬编码 section | 🧹 | 移到 i18n |
| 11.1 | API<S 视为可用 | ✅ | |
| 11.2 | `setExact` 旧 API | 🧹 | minSdk 已远高于 M，可直接 `setExactAndAllowWhileIdle` |
| 11.3 | delay<1s 拉长 | ♻️ | 保留拉长，但 UI 不要再显示"1s 后" |
| 11.4 | POST_NOTIFICATIONS 分支 | ✅ | |
| 11.5 | 6 种 `disabled` reason | ✅ | |
| 11.6 | delayMinutes 下限 1 | 🧹 | 应让 LLM 收到"delay 必须 ≥ 1 分钟"并重试 |
| 11.7 | 取消双通道 | ✅ | |
| 12.1 | 位置 null | ✅ | |
| 12.2 | 电量/网络 -1 / "other" | ✅ | |
| 12.3 | weather code 未匹配 → "unknown" | 🧹 | 表不全，应补 WMO 完整映射 |
| 12.4 | observedAt 空 | ✅ | |
| 12.5 | 找不到城市 | ✅ | |
| 13.1 | limit clamp | ✅ | |
| 13.2 | 非法 type 返错 | ✅ | |
| 13.3 | ToolCallRecorder 失败透出 | ✅ | |
| 14.1 | 流式期间不回放 DB | ✅ | |
| 14.2 | 配置未就绪拦截 | ✅ | |
| 14.3 | 空消息静默 | ✅ | |
| 14.4 | 图片消息占位 | ✅ | |
| 14.5 | 流式 idle 30s | ✅ | |
| 14.6 | 流式批渲染 90ms | ♻️ | 把阈值挪到 Config，可调 |
| 14.7 | 错误时保留部分 | ✅ | |
| 14.8 | AgentError 文案 | ✅ | |
| 14.9 | 图片处理失败 | ✅ | |
| 14.10 | extractIntensity 容错 | ✅ | |
| 14.11 | mood 保留 | ✅ | |
| 14.12 | intensity/relationshipLevel clamp | ✅ | |
| 14.13 | 取消/删除容错 | ✅ | |
| 14.14 | Boolean 偏好容错 | ✅ | |
| 14.15 | Settings 校验 | ✅ | |
| 15.1 | mood 空 → neutral | ✅ | |
| 15.2 | clamp | ✅ | |
| 15.3 | mood 集合未匹配 → IDLE | 🧹 | 应扩展集合或让 LLM 输出规范化 |
| 15.4 | memory 搜索多别名 | ✅ | |
| 15.5 | reaction/label/detail 兜底 | ✅ | |
| 15.6 | 反应优先级 | ✅ | |
| 15.7 | 反应重复抑制 | ✅ | |
| 16.1 | `ViewCompat` IME | ✅ | 注释里已写原因 |
| 17.1 | 资产缺失抛 FileNotFound | 🧹 | 应有 fallback yml 或二进制默认 prompt |
| 17.2 | callId 兜底 | ✅ | |
| 17.3 | runStructured 失败透出 | ♻️ | 保留透出，但日志要能区分"本地 LLM 不支持" |
| 17.4 | MAX_AGENT_ITERATIONS | ✅ | |
| 17.5 | vision/反思清空 ToolRegistry | ✅ | |
| 17.6 | moderate/models 空 | 🧹 | 同 2.3 |

汇总：✅ 必留 ≈ 67 条 · ♻️ 可重构 ≈ 13 条 · 🧹 可清理 19 条（**已落地 9 / 未落地 10**）。

### 18.2 为什么是"必留"——三大类硬约束

下面这 19 条兜底是不可谈判的，删除会导致线上 / 协议 / 设备 / 用户预期出问题：

- **跨 SDK 版本**：[`AndroidReminderScheduler.canScheduleExactReminders` API<S 分支](../app/src/main/java/com/xiaoqi/companion/core/reminder/AndroidReminderScheduler.kt)、[`ContextPermissionReader.hasPostNotifications` API<TIRAMISU 分支](../app/src/main/java/com/xiaoqi/companion/core/context/ContextPermissionReader.kt)
- **跨协议**：[`McpHttpClient` 协议版本协商 + 404 重连 + SSE/JSON 双解析 + cache](../app/src/main/java/com/xiaoqi/companion/core/mcp/McpHttpClient.kt)
- **跨存储位置**：[`AppFilesLocalQwenModelLocator` 多目录候选](../app/src/main/java/com/xiaoqi/companion/core/local/LocalQwenModelLocator.kt)
- **跨模型能力**：[`OutputParser` 标签缺失默认值](../app/src/main/java/com/xiaoqi/companion/core/companion/OutputParser.kt)、[`CompanionRuntime` 空响应 → raw 回退](../app/src/main/java/com/xiaoqi/companion/core/companion/CompanionRuntime.kt)
- **跨用户操作**：[`SendMessageUseCase` 各种 disabled 分支](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)
- **跨用户状态**：[`PresenceController` mood 集合与 IDLE fallback](../app/src/main/java/com/xiaoqi/companion/core/presence/PresenceController.kt)
- **跨 Compose / View 互操作**：[`ChatScreen` `ViewCompat` IME insets](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatScreen.kt)

### 18.3 "可清理"清单（19 条）— 落地状态更新（2026-06-15）

按"删除收益"由高到低排列。**已落地的标 ✅，未落的标 ⏳**。

1. ✅ **`ConfigRepository` 用 `charArrayOf` 构造中文** — 已改普通字符串 `"缺失 $subject"` / `"模型名称"`；见 §3.5。
2. ⏳ **`AnthropicMessagesLLMClient.moderate` / `models()` 返回空** — 改成 `throw NotImplementedError("moderation not configured")`，调用方立即报错而不是被误当"通过"。
3. ✅ **`PromptConfigLoader` 资产缺失 → FileNotFound** — 已加 `FALLBACK_ASSET_PATH = "prompts/system_persona.default.yml"` + `parseWithFallback`；见 §17.1。
4. ✅ **`ConfigRepositoryImpl` API Key 缺失只 warn** — 已删 warn 日志，单纯依赖 `LlmConfigStatus.isReady=false` + `SendMessageUseCase` 拦截；见 §3.4。
5. ⏳ **`OutputParser` / `ChatMappers.after` mood 为空时保留旧值** — 同 §14.11；但在反思里模型未返回 mood 时 0.5 中性强度比"不更新"更合理，可保留。
6. ⏳ **`CompanionRuntime` 单 sessionId 硬编码 `"default"`** — roadmap 项；先抽出 `SessionRepository` 接口，预留多会话。
7. ✅ **`PromptBuilder` 占位符未匹配保留原样** — 已改 `[MISSING:key]`；见 §10.3。
8. ✅ 部分 **`PromptBuilder` 中文硬编码 "## 会话摘要" / "## 最近对话"** — 已挪到 `system_persona.yml` 的 `summariesTitle` / `recentTitle`，由 `SystemPersona.applyConfig` 加载；见 §10.5。
9. ✅ **`McpHttpClient.toToolSpecOrNull` schema 缺失 → `{"type":"object"}`** — 改为 `return null` + `mcp_tool_schema_missing` warn，工具被从 registry 过滤；见 §7.5。
10. ⏳ **`LocalQwenAgentWrapper.runStructured` / Vision 抛 UnsupportedOperation** — 配合 Settings → 本地模型页显式标"Local Qwen 不会自动保存记忆"和"Local Qwen 暂不支持图片"。当前实现已迁到 `ReactiveCompanion`，行为不变（§8.8）。
11. ✅ **`ConversationContextBuilder.truncateToTokenBudget` 取尾部** — 改中心裁剪（保留前 1/3 + 后 2/3 + 省略号）；见 §6.2。
12. ⏳ **`MemoryRepository.truncateToTokenBudget` 同上** — 仍是尾部裁剪。
13. ⏳ **`LocalQwenModelCatalogSizes` 写死 600/1600/3200 MB** — 改为读远端 manifest 或 manifest.json；或 UI 显示"未知大小"。
14. ⏳ **`ModelScopeLocalQwenModelDownloader` 单文件 404 → 整段重下** — 接入 Range resume / 多镜像 fallback。
15. ✅ **`AndroidReminderScheduler.scheduleExactAlarm` < M 旧 API 分支** — 已统一 `setExactAndAllowWhileIdle`，移除 `@Suppress("DEPRECATION")`；见 §11.2。
16. ✅ **`CreateLocalReminderTool` delayMinutes 强制下限 1** — 已改为直接返 `disabled("delay_too_small_minute")` 让 LLM 自重试；见 §11.6 / §11.5。
17. ⏳ **`CompanionToolRegistry` 用 `runBlocking` 读偏好** — 改成 `mcpServerListRepository.readAll()`（封装更好），但 `create()` 仍非 suspend，**阻塞 agent 调用线程的风险未根除**；见 §7.10。
18. ⏳ **`OpenMeteoWeatherProvider.weatherLabel` 表不全** — 补完整 WMO Code 列表（`docs/wmo-weather-codes.md`）。
19. ✅ 部分 **`PresenceController` mood 集合未匹配 → IDLE** — happy / sad / tired 集合已扩同义词（happy ← `{happy, joy, excited, warm, calm}`，tired ← `{tired, sleepy, exhausted, low}`）；见 §15.3。完全挪 yml 仍未做。

**汇总（2026-06-15）：✅ 已落地 9 / ⏳ 未落地 10**。剩余 10 条按依赖性分三批：

- **零风险纯清理**：2（moderate 抛 NotImplementedError）、5（mood 空保留旧值保留判断）、12（MemoryRepository 中心裁剪）。
- **行为不变重写实现**：10（Local Qwen 提示）、13（catalog 读远端）、17（create() 变 suspend）、18（WMO 表）。
- **依赖其它工作**：6（多 session）、14（Range resume / 多镜像）、19（mood 集合挪 yml）。

### 18.4 "可重构"清单（13 条）

不改外部行为，但需要重写实现。

1. **API Key 缺失与 modelName 静默回退**：UI 必须显式提示（红字 / toast / settings 面板高亮），不能再"宽容"。
2. **`CompanionRuntime` `rawResponse` 回退**：保留兜底，但触发 ≥ N 次 / 同一会话触发 → 标记为 `model_format_issue` 并降级为 plain text 模式。
3. **反思失败不阻断**：ChatViewModel 端要能展示"本轮未自动保存记忆"。
4. **相似记忆合并阈值写死 0.72**：挪到 `ConfigRepository` 或 `AppPreferences`。
5. **`McpHttpClient` 通知失败**：debug 日志保留 + 在 MCP 面板显式"初始化告警"。
6. **`CompanionToolRegistry` listTools 失败**：MCP 面板状态条要能看到。
7. **MCP 工具 cache**：保留但加"刷新工具"按钮（手动失效）。
8. **`MnnLocalQwenEngine.stream` 空 token 过滤**：保留过滤但加 debug 日志计数。
9. **下载进度按文件数 fallback**：UI 文案要区分"按字节" / "按文件"两种来源。
10. **`MnnLocalQwenEngine` delay < 1s 拉长**：UI 不再显示"1s 后"。
11. **流式批渲染 90ms / 48 chars 阈值**：挪到 `ConfigRepository`。
12. **`runStructured` 失败透出**：日志要能区分"本地 LLM 不支持" vs "provider 错误"。
13. **`ContentPart` 未知 → toString**：保留兜底但加 warn。

### 18.5 决策建议：分批落地（2026-06-15 更新）

把剩余 ⏳ 的 10 条按"是否影响数据 / 用户预期 / 协议"分三批：

- **第一批（零风险纯清理，1 个 PR）**：
  - 2（moderate 抛 NotImplementedError）、5（mood 空保留旧值保留判断）、12（MemoryRepository 中心裁剪）。
- **第二批（行为不变，重写实现，1~2 个 PR）**：
  - 10、13、17、18、19。
- **第三批（依赖其它工作）**：
  - 6（多 session）、14（Range resume / 多镜像）。

---

## 十九、风险总览 & 待清理建议（2026-06-15 更新）

按"沉默程度 + 频率"排序。**✅ 表示该风险已在最新模块落地时被缓解；⏳ 表示仍待清理。**

| 风险 | 位置 | 建议 / 当前状态 |
| --- | --- | --- |
| 反思失败 / 本地模型不支持 `runStructured` 用户毫无感知 | `CompanionRuntime` + `ReactiveCompanion`（原 `LocalQwenAgentWrapper`） | ⏳ UI 弹"本地模型不会自动保存记忆"提示 |
| MCP `listTools` 失败仅 warn | `CompanionToolRegistry` | ✅ Settings → MCP 面板已加"测试连接"按钮（§27.2） |
| `ConfigRepositoryImpl` 始终用 `DefaultLlmValues.defaultBaseUrl` 覆盖 | `ConfigRepositoryImpl.getCurrentLlmConfig` | ⏳ 拆分为 `allowCustomBaseUrl: Boolean` 标志 |
| MCP 工具 cache 无 TTL | `McpHttpClient.toolCache` | ⏳ Settings 加 "刷新 MCP 工具" 按钮 |
| `OutputParser` mood 集合固定 | `PresenceController` | ✅ 部分：happy/sad/tired 集合已扩同义词（§15.3）；完全挪 yml 仍未 |
| `ModelScopeLocalQwenModelDownloader` 单文件 404 后整段重下 | `LocalQwenModelDownloader.download` | ⏳ 接入 Range resume / 多镜像 |
| `truncateToTokenBudget` 取尾部 | `ConversationContextBuilder` / `MemoryRepository` | ✅ 部分：`ConversationContextBuilder` 已改中心裁剪（§6.2）；`MemoryRepository` 仍未 |
| `Base URL` 写死为 Anthropic 兼容 | `DefaultLlmValues` | ⏳ 增加 OpenAI 兼容分支 |
| `LocalQwenAgentWrapper.runStructured` 抛错 | 已迁到 `ReactiveCompanion` | ⏳ 给出一份基于规则的结构化兜底 |
| `AnthropicMessagesLLMClient.moderate` / `models()` 返回空 | `AnthropicMessagesLLMClient` | ⏳ 至少打 warn 日志 |
| 🆕 vision memory 写失败完全静默 | `SendMessageUseCase` + `MemoryRepository.saveVisionMemory` | ⏳ UI 弹"正在保存 vision memory"提示 |
| 🆕 Health Connect 部分 ROM 误报 `SDK_AVAILABLE` | `HealthConnectDataSource` | ✅ 已加 `hc_runtime_not_responsive` 探活（§21.1） |
| 🆕 Reminder 用户拒通知后到点无反应 | `ReminderNotificationPoster` | ⏳ UI 弹"通知被关"提示 |
| 🆕 微信输入法卡小面板 | `ChatInputBar` | ✅ 已加 350ms 轮询 + "Switch input" 引导（§30.1） |
| 🆕 Settings "Save" 按钮被屏外 | `SettingsScreen` | ✅ 改放 TopAppBar actions 永久可见（§31.2） |
| 🆕 api_key 静默不写 DataStore | `SettingsUseCase.saveSettings` | ✅ 改 `updateSettingsApiKey` 实时保存（§31.1） |
| 🆕 Onboarding 5 问强制必填 | `OnboardingScreen` | ✅ 全部可选可跳过（§28.1） |
| 🆕 vision memory 走 Validator 全拒 | `MemoryRepository.saveVisionMemory` | ✅ 绕开 Validator（§24.3） |

---

## 二十、变更日志

- **2026-06-14（初版）**：基于 `app/src/main/java/...` 全量扫描，匹配 `try / catch / ?: / runCatching / coerceIn / ifBlank / Build.VERSION.SDK_INT / firstNotNullOfOrNull / ProtocolVersion / tempDir` 等模式；覆盖 LLM 兼容层、配置、主管线、解析、记忆、MCP、本地 LLM、提示词、Reminder、Context Provider、UI、Presence、View 共 16 个模块。
- **2026-06-14（审计）**：新增第十八章"保留 vs 清理审计"。99 条兜底点裁决：✅ 必留 67、♻️ 可重构 13、🧹 可清理 19。明确分三批落地建议：零风险清理（6 条）/ 重写实现（10 条）/ 依赖其它工作（3 条）。
- **2026-06-15（M3 PoC 真机发现 + 修复）**：

  | # | 现象 | 位置 | 状态 |
  |---|------|------|------|
  | 1 | `SettingsScreen` "Save" 按钮被 `DataTransparencySection` 推到屏外，用户根本点不到 → `api_key` / `model_name` / `base_url` 等字段从未真写入 DataStore | `feature/chat/SettingsScreen.kt` `LazyColumn` 末尾 item | ✅ commit `85cb87c` 已修：Save 按钮改放 TopAppBar `actions`，永久可见 |
  | 2 | `settingsApiKey: String = ""` 默认空 → `saveSettings` 中 `value.trim().takeIf { it.isNotEmpty() }` 永远为 null → `setApiKey` 跳过 → 用户即便在 UI 填了 key，DataStore 也无 `api_key` 字段 | `feature/chat/usecase/SettingsUseCase.kt` `saveSettings` | ✅ commit `85cb87c` 已修：api_key 字段改在 `updateSettingsApiKey` 实时保存（不依赖 Save 按钮） |
  | 3 | `seedDemoInsights` 用 `evidenceMoodSnapshotIds = listOf("seed-1", "seed-2", "seed-3")` 但 DB 里没这 3 个 mood_snapshot → `InsightValidator` "50% 真实存在门槛" 全拒 → `insight_save_rejected_by_validator × 3` | `data/repository/InsightRepository.kt` `seedDemoInsights` | ✅ commit `9fa58ab` 已修：saveIfValid **前**先 `moodSnapshotDao.insert` / `messageDao.insert` / `memoryRepository.insertMemoryWithId` 真实 mock 行；evidence 引用真实 id |
  | 4 | `MoodSnapshotEntity.companion_id` 是外键引用 `agent_state` → seed 触发 `FOREIGN KEY constraint failed (787)` | `data/repository/InsightRepository.kt` `seedDemoInsights` | ✅ commit `9fa58ab` 已修：先 `agentStateDao.insert(AgentStateEntity(companionId="default", ...))` |
  | 5 | `OnboardingScreen` 5 问全强制必填（`canAdvance = q1.isNotBlank()`） → 用户体验差，违背 plan §5.2 "不强迫"产品调性 | `feature/onboarding/OnboardingScreen.kt` | ✅ commit `9fa58ab` 已修：`canAdvance = true` 5 问全可选可跳过 |
  | 6 | 本地 `LocalQwenEngine.stream()` 真实模型未下载时 58ms 返回空流 → `dream_loop_empty_model_output` → `Result.retry()` | `core/presence/runtime/DreamLoopWorker.kt` | 🔍 预期内，待用户在 SettingsScreen 触发模型下载后端到端可跑通；M3 PoC 主结论已验证 pipeline 调度 + 异常兜底 |
- **2026-06-15（文档全面整理）**：
  - 同步代码现状：§1 索引 / §3 / §6.2 / §7.5 / §7.10 / §8.8 / §9.5 / §10.3 / §10.5 / §11.2 / §11.5 / §11.6 / §12.3 / §14.15 / §15.3 / §17.1 行为描述与代码一致
  - 审计表刷新：§18.3 标出 **9 条已落地 / 10 条未落地**，§19 风险总览按 ✅ / ⏳ 标记最新状态
  - 新增 11 个章节覆盖 2026-06 后落地的兜底点：§21 Health 多源链 / §22 DreamLoop 调度 / §23 Insight 校验 / §24 Memory Vision / §25 Reminder 通知 / §26 隐私面板 / §27 连通性检查 / §28 Onboarding / §29 Memory Room 操作 / §30 ChatInputBar IME 兼容 / §31 Settings 实时保存
  - 总条目：原 99 条（§2-§17）+ 新增 30+ 条 → 实际覆盖率提升约 30%

---

## 二十一、Health 多源链（跨 OEM 设备硬约束）

> 2026-06 后落地，覆盖 §1 索引中的 Health Connect / SensorManager 双源 + 防抖 + 1.5s 探活。

### 21.1 Health Connect 误报兜底

- **触发**：`getSdkStatus(provider)` 返回 `SDK_AVAILABLE` 但 HealthConnect service 实际未挂载（ColorOS / realme / 部分 MIUI 常见）
- **兜底**：`HealthConnectDataSource.isAvailable` 在 `SDK_AVAILABLE` 后**真实探活**：`runCatching { readRecords(StepsRecord::class) }.getOrNull() == null` → `hc_runtime_not_responsive` 日志 + 返 `false`
- **位置**：[`HealthConnectDataSource.isAvailable`](../app/src/main/java/com/xiaoqi/companion/data/source/HealthConnectDataSource.kt)
- **风险**：跨 OEM 行为不一致，没有这层探活会出现"配置显示 HC 可用，同步全失败"的死链。

### 21.2 HC 不可用 → SensorManager 兜底

- **触发**：`HealthConnectDataSource.isAvailable == false`
- **兜底**：`SensorManagerHealthSource.syncRecentDays` 仍能写当日步数；用 `updateStepsOnly` 局部更新，**不抹掉** HC 已写入的心率 / 睡眠
- **位置**：[`SensorManagerHealthSource`](../app/src/main/java/com/xiaoqi/companion/data/source/SensorManagerHealthSource.kt)
- **风险**：SensorManager 路径只有步数，心率 / 睡眠永久缺失；UI 需在 HealthDataSection 标注"部分数据缺失"。

### 21.3 SensorManager 回调 1.5s 超时

- **触发**：`Sensor` 关 / 权限拒 / ROM 拦截 `TYPE_STEP_COUNTER`
- **兜底**：`readBaselineWithTimeout(timeoutMs = 1_500L)`：超时直接返 0 不抛
- **位置**：[`SensorManagerHealthSource.readBaselineWithTimeout`](../app/src/main/java/com/xiaoqi/companion/data/source/SensorManagerHealthSource.kt)
- **风险**：超时后 UI 会短暂显示步数 = 0；后续可考虑"上次成功值 fallback"。

### 21.4 `HealthSyncManager` 防抖 + 互斥 + StateFlow

- **触发**：同进程内 5s 内多次触发同步
- **兜底**：
  - 5s 防抖窗口：仅第一次触发实际跑，其余直接复用 `SyncState`
  - `Mutex` 串行化：避免并发写 `health_snapshots` 触发 SQLite BUSY
  - `SyncState` 五态：`Idle / Syncing / Skipped / Success / Failure`，UI 用 `collectAsStateWithLifecycle` 订阅
- **位置**：[`HealthSyncManager`](../app/src/main/java/com/xiaoqi/companion/data/source/HealthSyncManager.kt)

---

## 二十二、DreamLoop 调度（WorkManager + 电量）

### 22.1 WorkManager `BatteryNotLow` 约束

- **触发**：`Constraints.Builder().setRequiresBatteryNotLow(true)`
- **兜底**：电量低 → `Result.success()` 跳过本轮，下次约束满足再跑
- **位置**：[`DreamLoopWorker`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamLoopWorker.kt)

### 22.2 Snapshot 收集失败 → backoff retry

- **触发**：`DreamDataCollector.collectLast7Days` 抛 DB 异常
- **兜底**：`Result.retry()` + WorkManager 默认指数 backoff
- **位置**：[`DreamLoopWorker.doWork`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamLoopWorker.kt)

### 22.3 空数据窗口 → 直接成功

- **触发**：`Snapshot.isEmpty`（无图、无文本、无 mood）
- **兜底**：`Result.success()` —— 不浪费模型推理；不再进 `LocalQwenExecutor`
- **位置**：[`DreamDataCollector.isEmpty`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamDataCollector.kt)

### 22.4 本地模型未下载 → `Result.retry()`

- **触发**：`LocalQwenEngine.stream()` 58ms 返空流（M3 PoC 现象 #6）
- **兜底**：`dream_loop_empty_model_output` 日志 + `Result.retry()`；待用户在 Settings 触发模型下载后端到端跑通
- **位置**：[`DreamLoopWorker`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamLoopWorker.kt)

### 22.5 Validator 全拒 → 静默成功

- **触发**：`InsightValidator.saveIfValid` 全部 reject（缺 evidence / 信心度低 / 与近期重复）
- **兜底**：`insight_save_rejected_by_validator` debug 日志 + `Result.success()` —— DreamLoop 周期继续，本轮无产出
- **位置**：[`DreamLoopWorker`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamLoopWorker.kt)

### 22.6 `DreamLoopScheduler` 周期档位

- **触发**：用户在 Settings 改 `dreamLoopInterval`
- **兜底**：7 档（`OFF / 15min / 30min / 1h / 3h / 6h 默认 / 12h`）经 `AppPreferences.dreamLoopInterval` 暴露；改档位走 `ExistingPeriodicWorkPolicy.UPDATE` 立即生效；`triggerNow()` 走 OneTimeWorkRequest 唯一名 `"dream_loop_now"`
- **位置**：[`DreamLoopScheduler`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamLoopScheduler.kt)
- **风险**：15min / 30min 时 UI 显示耗电警告文案但仍允许选。

---

## 二十三、Insight 校验（跨数据源硬约束）

### 23.1 `InsightValidator` 4 道校验

- **触发**：DreamLoop 生成 candidate insight 准备落库
- **兜底**：依次检查 4 道门槛，任一失败即拒：
  1. **缺 evidence**：`evidenceMoodSnapshotIds` / `evidenceMessageIds` / `evidenceMemoryIds` 任意为空
  2. **50% 真实存在门槛**：evidence 引用的 id 至少 50% 真的在对应 DAO 里
  3. **信心度低**：`confidence < 0.6`
  4. **与近期重复**：与近 30 天已存 insight heading 的 Jaccard > 0.8
- **位置**：[`InsightValidator.saveIfValid`](../app/src/main/java/com/xiaoqi/companion/core/insight/InsightValidator.kt)
- **风险**：M3 PoC 现象 #3、#4 都是这道校验的副作用（seed evidence 引用假 id 触发全拒）；seed 必须先 mock 真实 DAO 行。

### 23.2 `seedDemoInsights` 的 mock 顺序

- **触发**：首次启动 / Debug build 触发种子数据
- **兜底**：`InsightRepository.seedDemoInsights` 必须按 `agentState → moodSnapshot → message → memory` 的顺序先插真实 mock 行，再 `InsightValidator.saveIfValid` —— 否则全部 `insight_save_rejected_by_validator`
- **位置**：[`InsightRepository.seedDemoInsights`](../app/src/main/java/com/xiaoqi/companion/data/repository/InsightRepository.kt)

---

## 二十四、Memory Vision（跨模态 / M4 闭环）

### 24.1 `MemoryEntity.imageBase64` + `MIGRATION_7_8`

- **触发**：Room schema 升级到 v8
- **兜底**：新增 `imageBase64 TEXT` + `imageMediaType TEXT DEFAULT 'image/jpeg'` 两列；旧库迁移时 `ALTER TABLE` 自动加列，老记录两个字段都是 NULL（视为文本记忆）
- **位置**：[`MemoryEntity`](../app/src/main/java/com/xiaoqi/companion/data/db/entity/MemoryEntity.kt)、[`CompanionDatabase.MIGRATION_7_8`](../app/src/main/java/com/xiaoqi/companion/data/db/CompanionDatabase.kt)
- **风险**：base64 直接存在 Room 会让 db 文件变大（M4 实测单图 ~50KB JPEG base64），长尾用户上千图会让 db 膨胀；后续应考虑外部化到 filesDir。

### 24.2 `saveVisionMemory` fire-and-forget

- **触发**：`SendMessageUseCase` 发 `UserInput.Vision` 之前
- **兜底**：`scope.launch { runCatching { memoryRepository.saveVisionMemory(...) }.onFailure { AppLogger.warn(...) } }`；失败仅 log 不阻塞主对话
- **位置**：[`SendMessageUseCase`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SendMessageUseCase.kt)、[`MemoryRepository.saveVisionMemory`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：vision memory 写失败完全静默，用户看到的是"图已发但 Aura 没记住"；UI 没有"正在保存"提示。

### 24.3 vision memory **不走** Validator

- **触发**：保存 vision memory
- **兜底**：`saveVisionMemory` 固定写 `type=FACT, source=reflection:vision`，**绕过** `InsightValidator` —— 视觉事实不需要"50% evidence 真实存在"判定
- **位置**：[`MemoryRepository.saveVisionMemory`](../app/src/main/java/com/xiaoqi/companion/data/repository/MemoryRepository.kt)
- **风险**：如果未来 vision memory 也想走 Validator，需显式传 evidence。

### 24.4 `DreamDataCollector` 跨模态 evidence 注入

- **触发**：DreamLoop 渲染 prompt
- **兜底**：`collectLast7Days` 调 `memoryDao.getRecentImages(IMAGE_MEMORY_LIMIT=5)`，过滤 7 天窗口；`Snapshot.imageMemories` 仅 metadata（id/content/timestamp/importance），**base64 不进 prompt**
- **位置**：[`DreamDataCollector.collectLast7Days`](../app/src/main/java/com/xiaoqi/companion/core/presence/runtime/DreamDataCollector.kt)
- **护栏**：注释里把"base64 永远不进 DreamPrompt"作为安全护栏写死；`render_doesNotLeakBase64` 测试做硬约束。

---

## 二十五、Reminder 通知（跨 SDK 通知权限）

### 25.1 `ReminderNotificationPoster.post` 静默跳过

- **触发**：`canPostNotifications(context) == false`（API 33+ 用户拒了 POST_NOTIFICATIONS）
- **兜底**：直接 return，不抛；AlarmManager 仍会触发，Receiver 仍会调到 Poster，但 Poster 静默吞
- **位置**：[`ReminderNotificationPoster.post`](../app/src/main/java/com/xiaoqi/companion/core/reminder/ReminderNotificationPoster.kt)
- **风险**：用户拒了通知后到点完全没反应；UI 没有"通知被关"提示。

### 25.2 `ReminderNotificationWorker` 工作约束

- **触发**：`WorkManager` 调度 reminder 通知
- **兜底**：`OneTimeWorkRequest` + 默认约束；进程死亡后由 WorkManager 重启；与 `AlarmManager` 双通道（§11.7）
- **位置**：[`ReminderNotificationWorker`](../app/src/main/java/com/xiaoqi/companion/core/reminder/ReminderNotificationWorker.kt)

---

## 二十六、隐私面板（跨用户操作）

### 26.1 `DataTransparencySection` 4 项操作

- **触发**：用户在 Settings 进入"数据透明"区
- **兜底**：
  - **条数展示**：`insightCount / memoryCount / moodSnapshotCount` 直接查 DAO
  - **JSON 导出**：经系统 SAF（`ActivityResultContracts.CreateDocument("application/json")`）写文件
  - **3 个清空按钮**：清空 insight / 清空记忆 / 清空 mood snapshot，各自走 `runCatching` + Bipass 二次确认（防误删）
- **位置**：[`ChatViewModel.DataTransparencySection`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **风险**：清空按钮没有"撤销"，二次确认对话框只防当前次；后续可加 30 天软删除。

### 26.2 导出 JSON 失败 → 用户可读错误

- **触发**：SAF 写文件异常
- **兜底**：`runCatching` 失败 → `error = "导出失败：${e.message}"`
- **位置**：同 §26.1

---

## 二十七、连通性检查（跨协议 / Settings 主动探活）

### 27.1 `LlmConnectivityChecker.check`

- **触发**：用户在 Settings / MCP Settings 点 "Test connection"
- **兜底**：对当前 baseUrl 走 `GET /v1/models` 探活；区分：
  - `Success(durationMs)`（200/204）
  - `AuthFailure(401/403)`
  - `Unreachable(timeoutMs)`（connect 5s / read 8s 超时或网络异常）
  - `LOCAL_QWEN` provider → 直接 `Success(0ms)`
- **位置**：[`LlmConnectivityChecker`](../app/src/main/java/com/xiaoqi/companion/core/llm/LlmConnectivityChecker.kt)
- **风险**：只能验"能不能连 + key 对不对"，不能验 model 是否真支持 vision / 工具。

### 27.2 多 MCP server 并行探活

- **触发**：MCP Settings 点 "Test connection"
- **兜底**：`mcpServerListRepository.readAll()` → 对每个 serverUrl 异步 `McpHttpClient.listTools`；各自缓存 `toolList` 到 `McpHttpClient.toolCache`（§7.8）；任何一个失败不影响其他
- **位置**：[`ChatViewModel.checkMcpConnectivity`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)

---

## 二十八、Onboarding（5 问全可选）

### 28.1 `OnboardingScreen` 5 问可选可跳过

- **触发**：用户首次启动进入 onboarding
- **兜底**：`canAdvance = true`，5 问（挂心事 / 重要日期 / 称呼 / 关系人 / 作息）全可空；空提交时仅写 onboarding_completed_at 时间戳，不污染 user_patterns
- **位置**：[`OnboardingScreen`](../app/src/main/java/com/xiaoqi/companion/feature/onboarding/OnboardingScreen.kt)
- **历史**：M3 PoC 现象 #5：早期版本 `canAdvance = q1.isNotBlank()` 强制第一问必填，违背"不强迫"调性；commit `9fa58ab` 已修。

### 28.2 模板表单不入 LLM

- **触发**：onboarding 答案落盘
- **兜底**：写入 `user_patterns_json` / `recurring_topics_json` 两个 DataStore key；模板字段硬编码（不解析为 LLM prompt），避免 LLM 通过答案反推用户隐私
- **位置**：[`OnboardingViewModel.submit`](../app/src/main/java/com/xiaoqi/companion/feature/onboarding/OnboardingViewModel.kt)

---

## 二十九、Memory Room 操作（runCatching 模式）

### 29.1 长按菜单 5 动作

- **触发**：用户在 `MemoryRoomScreen` 长按某条记忆
- **兜底**：弹层 5 选项 — 置顶 / 取消置顶 / 归档 / 取消归档 / 删除；全部走 `ChatViewModel` 内的 `pinMemory / unpinMemory / archiveMemory / unarchiveMemory / deleteMemory`，**统一 `runCatching` 模式**：
  - 成功 → reducer 触发 `uiState` 更新
  - 失败 → `error = "操作失败：${e.message}"`，不抛给上层
- **位置**：[`ChatViewModel.pinMemory` 等](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)
- **风险**：与 `cancelReminder` / `deleteMemory` 是同一模式（§14.13）；重构者可考虑抽 `runUiAction` 高阶函数。

### 29.2 Insight 卡片 3 动作

- **触发**：用户长按主页 Insight 卡片
- **兜底**：弹层 4 选项 — 本周不再说 X 类 / 知道了 / 查看依据 / 和 Aura 聊聊；后 3 个走 `dismissInsight / muteInsightCategory(days=7) / openInsight`，同样 `runCatching`
- **位置**：[`ChatViewModel.dismissInsight`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatViewModel.kt)

---

## 三十、ChatInputBar 微信 IME 卡顿检测（View 兼容）

### 30.1 微信输入法卡小面板检测

- **触发**：用户用微信输入法时偶发 IME bottomInset 不更新（"卡小面板"现象）
- **兜底**：`ChatInputBar` 350ms 轮询 `ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.ime())`；`MIN_USEFUL_IME_HEIGHT_RATIO = 0.12f`（IME 高度 < 屏幕 12% 视为无效）；`ImeSnapshot.isWeChatInputMethod` 判定是否微信输入法
- **位置**：[`ChatInputBar.currentImeSnapshot`](../app/src/main/java/com/xiaoqi/companion/feature/chat/ChatInputBar.kt)
- **兜底 UI**：检测到 stuck 时显示 "Switch input" 引导用户切走微信输入法
- **风险**：350ms 轮询 + `ViewCompat` 调用是历史妥协（Compose `WindowInsets.ime` 扩展属性是 `@Composable`，不能在 `snapshotFlow` 非 Composable lambda 里调用）；见 §16.1。

---

## 三十一、Settings 实时保存（PR-C + M3 PoC UX 修复）

### 31.1 api_key 不依赖 Save 按钮

- **触发**：用户在 Settings 输入框编辑 api_key
- **兜底**：`SettingsUseCase.updateSettingsApiKey`（`onValueChange` 即时调用）走 DataStore `setApiKey`；不依赖 Save 按钮；M3 PoC 现象 #2 已修
- **位置**：[`SettingsUseCase.updateSettingsApiKey`](../app/src/main/java/com/xiaoqi/companion/feature/chat/usecase/SettingsUseCase.kt)

### 31.2 Save 按钮永久置顶

- **触发**：用户进入 SettingsScreen
- **兜底**：`SettingsScreen` 把 Save 按钮放在 `TopAppBar actions`，永久可见；不再被 `LazyColumn` 末尾的 `DataTransparencySection` 推到屏外；M3 PoC 现象 #1 已修
- **位置**：[`SettingsScreen`](../app/src/main/java/com/xiaoqi/companion/feature/chat/SettingsScreen.kt)

---

## 三十二、日志体系

> **历史**:2026-06-16 一次性补 P0(隐私泄漏 + 关键 catch 静默吞)+ P1(`core/insight` 整目录 + `feature/chat` 关键页)日志缺口。`./gradlew.bat testDebugUnitTest` 479 个测试 0 失败。

### 32.1 架构

- **统一入口** [`AppLogger`](../app/src/main/java/com/xiaoqi/companion/core/logging/AppLogger.kt) — 5 个方法:`verbose / debug / info / warn / error`,全部基于 Timber
- **15 个 `LogTags`** [`LogTags`](../app/src/main/java/com/xiaoqi/companion/core/logging/LogTags.kt) — `App / Chat / Runtime / Llm / LocalModel / Prompt / Parser / Repo / Tools / Reminder / Config / Emotion / Relation / Database / HealthConnect`
- **3 个 `LogEventType`** — `Diagnostic / Audit / Failure`,格式化时作为 `type=` 前缀
- **结构化日志**:`type=Audit event=foo key1=value1 key2=value2`
- **自动脱敏** [`LogFieldSanitizer`](../app/src/main/java/com/xiaoqi/companion/core/logging/LogFieldSanitizer.kt) — 字段名命中 `apikey/authorization/prompt/text/message/url/base64/image/input/response/secret/token/content` 时值走 SHA-256 hash
- **Debug/Release 双树** [`SafeLogTree`](../app/src/main/java/com/xiaoqi/companion/core/logging/SafeLogTree.kt) — `SafeDebugTree` 全打,`SafeReleaseTree` 只 WARN+,且 release 不走 Timber(直接 `Log.println`)

### 32.2 业务层不准直接 `android.util.Log`(P0-1 已修)

- **触发**:任何业务代码使用 `android.util.Log.*`
- **兜底**:走 `AppLogger`,享受自动脱敏 + Debug/Release 树分流
- **位置**:业务层 0 处 `android.util.Log`(2026-06-16 后;`AppLogger.kt` / `SafeLogTree.kt` 内部 import 除外)
- **历史修复**:
  - `HealthDataSection.kt:199` 双重 `runCatching` 静默吞 → 拆 `open_settings_intent_failed_primary` / `open_settings_intent_failed` 两条 warn
  - `SettingsScreen.kt:820` 导出失败 → `error(..., "export_all_failed")`

### 32.3 关键路径 `runCatching` 不准静默(P0-2 已修)

约 20 处"关键 catch 不打 log"已逐一补 `AppLogger.warn` / `debug` / `error`,覆盖:

| 文件 | 触发点 | 补的 event |
|---|---|---|
| `McpHttpClient.kt:355` | SSE JSON 解析 | `mcp_sse_payload_parse_failed` |
| `AnthropicMessagesLLMClient.kt:461` | SSE event 解析 | `sse_event_parse_failed` |
| `MemoryRepository.kt:587-588` | memory tags/keywords 合并 | `merge_json_list_left_failed` / `_right_failed` |
| `InsightRepository.kt:296` | evidence JSON 解析 | `insight_evidence_parse_failed` |
| `LocalQwenExecutor.kt:96` | 本地模型输出 JSON 解析 | `insight_json_parse_failed` |
| `OnboardingViewModel.kt:62` | 5 问落 LTM | `onboarding_memories_save_failed` |
| `ToolCallResultParser.kt:212` | legacy 工具 JSON 解析 | `tool_legacy_parse_failed` |
| `ToolEnvelope.kt:149/153` | 信封 Ok/Error 形态 | `envelope_ok_parse_failed` / `_error_parse_failed` |
| `SearchRecordsTool.kt:88` | FTS 查询 | `search_records_fts_failed` |
| `GetCurrentTimeTool.kt:41` | `ZoneId.of` 非法字符串 | `invalid_timezone_fallback` |
| `McpServerListRepository.kt:118` | MCP server 列表 JSON 解析 | `mcp_servers_parse_failed` |

### 32.4 `core/insight` 整目录 0/4 → 4/4(P1-1 已修)

- **历史**:整目录零 `AppLogger` 调用,而 §22.5 / §23.1 / §23.2 反复提"insight 校验 4 道门槛是 M3 PoC 调试高发路径"
- **修复**:
  - `InsightValidator.kt:30-58` 4 道校验(no_evidence / evidence_reality_check / low_confidence / duplicate_heading)每道都加 `AppLogger.debug(..., "insight_rejected", "stage" to ..., ...)`
  - `AutoMemoryStore.kt:43` `decodeList` 静默吞 → `auto_memory_list_decode_failed`
- **未改**:`InsightDraft.kt` / `InsightPrompts.kt` 是字面量,无可观测事件

### 32.5 `feature/chat` 关键页补日志(P1-2 已修)

用户报"我点了没反应"时,logcat 现在能看到完整链路:

| 触发点 | event | 用途 |
|---|---|---|
| `AuraHomeScreen` Insight 短按 | `insight_tapped` | 验证 M3 真机报告"短按无反应"复现路径 |
| `AuraHomeScreen` Insight 长按 | `insight_long_pressed` | 与短按区分 |
| `AuraHomeScreen` Insight 隐藏/取消隐藏 | `insight_dismissed` | 行为追踪 |
| `AuraHomeScreen` "和 Aura 聊聊" | `insight_chat_pressed` / `_from_action` | 预填 prompt 路径 |
| `AuraHomeScreen` 4 个弹层动作 | `insight_action_dismissed` / `_category_muted` / `_acknowledged` | 闭环追踪 |
| `AuraHomeScreen` 依据展开 | `insight_evidence_toggled` | UI 行为 |
| `ChatViewModel.attachImage` | `attach_image_empty_uri` / `_started` | 图片路径 |
| `ChatViewModel.removePendingImage` | `remove_pending_image` | UI 行为 |
| `ChatViewModel.sendMessage` | `send_message_started` | 区分发送失败 / 没发 |

### 32.6 仍存在的缺口(P2+,本轮不修)

1. **无文件持久化** — 当前 logcat 看完即丢,用户拿不到日志。需加 `BufferedWriter` + SAF 导出,见 `DataTransparencySection` 现成的 SAF 链路可复用
2. **无 Settings 日志开关** — release 树只打 WARN+,但用户无"暂时记录全部 / 关闭全部"二档
3. **无采样 / 限速** — 任何高频路径(`ChatScreen` IME 350ms 轮询、Memory 列表滚动)理论上都会打 log,但实测不会爆量
4. **tag 分布不均** — `Repo 37 / Tools 28 / Llm 28 / LocalModel 23 / Config 23` 头部 5 个,`Emotion 1 / Relation 1 / Database 0` 尾部 3 个几乎闲置
5. **`verbose()` 方法定义但 0 调用** — 保留作为粒度选择,或后续删除

