# Aura Roadmap

> 最后核对：2026-06-14
>
> 本文档用于跟踪当前实现进度，并把 `README.md` / `docs/architecture.md` 中的产品愿景拆成可执行里程碑。

## 当前状态

项目当前处于 **文本聊天技术闭环 / Phase 1 agent tools** 阶段，并已进入 Phase 2+ 的 Presence Layer / 本地 LLM / Reminder 系统雏形。

同时已经开始规划 Phase 2+ 的端云协同智能体能力：Android 端继续承担亲密交互、本地状态和用户授权边界；远程 Agent Server 承担 MCP、浏览器工具、长期任务、云端记忆和 Skills 编排。详细方案见 `docs/plan/agent-capability-server-plan.md`，Vision 与 Agent tools 协同策略见 `docs/plan/vision-tools-plan.md`。产品表现层也开始转向 Presence Layer 思路：借鉴 Looi 一类陪伴设备的状态动画，但目标不是玩具化机器人，而是把 Aura 的情绪、关系、思考、工具调用和主动关怀变成可感知的细腻行为。

**2026-06-15 叙事主轴更新**：Aura 的产品定位从"AI 陪伴 App"调整为"**第二大脑 / 数字孪生**"——一个长期认识你的 AI。云端对话体（Responsive Mind）负责"对外办事"，本地陪伴体（Continuous Awareness）负责"对内懂事"。详细方案见 `docs/plan/dual-mind-architecture.md` §1.4 与 `docs/plan/insight-driven-product.md`。M2-M5 的 KPI 已按本叙事调整（见各里程碑详情）。

已验证命令：

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

以上命令均已在 2026-06-14 验证通过（`testDebugUnitTest` 41 个测试全绿）。

## 已实现

- 单模块 Android App：`:app`。
- Compose 聊天页，链路为 `MainActivity` -> `ChatScreen` -> `ChatViewModel`。
- `CompanionRuntime` 主流程：Prompt 构建、记忆注入、Koog 执行、输出解析、情绪更新、关系更新。
- Koog `AIAgent` 真实集成，支持流式文本事件。
- Anthropic Messages 兼容 LLM client，支持 SSE streaming、tool schema 序列化、底层图片 content 组装。
- 本地 Qwen / MNN 链路：`core/local/*`（`LocalQwenEngine` / `MnnLocalQwenEngine` / `NativeMnnLlmBridge` / `LocalQwenModelDownloader` / `LocalQwenModelCatalog` / `LocalQwenModelLocator`），含 ModelScope 下载与 MNN 推理桥。
- Room 持久化：messages、memories、memory_summaries、agent_state、mood_snapshots、tool_calls、reminders。
- DataStore 配置仓库：API key、provider、model name、theme mode。
- Agent tools：只读上下文工具、`search_memory`、`search_records`、`search_summaries`、时间/设备/天气/提醒与远程 MCP 工具；记忆、情绪、关系写入改为回复完成后的系统阶段。
- App 启动时恢复 Room 中的聊天历史。
- 聊天页顶部展示当前情绪和关系状态。
- 情绪/关系状态写入 `agent_state`，App 重启后可恢复。
- 聊天页展示最近的长期记忆，只读可见。
- 记忆房间：完整页面 `MemoryRoomScreen`，可浏览全部记忆。
- 聊天页显示模型配置状态；缺少 API Key/Base URL/model 时会禁用发送并给出明确提示。
- 聊天页提供模型设置弹层，可编辑 Provider、模型名称和本机 API Key。
- 多页导航：`androidx.navigation.compose.NavHost`，5 条路由（Home / Chat / Settings / McpSettings / MemoryRoom），设置页与 MCP 设置页已落地。
- 角色主屏 `AuraHomeScreen`：Compose Canvas 绘制的 `AuraPetAvatar` + `PresenceAvatar`，作为应用入口。
- Reminder 模块：`AndroidReminderScheduler`（AlarmManager）+ `ReminderAlarmReceiver` + `ReminderNotificationWorker`（WorkManager OneTime）+ `ReminderNotificationPoster`（NotificationManagerCompat），含 `SCHEDULE_EXACT_ALARM` / `POST_NOTIFICATIONS` 权限。
- Presence 状态控制层：`PresenceController`（mood / relationship / streaming / tool / error → 状态推导）+ `PresenceReactionPolicy`（用户点击、应用回前台、记忆保存等事件 → 反应策略）+ `PresenceModels`，状态已覆盖 idle / listening / thinking / speaking / searching / remembering / happy / sad / tired / error 等。
- 单元测试覆盖 core runtime、prompt、parser、tools、DAO、repository、DataStore、ChatViewModel、消息 UI、Presence 反应策略等。
- Debug APK 构建链路。
- `docs/plan` 规划文档：已新增端云智能体能力整体方案与 Vision/tools 协同计划、Promise System 设计、双轨智能体架构（dual-mind）、Insight 驱动产品方案（第二大脑叙事）。

