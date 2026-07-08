# Aura 云端核心智能体规范

> Last updated: 2026-07-08
>
> Scope: 云端对话体（Responsive Mind）、Koog Agent 主循环、Anthropic Messages 兼容模型、工具策略、移动端体验兜底。
>
> 本文是实现规范，不是愿景文档。它用于约束后续改造：模型配置、工具调用、Vision、多轮任务、记忆/状态写入、远端 Agent Server 接入，都应先满足这里的规则。

## 1. 核心定位

Aura 的核心用户体验必须围绕云端模型展开：

```text
云端模型 = 主对话 / 主办事 / 外部工具 / 高质量推理
本地模型 = 后台觉察 / Dream Loop / POST_CHAT insight / 离线低风险分析
Android = 亲密交互 / 本地状态 / 用户授权边界 / 失败兜底
Server = 重型工具 / 浏览器 / MCP Gateway / 长任务 / 云端记忆
```

### 1.1 不变量

- 主聊天默认走云端 provider，不把本地 Qwen 作为普通用户的主聊天推荐路径。
- 本地 Qwen 不承担高风险动作、远程工具、浏览器、复杂多步任务。
- Android 端永远保留用户确认、隐私开关、工具状态展示和本地数据删除能力。
- 云端工具调用必须可观测、可取消、可降级。
- 任何模型输出都不能直接越过 policy 写入高风险外部系统。

## 2. 分层规范

云端主链路按五层组织：

```text
Chat UI
  -> Conversation Runtime
  -> Context Builder
  -> Agent Policy
  -> Cloud Agent Loop
  -> Post-turn Processor
```

### 2.1 Conversation Runtime

职责：

- 接收 `UserInput`。
- 发出 `Flow<AgentEvent>`。
- 保存 user / assistant message。
- 保证首字、流式、错误、超时、取消体验稳定。

不得包含：

- Provider 专属协议细节。
- 复杂工具路由策略。
- 远端工具凭据。

当前对应：`CompanionRuntime` + `SendMessageUseCase`。

### 2.2 Context Builder

职责：

- 组装最近对话、记忆、摘要、设备/位置/时间、insight。
- 控制 token budget。
- 把上下文分段注入，不把所有数据塞成一个大段文本。

规则：

- 最近对话必须有 token 上限。
- 记忆注入必须 top-K，不能全量注入。
- 图片 base64 不得进入非 Vision prompt。
- 页面、MCP、浏览器返回内容必须标记为不可信数据。

### 2.3 Agent Policy

这是当前最缺的一层。它负责在进入模型工具循环前，判断本轮能力边界。

建议第一版定义：

```kotlin
enum class AgentTurnMode {
    CHAT_ONLY,
    CHAT_WITH_LOCAL_TOOLS,
    VISION_REPLY_ONLY,
    VISION_WITH_PRE_CONTEXT,
    REMOTE_TOOL_TASK,
    REQUIRES_CONFIRMATION,
    LONG_RUNNING_TASK,
}
```

Policy 输入：

- `UserInput` 类型。
- Provider 能力。
- 用户工具开关。
- MCP server 可用性。
- 是否包含图片。
- 本轮风险等级。
- 最近 tool loop 状态。

Policy 输出：

- 是否允许工具。
- 允许哪些工具。
- 最大工具轮数。
- 是否需要用户确认。
- 是否走远端 Server。
- 失败时如何降级。

### 2.4 Cloud Agent Loop

当前可继续使用 Koog `AIAgent` + 自定义 strategy。

规则：

- 默认工具调用必须串行，除非工具被标记为只读且互不依赖。
- 每轮必须设置最大工具迭代次数。
- 必须检测重复工具调用：同一 `toolName + argumentsJson` 连续重复且结果无新增信息时中止工具循环。
- 工具失败后必须尽量生成自然语言回复，不允许 UI 长时间空白。
- 有图片的主回复轮次默认 no-tools。

### 2.5 Post-turn Processor

