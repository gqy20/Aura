# Vision 与 Agent Tools 协同计划

> Last updated: 2026-06-16
> Scope: Aura Android app, Koog 0.8.0, GLM-5v-turbo Vision, Agent tools
>
> 2026-06-16 同步：M4 vision→memory→dream 闭环已在 commit `1b826d1` 落地（见 §7.9 "2026-06-15 更新"）。P0 / P1 / P1.5 全部 ✅，P2 / P3 未做（依赖 P2 的 tool decision 抽象 + M5 Pulse 周期任务）。

## 1. 背景

当前系统选图 MVP 已经跑通：

- Chat UI 可通过系统 Photo Picker 选择图片。
- `ChatImageProcessor` 将图片压缩为 JPEG/base64。
- `ChatViewModel` 在有图片时发送 `UserInput.Vision`。
- `KoogAgentFactoryImpl` 将 `BuiltPrompt` 转为 Koog prompt DSL 的 `text + image` content。
- GLM Anthropic-compatible endpoint 已验证可识别真实 JPEG 图片。

为解决真机测试中“图片消息已发送但没有回复”的问题，当前实现对 Vision prompt 使用 `ToolRegistry.EMPTY`，也就是带图请求暂时禁用 Agent tools。

这个修复是合理的 MVP 稳定性保护，但后续如果希望 Vision 场景继续使用记忆、情绪、关系等工具，需要重新设计工具调用策略。

## 2. 问题复盘

真机日志显示，Vision 请求并不是没有发出，也不是图片处理失败：

```text
message_send_started hasImage=true
pipeline_started hasImage=true
prompt_built hasImage=true
request_built model=glm-5v-turbo
response_tool_use toolNames=update_mood
response_tool_use toolNames=update_mood
response_tool_use toolNames=update_mood,update_relationship
...
SocketTimeoutException: timeout
```

也就是说，模型持续返回 `tool_use`，Koog 按策略执行工具并把工具结果继续发回模型，但模型迟迟没有返回最终 `Assistant` 文本。最后接口等待响应超时，UI 只显示了用户图文消息。

核心问题不是工具本身失败，而是 **Vision 主回复被开放式工具循环阻塞**。

## 3. Koog 源码依据

本次分析基于本地 Gradle 缓存中的 Koog 0.8.0 sources：

- `agents-core-jvm-0.8.0-sources.jar`
- `agents-ext-jvm-0.8.0-sources.jar`

关键源码位置：

- `ai/koog/agents/ext/agent/SingleRunStrategyWithHistoryCompression.kt`
- `ai/koog/agents/core/agent/AIAgentSimpleStrategies.kt`
- `ai/koog/agents/core/dsl/extension/AIAgentNodes.kt`
- `ai/koog/agents/core/agent/session/AIAgentLLMReadSessionCommon.kt`

### 3.1 默认 single-run 策略

Koog 的 single-run 策略大体流程是：

```text
nodeStart
  -> nodeLLMRequest / nodeLLMRequestMultiple
  -> if tool calls: nodeExecuteTool / nodeExecuteMultipleTools
  -> nodeLLMSendToolResult / nodeLLMSendMultipleToolResults
  -> if assistant message: nodeFinish
  -> if more tool calls: repeat
```

源码里对应的边类似：

```kotlin
edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
edge(nodeExecuteTool forwardTo nodeSendToolResult)
edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
```

`singleRunStrategyWithHistoryCompression` 在此基础上增加了历史压缩，但工具循环机制相同。

### 3.2 Koog 支持禁用工具的一轮 LLM 调用

`AIAgentNodes.kt` 中 `nodeLLMRequest` 支持：

```kotlin
nodeLLMRequest(allowToolCalls = false)
```

它内部会调用：

```kotlin
requestLLMWithoutTools()
```

`AIAgentLLMReadSessionCommon.kt` 中 `requestLLMWithoutTools()` 会把工具列表置空，并清除 `toolChoice`：

```kotlin
val promptWithDisabledTools = prompt
    .withUpdatedParams { toolChoice = null }
    .let { preparePrompt(it, emptyList()) }

return executeMultiple(promptWithDisabledTools, emptyList())
```

这说明 Koog 原生支持“某一轮只要自然语言回复，不允许工具调用”。

## 4. 设计原则

后续恢复 Vision 场景工具调用时，应遵循：

1. **用户可见回复优先**
   图片消息必须优先产生 Assistant 文本。工具调用不能阻塞主回复。

2. **Vision 与工具分阶段**
   不让模型在同一轮里同时承担“看图、决定工具、执行工具、继续推理、最终回复”的全部责任。

3. **工具白名单**
   Vision 前置工具只允许对主回复有直接帮助的工具，例如 `search_memory`。状态类工具应后置。

4. **硬限制**
   工具阶段必须有轮数、超时、失败降级策略。

5. **状态更新后置**
   `update_mood`、`update_relationship` 更适合在回复完成后异步执行，或者直接由 `OutputParser` / Runtime 根据最终回复更新。

## 5. 推荐架构