## 部分实现

- **模型切换**：Repository/Config、聊天页配置状态提示、聊天页内设置弹层、独立设置页与 MCP 设置页已落地；仍缺少模型连通性检查动作。
- **Vision**：`UserInput.Vision` 和 LLM client 图片 content 支持已存在，但 CameraX 预览/拍照/选图 UI 仍未实现（manifest 已声明 `CAMERA` 权限）。
- **情绪与关系**：核心状态更新、持久化恢复、聊天页可视化与 Presence 反应已接入；头像/表情层由 Compose Canvas 临时替代，Rive/Lottie 动画资源尚未接入。
- **记忆**：LLM reflection 后置保存、工具搜索、prompt 注入、聊天页只读展示与 `MemoryRoomScreen` 已实现，但还没有完整编辑管理能力（删除 / 置顶 / 归档仍缺）。
- **Release 构建**：ProGuard 与 debug 签名 fallback 已有，真实 release keystore 仍需验证。
- **端云智能体能力**：总体方案已整理到 `docs/plan/agent-capability-server-plan.md`，但 Android 远程 runtime、Agent Server、MCP Gateway、Browser Worker、云端记忆、长期任务和 Skills 系统尚未实现。
- **Presence Layer**：状态控制器与反应策略已落地；动画资源（Rive / Lottie 状态机）、触摸互动、回归反应动画、连续动作编排尚未实现。
- **Pulse / 主动陪伴**：Reminder 通知链路已实现，但 WorkManager **PulseWorker**（离线衰减 / 回归反应 / 主动通知调度）尚未实现；权限框架已声明 `POST_NOTIFICATIONS` / `RECORD_AUDIO` / `CAMERA`。

## 尚未实现

- CameraX 预览、拍照、图库选择流程。
- Android 运行时权限 UX（权限已声明，运行时申请 UX 缺）。
- `SpeechRecognizer` / `TextToSpeech` 语音输入输出（`RECORD_AUDIO` 已声明）。
- WorkManager Pulse worker：离线衰减、回归反应、主动通知调度（目前仅 Reminder 用 OneTimeWorkRequest）。
- Rive / Presence Layer 角色状态机、思考/说话/记忆/搜索等状态动画、触摸互动和回归反应动画。
- 隐私、导出、删除数据等用户控制能力。
- Instrumented UI 测试套件和 CI 工作流验证。
- 远程 Agent Server 与 Android `RemoteAgentRuntime`。
- MCP Gateway、Browser Worker、云端长期记忆、长期任务调度和 Skill Registry。
- **Insight 流水线（叙事主轴）**：`insights` 表 / InsightValidator / Pattern Detect / Weekly Insight / 用户反馈回路。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3。
- **陪伴体运行时（叙事主轴）**：`core/presence/runtime/`（Heartbeat / DreamPipeline / ReactiveResponder / CompanionKVCacheStore / MoodDrift）。详见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md) §4.2。

## 里程碑

### M0：稳住当前文本 Demo

目标：让当前聊天 demo 稳定、可运行、可排障。

- 保持 `testDebugUnitTest` 和 `assembleDebug` 通过。
- 补充 `.env` / API key 配置排障说明。
- App 启动时从 Room 恢复聊天历史。（已完成）
- 完善当前聊天页的空状态、错误状态、加载状态。（部分完成）
- 用真实 API 行为确认 GLM/Kimi base URL，并记录默认配置。

### M1：设置与配置 MVP

目标：不重新构建 App 也能配置模型。

- 添加导航框架。（已完成）
- 添加设置页。（`SettingsScreen` + `McpSettingsScreen` 已落地）
- 添加 API key 输入与本地持久化。（已完成）
- 添加 GLM/Kimi provider 与 model selector。（已完成）
- 聊天页设置弹层。（已完成）
- 添加模型连通性检查动作。
- 聊天页配置状态提示。（已完成）
- 补充配置写入与 ViewModel 行为测试。（已完成）

### M2：记忆 + Insight 框架 MVP