职责：

- 情绪、关系、记忆写入兜底。
- 触发 POST_CHAT insight。
- 记录性能、错误、工具轨迹。
- 做可恢复的后置任务。

规则：

- 不能只依赖 `update_state` 工具。模型没有调工具时，也要有轻量后置兜底。
- 后置记忆保存失败不阻塞主回复。
- 高风险状态变更必须可回滚或可删除。
- 用户可见回复完成优先于后台整理。

## 3. Provider 与模型配置规范

### 3.1 Provider 能力矩阵

每个 provider 必须显式声明能力：

```kotlin
data class ProviderCapabilities(
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val supportsThinking: Boolean,
    val maxContextTokens: Int,
    val defaultToolPolicy: ToolPolicy,
)
```

用途：

- UI 显示当前能力。
- Policy 决定是否给 tools。
- Vision 轮次判断是否可用。
- 调试时能解释“为什么本轮没调用工具”。

### 3.2 Base URL 规则

- 用户界面允许编辑的配置必须真实参与 `LlmConfig`。
- 默认值可以来自 `DefaultLlmValues`，但不得静默覆盖用户保存的 Base URL。
- 连通性检查必须使用和实际请求完全相同的 provider / baseUrl / model / apiKey。
- 每个 provider 的 API key 独立保存，legacy key 只能作为迁移兜底。

### 3.3 推荐默认路径

- 主聊天默认：GLM Anthropic Messages 兼容端点。
- 中文长上下文/文本备选：Kimi。
- 大模型推理/成本可控备选：ModelScope Qwen。
- 本地 Qwen：设置中标记为“本地分析/实验”，不作为默认主聊天推荐。

## 4. 工具策略规范

### 4.1 工具分类

所有工具必须属于一个类别：

```text
READ_CONTEXT       只读上下文：time, device, weather, health, memory search
LOCAL_WRITE        本地写入：update_state, create_local_reminder
REMOTE_READ        远端只读：web search, browser read, map search
REMOTE_WRITE       远端写入：send message, create calendar event, submit form
HIGH_RISK_ACTION   购买、删除、发帖、发送外部消息、授权、支付
```

### 4.2 风险等级

```text
LOW      可自动执行，必须记录
MEDIUM   会话级确认或首次确认
HIGH     每次执行前确认
BLOCKED  当前版本禁止执行
```

规则：

- `READ_CONTEXT` 默认 LOW。
- `LOCAL_WRITE` 默认 MEDIUM，提醒类可通过系统权限提示确认。
- `REMOTE_WRITE` 默认 HIGH。
- `HIGH_RISK_ACTION` 第一阶段全部 BLOCKED，直到 Server policy 和确认 UI 完成。

### 4.3 Tool Loop 限制

每个云端 turn 必须有：

- `maxToolCallsPerTurn`。
- `maxToolRoundsPerTurn`。
- 单工具超时。
- 整体 turn 超时。
- 重复工具调用检测。
- 工具失败后的 final no-tools 回复尝试。

建议初始值：

```text
maxToolRoundsPerTurn = 3
maxToolCallsPerTurn = 6
singleToolTimeoutMs = 8000
finalReplyTimeoutMs = 30000
```

### 4.4 MCP 工具

规则：

- Android 端可以保留轻量远程 MCP 配置，但重型 MCP 应迁移到 Server。
- MCP `listTools` 结果应缓存，不能在首条消息路径上无超时同步阻塞。
- MCP 工具必须经过白名单和风险分级后再暴露给模型。
- MCP server 返回的内容进入模型前必须标记为外部不可信数据。
- 需要第三方授权时走 Server 托管授权，Android 只展示用户确认和授权状态。

## 5. Vision 规范

Vision 主链路必须分阶段。

### 5.1 默认流程

```text
UserInput.Vision
  -> 保存用户图文消息
  -> fire-and-forget 保存 vision memory
  -> Vision LLM no-tools 生成用户可见回复
  -> Post-turn Processor 更新状态/记忆兜底
  -> Dream Loop 后续使用 image metadata
```