### 5.1 短期：保持 Vision 主链路无工具

当前已实现：

```kotlin
.toolRegistry(if (prompt.hasImage) ToolRegistry.EMPTY else toolRegistry.create())
```

这保证 Vision 请求稳定返回文本，适合作为 MVP 默认行为。

短期只需要补齐：

- 更明确的日志：记录 `visionToolsEnabled=false`。
- 文档：说明 Vision MVP 默认禁用 tools。
- UI 错误提示：超时时显示可重试，而不是只消失 assistant bubble。

### 5.2 中期：回复后异步工具任务

主链路：

```text
UserInput.Vision
  -> Vision LLM without tools
  -> Assistant reply shown to user
  -> OutputParser parse reply
  -> update emotion / relationship locally
  -> optional background memory extraction
```

适合后置的工具/任务：

- `update_mood`
- `update_relationship`
- `save_memory`

这里不一定要继续通过 Koog tool loop。更稳的做法是 Runtime 自己调用领域服务或 DAO：

```text
Assistant reply
  -> lightweight local parser
  -> EmotionStateMachine.feed(...)
  -> RelationshipModel.update(...)
  -> Memory extraction task if needed
```

优点：

- 用户先看到回复。
- 状态更新失败不影响聊天体验。
- 避免模型为了状态工具陷入循环。

### 5.3 中期增强：Vision 前置只读工具

如果希望看图时结合记忆，可允许一类只读工具：

- `search_memory`

但建议不要让模型自由决定无限工具循环，而是由 Runtime 显式执行：

```text
User image + user text
  -> Runtime builds search query from user text
  -> search_memory / MemoryDao query
  -> inject memory snippets into Vision prompt
  -> Vision LLM without tools
```

这比“让模型先 tool_use 再回答”更可控。

### 5.4 长期：自定义 Koog Vision Strategy

如果确实要保留 Koog graph strategy 的工具能力，可以实现一个自定义 strategy：

```text
nodeStart
  -> nodeVisionUnderstandWithoutTools
  -> nodeOptionalToolDecision
  -> nodeExecuteWhitelistedTools
  -> nodeFinalReplyWithoutTools
  -> nodeFinish
```

关键点：

- `nodeVisionUnderstandWithoutTools` 使用 `requestLLMWithoutTools()`。
- `nodeOptionalToolDecision` 只允许白名单工具。
- `nodeFinalReplyWithoutTools` 再次禁用工具，强制产出 Assistant 文本。
- 工具阶段最多 1 轮或 2 轮。

伪代码形态：

```kotlin
val nodeVisionReply by nodeLLMRequest(allowToolCalls = false)
val nodeToolDecision by nodeLLMRequestMultiple()
val nodeExecuteTools by nodeExecuteMultipleTools(parallelTools = false)
val nodeFinalReply by nodeLLMRequest(allowToolCalls = false)

edge(nodeStart forwardTo nodeVisionReply)
edge(nodeVisionReply forwardTo nodeFinish onAssistantMessage { noToolNeeded })
edge(nodeVisionReply forwardTo nodeToolDecision onCondition { needsTool })
edge(nodeToolDecision forwardTo nodeExecuteTools onMultipleToolCalls { allowedToolsOnly })
edge(nodeExecuteTools forwardTo nodeFinalReply)
edge(nodeFinalReply forwardTo nodeFinish onAssistantMessage { true })
```

这个方案保留 Koog 的 graph/event handler 能力，但比默认 single-run 更适合 Vision。

## 6. 工具分类建议

| 工具 | Vision 主回复前 | Vision 回复后 | 说明 |
|------|----------------|---------------|------|
| `search_memory` | 可选 | 可选 | 只读，适合补上下文 |
| `save_memory` | 不建议 | 推荐 | 可能改变状态，后置更稳 |
| `update_mood` | 不建议 | 推荐 | 可由最终回复解析后更新 |
| `update_relationship` | 不建议 | 推荐 | 可由最终回复解析后更新 |

## 7. 实施计划

### P0：文档与日志

- [x] 在 `docs/roadmap.md` 标记 Photo Picker Vision MVP 已实现。（`roadmap.md` 2026-06-15 更新 + `architecture.md` 当前实现状态 M4 段）
- [x] 在 Koog 集成文档记录：Vision MVP 默认禁用 tools。（`koog-android-integration.md` 5.1 节 + 7 节）
- [x] 在 `KoogAgentFactoryImpl` 增加日志字段：`hasImage`、`toolRegistryMode`。

验收：

- 真机发送图片能看到 Assistant 回复。
- 日志能明确看到 Vision 走 no-tools 模式。

### P1：回复后状态更新

- [x] 保持 Vision 主回复无工具。✅（`KoogAgentFactoryImpl` 在 `prompt.hasImage` 时 `toolRegistry = ToolRegistry.EMPTY`）
- [x] 在 `CompanionRuntime` 完成回复后，根据 parsed output 更新 emotion / relationship。✅（`SendMessageUseCase.Complete` 事件后 `persistStatus` 写 `agent_state`）
- [x] 将 `save_memory` 类任务改成可选后置任务，失败只记录日志。✅（M4 落地：vision 消息 fire-and-forget 调 `MemoryRepository.saveVisionMemory`，try/catch 兜底仅 log；详见 commit `1b826d1`）