> 调整说明（2026-06-15）：M2 不再只是"记忆可查看"，而是为"长期认识你"叙事打基础。落地 Insight 数据模型和最小验证链。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md)。

目标：让 Aura 开始拥有"看见关于你的事"的能力底座。

- 如果 DAO 继续外溢到 runtime/UI，补一个 memory repository facade。
- 添加只读版记忆房间。（`MemoryRoomScreen` 已落地）
- 按需要添加删除、置顶、归档动作。
- 在聊天 UI 中更清楚地展示保存/搜索记忆的过程。
- 补充记忆排序、prompt 注入数量限制、工具结果展示测试。
- **【新增，叙事主轴】** 落地 `insights` 表 / Entity / DAO / Repository。
- **【新增，叙事主轴】** `InsightValidator` 通过单元测试（5+ 边界用例：缺 evidence / 全部 hallucinate / 重复 / 信心度低 / evidence 不存在）。
- **【新增，叙事主轴】** Onboarding 5 问模板上线（最近挂心事 / 未来重要日期 / 称呼 / 关系人 / 作息），写入 Auto Memory `user_patterns.md` + `recurring_topics.md`。
- **【新增，叙事主轴】** 主页 Insight 卡片 UI 落地（占位 / 无真实数据，验证视觉与交互）。
- **【新增，叙事主轴】** 用户可一键删除 / 类别静音 / 查看依据（insight 长按弹层）。
- **【新增，叙事主轴】** 设置页直接展示 `insights` / `mood_snapshots` 当前条数，提供导出 / 删除入口（隐私的"看见感"）。

### M3：情绪 + Insight Pattern MVP

> 调整说明（2026-06-15）：情绪层不再只是"角色表现状态"，而是 Insight Pattern 类的核心数据源。mood trend 同时服务 Presence 和 Insight。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3 + §9。

目标：让 Aura 第一次主动说出"我注意到了一件事"。

- 持久化情绪和关系快照，支持重启恢复。（已完成）
- 在聊天页或独立主页增加紧凑状态/头像展示。（状态持久化、聊天页状态条、`AuraHomeScreen` 头像已完成；Rive/Lottie 动画层待做）
- 将 parsed mood/intensity 映射到可见 UI 状态。（已通过 `PresenceController` 映射）
- 新增 `PresenceController` 雏形，把 mood、relationshipLevel、streaming/tool 状态映射为统一的角色表现状态。（已完成）
- 优先用 Rive 状态机验证 idle、listening、thinking、speaking、happy、sad、tired 等基础状态。（逻辑层已覆盖，动画资源待做）
- App 回到前台时补算时间衰减。
- 补充持久化、衰减、关系阈值变化测试。（`PresenceReactionPolicy` 测试已落地）
- **【新增，叙事主轴】** `patternDetect` prompt 端到端跑通（Dream Loop → InsightValidator → insights 表）。
- **【新增，叙事主轴】** 用户能在主页看到第一条真实 Pattern insight（"你过去 3 周都周日下午情绪偏低"）。
- **【新增，叙事主轴】** mood trend 按周 / 月可视化上线（用 mood_snapshots 现有数据，无需新表）。
- **【新增，叙事主轴】** Insight 与对话体打通：点"和 Aura 聊聊"→ prefill prompt → 进入对话体上下文。

### M4：Vision + Insight 增强

> 调整说明（2026-06-15）：Vision 在新叙事下不是孤立能力，而是"Aura 记得你看见了什么"——图片作为 evidence 进入 memory，进而可被 Pattern 跨模态引用。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §9。

目标：让 Aura 看见的不只是文字，还有你看见的世界。

- 添加 CameraX 预览与拍照。
- 视需要添加图库/图片选择。
- 发送前压缩图片 payload。
- 通过现有 runtime 路径发送 `UserInput.Vision`。
- 权限拒绝时优雅退回纯文本聊天。
- 补充图片大小限制与 prompt 构建测试。
- **【新增，叙事主轴】** 视觉内容进入 memory 表（"你在 2026-06-15 拍了张夕阳"），并能作为 Pattern / Connection insight 的 evidence。
- **【新增，叙事主轴】** Pattern insight 可跨 mood + memory + 图片三种数据源生成。
- **【新增，叙事主轴】** Connection 类 insight 第一版可触发（找到 2 条不相关数据的潜在联系，confidence 必须 < 0.5，宁缺毋滥）。

### M5：Pulse + Insight 主线化

> 调整说明（2026-06-15）：Pulse 不再只是"主动关怀通知"，而是 Weekly Insight + 重要日期 + 外部回写的统一调度。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3.1 + §9。