### 5.2 禁止事项

- 不得在带图主回复轮次注入完整 ToolRegistry。
- 不得把图片 base64 放入 Dream Prompt、普通文本 prompt 或日志。
- 不得因为 vision memory 保存失败阻塞用户可见回复。

### 5.3 后续增强

P2 可以做“只读记忆预注入”：

```text
image/text input
  -> Runtime 根据用户文本检索 memory
  -> memory snippets 注入 Vision prompt
  -> Vision no-tools 回复
```

P3 才考虑自定义 Vision Strategy：

```text
vision understand no-tools
  -> optional whitelisted read-only tools
  -> final reply no-tools
```

## 6. 记忆与状态规范

### 6.1 记忆写入路径

允许三类写入：

```text
tool:update_state       模型主动工具写入
post_turn:fallback      后置兜底写入
reflection:vision       图片事件写入
```

规则：

- 记忆内容必须是可检索事实，不是对话摘要。
- 每 turn 写入数量必须有上限。
- 用户纠正记忆时，新记忆必须能覆盖旧记忆或标记旧记忆过期。
- 所有记忆必须可查看、删除、导出。

### 6.2 情绪/关系更新

优先级：

```text
1. update_state 工具结果
2. Post-turn 轻量 parser 兜底
3. 无变化
```

规则：

- 情绪/关系变化不能只存在内存中，完成回复后必须持久化。
- 低置信度变化只影响 Presence，不写长期状态。
- 关系变化幅度必须有 clamp，不能让单轮模型输出造成大幅跳变。

## 7. 远端 Agent Server 接入规范

远端 Server 不应一次性替换本地 `CompanionRuntime`。第一阶段只承接远端工具任务。

### 7.1 最小事件协议

Android 只需要理解统一事件：

```text
text_delta
tool_call_updated
confirmation_requested
browser_snapshot
task_scheduled
task_updated
complete
error
```

### 7.2 Server 责任

- Agent Orchestrator。
- MCP Gateway。
- Browser Worker。
- Cloud Memory。
- Task Scheduler。
- Audit log。
- Risk policy。

### 7.3 Android 责任

- 展示流式文本。
- 展示工具进度。
- 展示确认卡片。
- 用户批准/拒绝。
- 本地通知和权限。
- 本地数据透明面板。

### 7.4 首个 Server MVP

只做：

```text
Android -> Server SSE 文本流
Server -> 一个只读 remote search/browser-read tool
Android -> tool progress 展示
```

不要第一版就做：

- 登录态浏览器。
- 自动购买/提交/发送。
- 云端记忆同步。
- 长任务恢复。

## 8. UX 规范

### 8.1 首字体验

- 云端请求开始后必须立即显示 assistant 占位。
- 超过 1.5s 未收到首字，显示动态安抚文案。
- 超过 8s 未收到首字，显示“仍在思考/可重试”的非阻塞提示。

### 8.2 部分回复

- 网络中断时保留已有部分回复。
- 标记“回复未完整完成”。
- 允许用户重试本轮。

### 8.3 工具可见性

工具状态必须用用户能理解的动词展示：

```text
正在查找记忆
已创建提醒
正在读取网页
需要你确认
工具失败，可继续聊天
```

不得直接暴露 raw tool name 作为主要 UI 文案。

### 8.4 确认体验

确认卡片必须包含：

- 要执行什么。
- 为什么需要。
- 会影响哪里。
- 风险等级。
- 批准/拒绝。
- 可选修改说明。

## 9. 日志与观测规范

每个云端 turn 至少记录：

```text
turn_id
session_id
provider
model
has_image
tool_policy
prompt_context_counts
first_token_latency_ms
total_duration_ms
tool_call_count
tool_round_count
finish_reason
error_type
```

隐私规则：