验收：

- 图片回复不会因状态更新失败而失败。✅
- 情绪/关系仍能随图片对话更新。✅

### P1.5：Vision→Memory→Dream 闭环（2026-06-15 落地）

> 不在原 P0-P3 路线内，源自新叙事主轴"Aura 记得你看见了什么"。

- [x] `MemoryEntity` 加 `imageBase64 TEXT` + `imageMediaType TEXT DEFAULT 'image/jpeg'` + `MIGRATION_7_8`（Room v8）。
- [x] `MemoryDao.getRecentImages/observeImages` 按 `imageBase64 IS NOT NULL` 过滤。
- [x] `MemoryRepository.saveVisionMemory` 把"用户发图"作为 `type=FACT, source=reflection:vision` 记忆写入。
- [x] `SendMessageUseCase` 注入 `MemoryRepository`，发送带图消息时 fire-and-forget 调 `saveVisionMemory`。
- [x] `DreamDataCollector.Snapshot.imageMemories`（metadata-only，不含 base64）+ `render` 新增 `## 视觉证据(N 张)` section。
- [x] 11 个新单测覆盖：窗口过滤 / limit 透传 / base64 隔离 / section 输出 / 无图省略 / 防泄漏 / 自动落库 / 失败不影响主流程。

设计约束：

- **base64 永远不进 DreamPrompt**（本地 Qwen 纯文本路径，模型看不到图），仅 metadata 进 prompt 让 LLM 推测用户视觉节奏。
- **`render_doesNotLeakBase64` 测试作为安全护栏**，防止后续维护者误把 `imageBase64` 加进 `ImageMemorySummary`。

### P2：只读记忆注入

- [ ] 为 Vision 输入构造 memory query。
- [ ] 使用 `MemoryDao` 或 `search_memory` 的底层逻辑提前检索相关记忆。
- [ ] 将记忆片段注入 Vision prompt。

验收：

- Vision 回复能引用相关长期记忆。
- 不出现 tool loop。

### P3：自定义 Vision Strategy

- [ ] 新增 `visionStrategy`，基于 Koog `strategy {}` DSL。
- [ ] 第一轮和最终轮使用 `requestLLMWithoutTools()`。
- [ ] 工具阶段使用白名单和最大轮数限制。
- [ ] 补单测模拟“模型先 tool_call 后 final assistant”的路径。

验收：

- 有工具需求时最多 1-2 轮工具调用。
- 必定尝试 final no-tools assistant response。
- 超时或工具失败时返回降级文本，而不是空回复。

## 8. 风险与对策

| 风险 | 表现 | 对策 |
|------|------|------|
| 模型偏好工具调用 | 反复 `tool_use`，无最终文本 | final response 强制 no-tools |
| Vision payload 过大 | 请求慢或超时 | 压缩、限制尺寸、日志记录图片字节数 |
| 工具结果继续诱导工具 | 工具链循环 | 工具阶段限轮，状态工具后置 |
| UI 等待过久 | 用户以为无响应 | 保留 loading + 明确超时 snackbar |
| 多供应商兼容不一致 | Kimi/GLM tool_use 差异 | 按 provider 配置 tool policy |

## 9. 当前结论

当前提交中对 Vision 禁用 tools 是正确的 MVP 策略。后续不要简单把 `ToolRegistry.EMPTY` 改回完整工具注册表。更好的演进路径是：

```text
Vision 主回复 no-tools
  -> 后置状态/记忆任务     ✅ P1 已落(save_memory 后置、emotion/relationship 写后)
  -> 只读记忆预注入         ⏳ P2 未做
  -> 自定义 Koog Vision Strategy  ⏳ P3 未做
```

这样既能保持图片聊天的稳定性，又能逐步恢复智能体工具能力。

### 2026-06-15 更新

P1 + P1.5（vision→memory→dream 闭环，commit `1b826d1`）已落地。`docs/roadmap.md` 标记 M4 部分完成。

- **P1**：Vision 主回复无工具 / 后置状态更新 / `save_memory` 后置 fire-and-forget — 全 ✅
- **P1.5**（新增）：视觉内容进入 `memories` 表 + DreamDataCollector 跨模态 evidence 注入（metadata-only，**base64 不进 DreamPrompt**）— 全 ✅
- **P2**：Vision 输入时预注入相关 memory 片段到 prompt — 未做（M4 PoC 阶段未跑通前，避免引入额外 LLM 延迟）
- **P3**：自定义 Vision Strategy（白名单工具 + 强制 final no-tools）— 未做（依赖 P2 的 tool decision 抽象）
- **M4 余下**：CameraX UI（当前走 Photo Picker 选图满足 MVP）、Connection 类 insight 端到端、Pattern 跨 mood+memory+图片三种数据源的真机验证。