目标：让 Aura 在你不在时也真的在"为你工作"。

- 添加 WorkManager pulse worker。
- 实现离线情绪衰减和回归反应。
- 添加通知权限流程。
- 添加用户可选择的主动通知。
- 将 idle、sleeping、return reaction、remembering、searching 等 Presence 状态接入 pulse、工具调用和用户回归事件。
- 将工具调用从普通 loading 文案升级为角色行为反馈，例如搜索时观察、保存记忆时收纳、失败时困惑但可恢复。
- 补充 worker 调度与状态更新测试。
- **【新增，叙事主轴】** 每周定时（建议周日 21:00）DreamLoopWorker 自动汇总 → Weekly Insight 推 1 条（不超过 3 条，类别去重）。
- **【新增，叙事主轴】** 高信心度（> 0.8）Reminder 类 insight 走通知；其余默认不通知。
- **【新增，叙事主轴】** 用户反馈回路打通：👍 / 👎 / 文字 → InsightLog 表（用于后续 M6+ 训练 insight 质量）。
- **【新增，叙事主轴】** `AnniversaryScannerWorker` 跑通：每天扫描未来 14 天，命中写入 `DRAFT` 状态 insight 等周报汇总。

### M6：产品化加固

目标：准备更长时间的日常使用。

- 添加隐私控制、数据删除、数据导出。
- 扩展聊天、设置、记忆、权限等 instrumented tests。
- 添加或验证 CI 工作流。
- 验证 release signing 和 shrinker 行为。
- 在隐私预期清晰后再接入崩溃分析。

### M7：端云 Agent 能力

目标：让 Aura 从本地聊天 Agent 演进为可使用外部工具、浏览网页、执行长期任务的端云协同智能体。

- 抽象 `AgentRuntime`，为本地 `CompanionRuntime` 和远程 `RemoteAgentRuntime` 留出切换入口。
- 新增 `RemoteAgentService`，支持 HTTPS + SSE/WebSocket 的远程流式事件。
- 搭建最小 Aura Agent Server，先支持文本输入、流式输出和只读远程工具。
- 接入 MCP Gateway，按用户、场景和风险等级筛选工具。
- 接入 Browser Worker，支持网页打开、搜索、正文提取、截图和总结。
- 在 Android UI 中展示远程工具调用、浏览器结果和确认请求。
- 建立云端 Memory Service，并设计本地/云端记忆同步策略。
- 建立 Task Scheduler，支持提醒、网页监控、pulse、等待用户确认后恢复。
- 增加 Skill Registry，把 prompt 片段、工具白名单、风险策略和场景触发组合为可管理能力。
- 补充远程事件映射、确认流程、任务状态和安全策略测试。

## 近期建议顺序

> 调整说明（2026-06-15）：近期顺序按"第二大脑"叙事重新排序——Insight 框架（数据底座）优先于 Vision/动画等表现层能力。

1. ~~设置页与 API key/model 配置。~~（已完成，缺模型连通性检查）
2. ~~聊天历史恢复。~~（已完成）
3. ~~只读版记忆房间。~~（已完成，缺编辑/归档动作）
4. ~~情绪/关系持久化 + Presence 状态控制器。~~（已完成，缺动画资源）
5. **Insight 框架 MVP（新版优先项）**：`insights` 表 / Validator / Onboarding 5 问 / 主页 Insight 卡片占位 / 一键删除静音。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §9 M2。
6. **dual-mind Phase 0-2**：重命名 + `core/presence/runtime/` 目录 + PoC（Heartbeat + KV-cache + MoodDrift）。详见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md) §11。
7. **Pattern Detect 端到端**：Dream Loop → 第一条真实 Pattern insight。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §9 M3。
8. CameraX 单张图片发送（重排到 M4，依赖 Vision 后可触发 Connection insight）。
9. WorkManager Pulse worker + Weekly Insight：离线衰减、回归反应、Weekly 自动汇总通知。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §9 M5。
10. Presence Layer 动画资源：Rive/Lottie 状态机、触摸互动、回归反应动画。
11. 抽象 `AgentRuntime`，为后续远程 Agent / 外部信息回写预留接入点（远端 agent 收窄为"信息回写到记忆"主轴）。

## 维护规则

- 架构文档可以保留目标形态，但必须明确标注当前实现状态。
- 每当里程碑从 planned 进入 implemented，都同步更新本文档。
- 每个功能里程碑尽量让测试和行为一起落地。