- 不记录完整用户文本。
- 不记录图片 base64。
- 不记录 API key、MCP token、第三方 token。
- 日志中内容预览必须长度限制并脱敏。

## 10. 测试规范

### 10.1 单元测试

必须覆盖：

- Provider 配置解析。
- Base URL 用户设置是否生效。
- Tool policy 分类。
- 重复工具调用检测。
- Vision no-tools。
- Post-turn fallback。
- 工具失败 final reply。

### 10.2 集成测试

必须覆盖：

- 云端纯聊天流式。
- 云端工具调用成功。
- 工具调用失败。
- Vision 回复。
- MCP server 不可达时不阻塞主回复。

### 10.3 真机验证

每次改云端主链路，至少真机验证：

```text
纯文本聊天
创建提醒
搜索记忆
发送图片
断网/超时恢复
切 provider 后连通性检查
```

## 11. 近期实施顺序

### P0: 配置可信度

- 修正 Base URL 保存后不参与 `LlmConfig` 的问题。
- Settings 展示实际生效 provider / model / baseUrl。
- 连通性检查使用实际生效配置。

### P1: Tool Policy

- 新增工具分类和风险等级。
- 为云端 turn 输出 tool policy。
- 加重复工具调用检测和最大工具轮数。
- 工具失败后强制 final no-tools 回复。

### P2: Post-turn 兜底

- `update_state` 未调用时做轻量记忆/情绪/关系兜底。
- 兜底写入必须可测试、可关闭、可观察。

### P3: Vision 稳定增强

- 保持 Vision no-tools。
- 增加只读记忆预注入。
- 增加 Vision 相关日志字段。

### P4: Remote Tool MVP

- 设计 `RemoteAgentEvent` 到 `AgentEvent` 映射。
- 实现只读远端工具 SSE demo。
- UI 展示远端 tool progress。

## 12. 调研依据

- Koog 当前文档确认：AIAgent、ToolRegistry、EventHandler、MCP provider 都适合 Kotlin/Android 与 Server 共用。
- Anthropic Messages streaming 支持 SSE 增量文本、tool use、thinking delta，项目自研兼容 client 的方向合理。
- Kimi tool_calls 文档确认：工具调用由应用执行并回传结果，模型可能连续/重复调用工具，应用层必须防循环。
- MCP 2025-11-25 规范确认：tools 是 model-controlled，但客户端/宿主可自由设计交互、授权、确认和展示模式。
- Z.AI/智谱文档确认：GLM Anthropic 兼容端点适合作为当前云端主链路，GLM 系列强调 agentic engineering、长程任务和工具协调。

## 13. 2026-07-08 Android 侧落地状态

已完成并提交：

- 配置可信度、Provider 能力、TurnPolicy、ToolPolicy、Registry policy filtering。
- Koog 工具循环收束、工具失败后最终文本兜底、MCP 工具预热缓存。
- Post-turn 低置信度记忆兜底、MemorySources 常量化。
- Vision 最近图像记忆只读注入、发图状态与日志、runtime turnId observability。
- 工具状态文案补齐、写入/高风险工具确认 policy。
- AgentEvent 扩展、RemoteAgentEvent DTO 映射、RemoteAgentService HTTP MVP、只读远端工具端点、Browser Worker read/write 边界。
- 长任务核心模型、Home 长任务入口、云端记忆同步设计文档。

对应提交：

- `c7822be feat(agent): add cloud agent policy foundation`
- `f8ad761 feat(agent): harden tool loop and memory fallback`
- `1d96e62 feat(agent): improve vision context observability`
- `1dea142 feat(agent): add remote agent boundary`
- `70968c2 feat(agent): add long task surface`

尚未完成：

- 真机 checklist。
- 真实远端 Agent Server、SSE/WebSocket 流式传输、`RemoteAgentRuntime` 主链路切换。
- Browser Worker 实际执行器与用户确认 UI。
- 云端 memory sync 的 Room cursor/tombstone 表、隐私设置和同步服务实现。
