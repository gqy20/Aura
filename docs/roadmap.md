# Aura Roadmap

> 最后核对：2026-08-09
>
> 本文档用于跟踪当前实现进度，并把 `README.md` / `docs/architecture.md` 中的产品愿景拆成可执行里程碑。

> 2026-07-08 云端核心智能体落地更新：
> 已完成 `docs/plan/cloud-agent-core-guidelines.md` 中 Android 侧 P0-P3 与远端边界 MVP 的 24 项非真机任务，并按 5 项一批提交：
> `c7822be` 配置可信度 / ProviderCapabilities / TurnPolicy / ToolPolicy / Registry policy；
> `f8ad761` 工具循环收束 / 工具失败最终回复 / MCP 预热缓存 / post-turn fallback / memory source 规范化；
> `1d96e62` Vision 只读图像记忆注入 / Vision UX 日志 / turnId observability / 工具文案 / 确认模型；
> `1dea142` AgentEvent 扩展 / RemoteAgentEvent 映射 / RemoteAgentService MVP / 只读远端工具 / Browser Worker 边界；
> `70968c2` 长任务模型 / Home 任务入口 / 云端 memory sync 设计。
> 已跳过真机 checklist；验证命令为多轮定向 `testDebugUnitTest` 与 `make test-fast`。

## 当前状态

项目当前处于 **文本聊天技术闭环 / Phase 1 agent tools** 阶段，并已进入 Phase 2+ 的 Presence Layer / 本地 LLM / Reminder 系统雏形。

同时已经开始规划 Phase 2+ 的端云协同智能体能力：Android 端继续承担亲密交互、本地状态和用户授权边界；远程 Agent Server 承担 MCP、浏览器工具、长期任务、云端记忆和 Skills 编排。云端核心智能体实现规范见 `docs/plan/cloud-agent-core-guidelines.md`，端云整体方案见 `docs/plan/agent-capability-server-plan.md`，Vision 与 Agent tools 协同策略见 `docs/plan/vision-tools-plan.md`。产品表现层也开始转向 Presence Layer 思路：借鉴 Looi 一类陪伴设备的状态动画，但目标不是玩具化机器人，而是把 Aura 的情绪、关系、思考、工具调用和主动关怀变成可感知的细腻行为。

**2026-06-15 叙事主轴更新**：Aura 的产品定位从"AI 陪伴 App"调整为"**第二大脑 / 数字孪生**"——一个长期认识你的 AI。云端对话体（Responsive Mind）负责"对外办事"，本地陪伴体（Continuous Awareness）负责"对内懂事"。详细方案见 `docs/plan/dual-mind-architecture.md` §1.4 与 `docs/plan/insight-driven-product.md`。M2-M5 的 KPI 已按本叙事调整（见各里程碑详情）。

**2026-06-15 M4 部分落地**：vision→memory→dream 闭环已通。`UserInput.Vision` 触发时自动写一条 `type=FACT, source=reflection:vision` 的记忆（含 base64 + mediaType），Dream Loop 7 天窗口里把最近 5 张图的 metadata 注入 `## 视觉证据` section 作为跨模态 evidence（**base64 永远不进 DreamPrompt**——本地 Qwen 纯文本路径，模型看不到图）。M4 余下：CameraX UI、Connection insight 端到端。

已验证命令：

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

以上命令均已在 2026-08-10 验证通过（`testDebugUnitTest` **672 个测试全绿**；含聊天恢复交互、复合 MCP 任务、工具安全重试、Android 工具参数兼容与既有 DAO/Insight/ChatViewModel 测试）。

## 已实现

- 单模块 Android App：`:app`。
- Compose 聊天页，链路为 `MainActivity` -> `ChatScreen` -> `ChatViewModel`。
- `CompanionRuntime` 主流程：Prompt 构建、记忆注入、Koog 执行、输出解析、情绪更新、关系更新。
- Koog `AIAgent` 真实集成，支持流式文本事件。
- 对话体验闭环：停止生成、失败重试、重新生成、编辑后重发、消息/代码复制、离底后新消息提示与回到底部入口。
- Anthropic Messages 兼容 LLM client，支持 SSE streaming、tool schema 序列化、底层图片 content 组装。
- 本地 Qwen / MNN 链路：`core/local/*`（`LocalQwenEngine` / `MnnLocalQwenEngine` / `NativeMnnLlmBridge` / `LocalQwenModelDownloader` / `LocalQwenModelCatalog` / `LocalQwenModelLocator`），含 ModelScope 下载与 MNN 推理桥。
- **`ReactiveCompanion`（dual-mind Phase 0 命名清理 + PR A 对齐）**：原 `LocalQwenAgentWrapper` 重命名；2026-06-17 PR A 后 `runStreaming`/`runEvents`/`runStructured`/`allowTools` 行为契约与云端 `KoogPromptExecutorWrapper` 对齐（不再硬抛 `UnsupportedOperationException`，`runStructured` 走 `Json.decodeFromString` 兜底 `examples[0]`）。
- **`core/presence/runtime/` 目录（dual-mind 觉察面前驱）**：
  - `LocalQwenExecutor`（包装 MNN 引擎 + Request(maxTokens/temperature) + parsePatternDetectOutput）
  - `DreamDataCollector`（7 天 mood/message/memory 聚合 + 简易词频 top 10 + **跨模态 evidence**：`Snapshot.imageMemories` 取最近 5 张图 metadata，render 输出 `## 视觉证据` section；**base64 不进 prompt**）
  - `DreamLoopWorker`（@HiltWorker + @AssistedInject 模式）
  - `DreamLoopScheduler`（**7 档可配置周期** OFF / 15min / 30min / 1h / 3h / 6h(默认) / 12h + **立即跑一次按钮**；`WorkScheduler` 接口 + `WorkManagerScheduler` 实现解耦 WorkManager 静态；Hilt 注入 `@ApplicationScope` CoroutineScope 跑 collector；改档位走 `ExistingPeriodicWorkPolicy.UPDATE`；默认 6h 向后兼容）
  - `BatteryHelper`（API 29+ / sticky broadcast 兜底）
- Room 持久化：messages、memories、memory_summaries、agent_state、mood_snapshots、tool_calls、reminders、**insights**、**health_snapshots**（DB schema **v10**；9→10 加 FTS5 trigram 全文搜索 `message_search_docs` + `message_search_docs_fts`）。
- DataStore 配置仓库：API key（实时写）、provider、model name、theme mode、**onboarding 相关 3 key**（user_patterns_json / recurring_topics_json / onboarding_completed_at）、**模型连通性检查 3 key**（PR-A）、`llm_provider` / `model_name` / `base_url` / `mcp_*`。
- Agent tools：只读上下文工具、`search_memory`、`search_records`、`search_summaries`、时间/设备/天气/健康数据/提醒与远程 MCP 工具；记忆、情绪、关系写入改为回复完成后的系统阶段。
- App 启动时恢复 Room 中的聊天历史。
- 聊天页顶部展示当前情绪和关系状态。
- 情绪/关系状态写入 `agent_state`，App 重启后可恢复。
- 聊天页展示最近的长期记忆，只读可见。
- 记忆房间：完整页面 `MemoryRoomScreen`，可浏览全部记忆 + **长按弹层（PR-A 增）：置顶/取消置顶/归档/取消归档/删除**。
- 聊天页显示模型配置状态；缺少 API Key/Base URL/model 时会禁用发送并给出明确提示。
- 聊天页提供模型设置弹层，可编辑 Provider、模型名称和本机 API Key。
- **模型连通性检查**（PR-A）：`LlmConnectivityChecker` + SettingsScreen/McpSettingsScreen "Test connection" 按钮 + 实时结果展示（OK · 534ms / 鉴权失败 / 不可达）。
- **Save 按钮永久可见**（PR-C + M3 PoC UX 修复）：TopAppBar actions 永久显示；api_key 字段实时保存（不依赖 Save 按钮）。
- 多页导航：`androidx.navigation.compose.NavHost`，6 条路由（Home / Chat / Settings / McpSettings / MemoryRoom / **Onboarding**）。
- 角色主屏 `AuraHomeScreen`：Compose Canvas 绘制的 `AuraPetAvatar` + `PresenceAvatar` + **LazyColumn 集成（M2/M3 改）**。
- **Insight 主页卡片（M2）**：`InsightCard` / `InsightCardList` / `InsightLongPressDialog`（4 动作：本周不再说 X 类/知道了/查看依据/和 Aura 聊聊）；接入 Validator 守门。
- **MoodTrendChart Canvas（M3）**：4 根周柱状图（高/中/低 3 档配色），按周聚合近 28 天 mood_snapshots。
- **Insight "和 Aura 聊聊" prefill 路由（M3）**：`pendingPrefill` 字段 + `consumePrefillPrompt` + ChatScreen `LaunchedEffect(pendingPrefill)` 消费。
- Reminder 模块：`AndroidReminderScheduler`（AlarmManager）+ `ReminderAlarmReceiver` + `ReminderNotificationWorker`（WorkManager OneTime）+ `ReminderNotificationPoster`（NotificationManagerCompat），含 `SCHEDULE_EXACT_ALARM` / `POST_NOTIFICATIONS` 权限。
- **M4 Vision→Memory 闭环**：`MemoryEntity` 升级到 v8（`MIGRATION_7_8` 新增 `imageBase64 TEXT + imageMediaType TEXT DEFAULT 'image/jpeg'` 两列）；`MemoryDao.getRecentImages/observeImages` 按 `imageBase64 IS NOT NULL` 过滤；`MemoryRepository.saveVisionMemory` 把"用户发图"事件作为 FACT 记忆写入；`SendMessageUseCase` 注入 `MemoryRepository`，发送带图消息时 fire-and-forget 调 `saveVisionMemory`（失败仅 log 不阻塞主流程）；`DreamDataCollector.collectLast7Days` 拉最近 5 张图 metadata（**不含 base64**）进 `Snapshot.imageMemories` 注入 DreamPrompt `## 视觉证据` section。
- Presence 状态控制层：`PresenceController`（mood / relationship / streaming / tool / error → 状态推导）+ `PresenceReactionPolicy`（用户点击、应用回前台、记忆保存等事件 → 反应策略）+ `PresenceModels`，状态已覆盖 idle / listening / thinking / speaking / searching / remembering / happy / sad / tired / error 等。
- **Dream Loop 周期可配置 + 立即触发**：7 档周期（OFF / 15min / 30min / 1h / 3h / 6h(默认) / 12h）经 `AppPreferences.dreamLoopInterval` 暴露给 Settings UI；`DreamLoopScheduler` 注入 `@ApplicationScope` CoroutineScope 跑长生命周期 collector，改档位走 `ExistingPeriodicWorkPolicy.UPDATE` 立即生效；`WorkScheduler` 接口 + `WorkManagerScheduler` 实现解耦 WorkManager 静态；`triggerNow()` 走 OneTimeWorkRequest 唯一名 `"dream_loop_now"`，适合用户主动验证或调参；选 15min/30min 时 UI 显示耗电警告文案。默认 6h 向后兼容历史行为。
- **Onboarding 5 问（M2 收尾）**：plan §5.2 种子期问题（挂心事/重要日期/称呼/关系人/作息）— 全部可选可跳过，模板表单不入 LLM。
- **隐私"看见感"面板（M2 收尾）**：`DataTransparencySection`（设置页条数 + 导出 JSON via 系统 SAF + 3 个清空按钮 + Bipass 二次确认）。
- **Health 多源链雏形**：`HealthSnapshotEntity` + `HealthSnapshotDao` + `HealthConnectDataSource` + `SensorManagerHealthSource` + `HealthSyncManager` + `HealthDataSection` + `QueryHealthDataTool`，支持健康数据同步、展示与工具查询。
- 单元测试覆盖：634 单测全绿（core runtime、prompt、parser、tools、DAO、repository、DataStore、ChatViewModel、消息 UI、Presence 反应策略、InsightValidator、LocalQwenExecutor、DreamDataCollector、MemoryRepository、SendMessageUseCase、MCP 路由/工具适配、Android 裸字符串参数修复与 POST_CHAT 触发链路等）。
- Debug APK 构建链路。
- `docs/plan` 当前保留端云智能体能力整体方案、Vision/tools 协同计划、双轨智能体架构（dual-mind）、Insight 驱动产品方案（第二大脑叙事）；已完成/阶段性过期方案归档到 `docs/archive/plan`。

### 近期打磨（2026-06-15 → 06-17，commit `1b826d1` 之后）

无新功能模块上线（PR A + PR B 是对既有 2-way 分支的契约对齐 + vision 路径补齐，非产品功能新增）：

- **注释规范文档化**（`4bc1f4b`）：AGENTS.md / CLAUDE.md 新增"注释规范"章节（不写什么 / 压到什么 / 保留什么）
- **注释清理**（`5d1920b` 等）：A 类删 19 处（步骤编号、字面复述、考古注释、防御性解释），B 类合并（章节标题砍半、`when` 分支标签精简）
- **日志体系 P0-P1 补缺**（`1df684e`）：隐私字段脱敏 + 关键 catch 兜底打点 + `Insight/Feature` tag 覆盖
- **设计 token 抽取**（`e1b9f8c` / `f6f599b`）：`ChatColors` / `ChatStatusColors` / `ChatCardSurface` 统一 8 个 Composable 重复的 `Surface` 模板
- **envelope 协议**（`5203538`）：工具调用 envelope + 错误结构化 + ToolCall 详情面板
- **中文 i18n**（`fe7b1df`）：settings + chat UI 文案本地化
- **UI 打磨**（`68986c9` / `5f74e18` / `7bca3cf` / `2b6b458` / `0708baa`）：对齐优化 / 文案统一 / tool chip 动态文案 / 主页 Insight 短按弹层 / 颜色编码主信号
- **DB 优化**（`1c639f0`）：拆 `MessageDao` → `MessageSearchDao`，删 LIKE fallback，加 androidTest 真 FTS5 验证
- **PR A：2-way 分支契约对齐**（2026-06-17）：`ReactiveCompanion` 与 `KoogPromptExecutorWrapper` 接口 4 方法契约对齐 — `runStreaming` 走 `runEvents` 单一入口；`runStructured` 改 `Json.decodeFromString` 解析（不再硬抛）；`toLocalRequest` 读 `BuiltPrompt.allowTools` 并打 warn 日志；新增 `StructuredLocalParser` 做 JSON block 提取 + 围栏剥离 + 兜底示例。`ReactiveCompanionTest` 4 → 8 用例。
- **PR B：本地 vision 支持**（2026-06-17）：`LocalQwenRequest` 加 `imageBase64/imageMediaType` 字段；`MnnLlmBridge` 加 `generateWithImage(imageBytes, mediaType)`；`NativeMnnLlmBridge` + JNI 加 `submitWithImageNative`；`aura_mnn_llm_jni.cpp` 用 stb_image 解码 JPEG/PNG → MNN `ImageProcess` 转 448x448 float tensor → 构造 `PromptImagePart` + `MultimodalPrompt` → 调 `llm->response(multimodal_prompt, ...)`。`CMakeLists.txt` 加 stb_image include 路径。ReactiveCompanionTest 新增 image 透传用例，FakeBridge/RecordingNativeApi 同步补 method。

### 近期打磨（2026-06-22）

- **Insight 生成率大幅提升**（`c5c3687`）：新增 POST_CHAT 即时洞察触发器（对话结束 3min 后自动分析，最少 2 条消息）；EvidenceResolver 去掉 ASCII 过滤（解决中文关键词搜索失效）+ snapshot 兆底策略；InsightValidator 门槛降低（confidence 0.4→0.2 / evidence reality 25%→10% / heading similarity 65%→85%）；DreamLoopWorker 传真实 sessionId + confidence boost；InsightPrompts 更激进（2-3 个发现 / 大胆推测 / 空数据才输出空数组）；POST_CHAT 卡片 UI（蓝色徽标 + InsightAnalyzingIndicator）；全链路 info 级日志。测试 497 → 561。
- **Lint 修复**（`045e2e7`）：`CurrentLocationProvider` 两处 `MissingPermission` 加 `@SuppressLint`（已有 `hasLocationPermission()` 自定义检查，lint 无法识别）。
- **本地工具开关**（feat `d397db3`）：`local_tools_enabled` DataStore key 控制 LOCAL_QWEN 路径是否注入 `AgentToolRegistry.create()`，走 LocalToolProtocol 软协议（0.8B JSON 质量不稳定 + 多轮推理慢，默认 false）。
- **安抚文案轮播**（`90151ca` / `d618038`）：首字到达前加随时间升级的安抚文案轮播；本地模型加载态走 carousel 而非静态 pill。
- **Onboarding 称谓清理**（`b83b0e2`）：Onboarding 称呼默认值清零 + MessageBubble 字体与工具状态显示。

> 备注：测试用例 372（06-15）→ 483（06-17）→ 497（06-22）→ 561（06-22 Insight 改进）；native 端需真机 NDK 编译验证。

### 近期打磨（2026-08-09）

- **对话恢复体验**：流式阶段支持停止生成；中断/失败消息保留部分内容并提供重试/重新生成；用户消息支持编辑后重发；消息与代码块提供复制入口。
- **滚动与状态反馈**：用户离开底部后不再被流式内容强制拉回，出现“回到最新消息”入口与新消息提示；顶部状态展示思考、工具活动与错误状态；错误从短暂 Snackbar 改为可关闭、可重试的持久卡片。
- **工具协议 Android 兼容**：扁平 JSON 裸字符串修复从正则改为保守逐字符解析，规避 Android ICU `PatternSyntaxException`；MCP 边界再次规范化 Koog lenient primitive，避免中文参数形成非法 JSON。
- **MCP 渐进路由**：补齐 `map tool` / `map search` 显式意图，按请求只加载相关 server 和少量工具。
- **模拟器端到端验收**：GLM 流式回复、记忆写入/召回、提醒权限失败/成功调度/通知触发、高德 `maps_geo`、停止生成、新对话与 Photo Picker Vision 均已跑通。
- **验证**：`assembleDebug` 与 `testDebugUnitTest` 通过，634 测试、0 失败。

### 近期打磨（2026-08-16）

> 背景：`docs/chat-ux-benchmark.md` 对标豆包/千问/ChatGPT/Claude 与 coding agent 的时间线模式，定位出 agent 回复的两处硬闪断、pill 覆盖式叙事与到达驱动出字节奏问题，本批落 P0 修复。

- **止血闪断 #1（工具启动清屏）**：`StreamingReset` 不再把 pre-tool 文本整段擦掉，降级为消息内"意图段"（`ChatMessage.intentText`，弱化样式渲染在正文上方）；多工具连续调用时意图段按轮累积。
- **止血闪断 #2（完成换 key 重淡入）**：完成与 DB 合并全程保持 transient id 作 LazyColumn key，DB 行 id 记录到新字段 `persistedId`（`ChatMessageReconciler` + `SendMessageUseCase.Complete` + 新增 `stabilizePersistedMessages` 在每次 DB 全量刷新时延续 UI id 与瞬态字段，顺带消除了用户消息发送后的 id 切换闪烁）。
- **工具步骤时间线**：`ChatMessage.toolStatus`（单值覆盖）拆分为非工具状态（看图/本地加载/Progress）+ `toolSteps` 有序步骤列表；气泡下方渲染时间线（运行中呼吸圆点 → ✓/✗ + 动词化文案 + ≥1s 耗时），点步骤开详情 Sheet；工具全结束但正文未流出时补"还在想"信号点；`ToolStarted/ToolFinished`（无 callId）与 `ToolCallUpdated`（有 callId）两种事件去重为一格；工具阶段后失败保留意图段+时间线叙事不整条删消息；`RECENT_TOOL_CALL_LIMIT` 3→8 支撑复合调用详情反查。
- **节奏驱动流式**：16ms 到达驱动批量 flush 改为 33ms ticker 出字队列（首包 16 字符立即上屏；积压 ≥96 时每 tick 排空 1/4 加速追平），网络"一坨坨"到达被摊平为匀速流出。
- **流式光标**：`StreamingMessageText` 尾部加呼吸竖条 caret（独立 Composable，不污染 markdown 解析）——暂停的流与完成的流恢复可区分。
- **杂项**：PerformancePill（tok/s）仅 debug 构建可见；用户发送消息与"回到最新"FAB 改 `animateScrollToItem`（流式跟随保持即时滚动）。
- **验证**：`testDebugUnitTest` 680 测试、0 失败（+8：节奏驱动两例、复合工具步骤、意图段、时间线/意图段/空白期 UI 三例、stabilize 三例）；`assembleDebug` 通过。
- **模拟器端到端验收**（2026-08-16）：纯文本流式（安抚轮播→呼吸 caret→完成恢复）与复合工具任务（maps_around_search+save_memory 两步时间线"已找到 1 家麦当劳/已保存"→地图卡片→步骤点击打开详情 Sheet）全链路通过；重启后回复正文可恢复、瞬态 UI（时间线/卡片/perf pill）按设计不持久化。
- **GLM 缺失 content_block_stop 修复**（`a930b5f`）：验收中撞上 GLM 偶发在工具块 `content_block_stop` 前结束 SSE（`Streaming response ended with incomplete tool calls: maps_text_search`，整轮失败）。改为流干净结束时补齐完成 pending 工具（累积参数规范化、空参数按 `{}`、finishReason 缺省 `tool_use`），截断参数与"stop 到达但参数畸形"同路交给 Koog 工具校验兜底；probe 脚本全场景 + 3 个新单测通过。
- **意图段（intentText）真机验证**：用"先闲聊后查图"prompt 诱导模型工具前说话，完整三层结构呈现——浅色意图段（"好嘞好嘞~…我这就帮你找找附近的烘焙店！"）→ 绿勾时间线（"已找到 3 家烘焙店"）→ 正文流式 + 地图卡片，pre-tool 文本不再被 StreamingReset 擦掉。
- 模拟器驱动/二进制拉取的坑（input text 空格切分、Git Bash 重定向损坏二进制、WAL 数周不 checkpoint）已固化到 `AGENTS.md` "ADB 日志与本地数据排查" 第 5 节。

## 部分实现

- **模型切换**：Repository/Config、聊天页配置状态提示、聊天页内设置弹层、独立设置页、MCP 设置页、模型连通性检查均已落地；后续主要是可用性和真实 provider 兼容性打磨。
- **Vision**：`UserInput.Vision` + LLM client 图片 content + **M4 vision memory 闭环已落**（base64 存 `memories.imageBase64`，Dream Loop 跨模态 evidence 注入）。CameraX 预览/拍照 UI 仍未实现（manifest 已声明 `CAMERA` 权限，**当前用 Photo Picker 选图满足 MVP**）。
- **情绪与关系**：核心状态更新、持久化恢复、聊天页可视化与 Presence 反应已接入；头像/表情层由 Compose Canvas 临时替代，Rive/Lottie 动画资源尚未接入。
- **记忆**：LLM reflection 后置保存、工具搜索、prompt 注入、聊天页只读展示、`MemoryRoomScreen`、删除、置顶、归档已实现；完整编辑内容/合并/批量管理仍待补。
- **Release 构建**：ProGuard 与 debug 签名 fallback 已有，真实 release keystore 仍需验证。
- **端云智能体能力**：总体方案已整理到 `docs/plan/agent-capability-server-plan.md`，但 Android 远程 runtime、Agent Server、MCP Gateway、Browser Worker、云端记忆、长期任务和 Skills 系统尚未实现。
- **Presence Layer**：状态控制器与反应策略已落地；动画资源（Rive / Lottie 状态机）、触摸互动、回归反应动画、连续动作编排尚未实现。
- **Pulse / 主动陪伴**：Reminder 通知链路已实现，但 WorkManager **PulseWorker**（离线衰减 / 回归反应 / 主动通知调度）尚未实现；权限框架已声明 `POST_NOTIFICATIONS` / `RECORD_AUDIO` / `CAMERA`。

## 尚未实现

- CameraX 预览、拍照流程；图库选择已由 Photo Picker 覆盖。
- Android 运行时权限 UX（权限已声明，运行时申请 UX 缺）。
- `SpeechRecognizer` / `TextToSpeech` 语音输入输出（`RECORD_AUDIO` 已声明）。
- WorkManager Pulse worker：离线衰减、回归反应、主动通知调度（目前仅 Reminder 用 OneTimeWorkRequest）。
- Rive / Presence Layer 角色状态机、思考/说话/记忆/搜索等状态动画、触摸互动和回归反应动画。
- 更完整的隐私控制（当前已有设置页条数展示、JSON 导出和清空入口；仍缺细粒度策略、权限 UX 和长期产品化说明）。
- Instrumented UI 测试套件和 CI 工作流验证。
- 远程 Agent Server 与 Android `RemoteAgentRuntime`。
- MCP Gateway、Browser Worker、云端长期记忆、长期任务调度和 Skill Registry。
- **Insight 后续流水线（叙事主轴）**：Weekly Insight、InsightLog 用户反馈回路、Connection insight 端到端。`insights` 表 / Validator / Pattern Detect PoC 已落地。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3。
- **陪伴体运行时后续（叙事主轴）**：Heartbeat / ReactiveResponder / CompanionKVCacheStore / MoodDrift。`core/presence/runtime/` 中 DreamLoop 前驱已落地。详见 [`plan/dual-mind-architecture.md`](./plan/dual-mind-architecture.md) §4.2。

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
- 添加模型连通性检查动作。（已完成）
- 聊天页配置状态提示。（已完成）
- 补充配置写入与 ViewModel 行为测试。（已完成）

### M2：记忆 + Insight 框架 MVP

> 调整说明（2026-06-15）：M2 不再只是"记忆可查看"，而是为"长期认识你"叙事打基础。落地 Insight 数据模型和最小验证链。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md)。

目标：让 Aura 开始拥有"看见关于你的事"的能力底座。

- 如果 DAO 继续外溢到 runtime/UI，补一个 memory repository facade。
- 添加只读版记忆房间。（`MemoryRoomScreen` 已落地）
- 按需要添加删除、置顶、归档动作。（已完成）
- 在聊天 UI 中更清楚地展示保存/搜索记忆的过程。
- 补充记忆排序、prompt 注入数量限制、工具结果展示测试。
- **【新增，叙事主轴】** 落地 `insights` 表 / Entity / DAO / Repository。
- **【新增，叙事主轴】** `InsightValidator` 通过单元测试（5+ 边界用例：缺 evidence / 全部 hallucinate / 重复 / 信心度低 / evidence 不存在）。✅（8 边界用例已落，门槛已降低）
- **【新增，叙事主轴】** Onboarding 5 问模板上线（最近挂心事 / 未来重要日期 / 称呼 / 关系人 / 作息），写入 Auto Memory `user_patterns.md` + `recurring_topics.md`。
- **【新增，叙事主轴】** 主页 Insight 卡片 UI 落地（占位 / 无真实数据，验证视觉与交互）。✅（卡片 + POST_CHAT 徽标 + 分析中指示器已落）
- **【新增，叙事主轴】** 用户可一键删除 / 类别静音 / 查看依据（insight 长按弹层）。✅
- **【新增，叙事主轴】** 设置页直接展示 `insights` / `mood_snapshots` 当前条数，提供导出 / 删除入口（隐私的"看见感"）。✅

### M3：情绪 + Insight Pattern MVP

> 调整说明（2026-06-15）：情绪层不再只是"角色表现状态"，而是 Insight Pattern 类的核心数据源。mood trend 同时服务 Presence 和 Insight。
> **M3 已落地**（commits `ba6ebad` / `9fa58ab` / `85cb87c`）。详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3 + §9 + [architecture.md "当前实现状态"](./architecture.md)。

目标：让 Aura 第一次主动说出"我注意到了一件事"。

- 持久化情绪和关系快照，支持重启恢复。✅（已完成）
- 在聊天页或独立主页增加紧凑状态/头像展示。✅（状态持久化、聊天页状态条、`AuraHomeScreen` 头像已完成；Rive/Lottie 动画层待做）
- 将 parsed mood/intensity 映射到可见 UI 状态。✅（已通过 `PresenceController` 映射）
- 新增 `PresenceController` 雏形，把 mood、relationshipLevel、streaming/tool 状态映射为统一的角色表现状态。✅（已完成）
- 优先用 Rive 状态机验证 idle、listening、thinking、speaking、happy、sad、tired 等基础状态。✅（逻辑层已覆盖，动画资源待做）
- App 回到前台时补算时间衰减。
- 补充持久化、衰减、关系阈值变化测试。✅（`PresenceReactionPolicy` 测试已落地）
- **【新增，叙事主轴】** `patternDetect` prompt 端到端跑通（Dream Loop → InsightValidator → insights 表）。✅（pipeline 跑通；**已实机验证**：本地 Qwen 4B 模型生成真实 insight，EvidenceResolver 兆底策略生效，成功保存到 DB）
- **【新增，叙事主轴】** 用户能在主页看到第一条真实 Pattern insight。✅（3 张 hardcoded seed 卡片真机显示；M3 PoC 修复 `9fa58ab` 后 `Validator` 全部 `insight_save_completed` 通过）
- **【新增，叙事主轴】** mood trend 按周 / 月可视化上线。✅（`MoodTrendChart` Compose Canvas 4 根周柱状图；W22/W23/W24 真机渲染与 seed mood_snapshots 数据匹配）
- **【新增，叙事主轴】** Insight 与对话体打通：点"和 Aura 聊聊"→ prefill prompt → 进入对话体上下文。✅（`pendingPrefill` 字段 + `consumePrefillPrompt` + ChatScreen `LaunchedEffect(pendingPrefill)` 消费）

### M4：Vision + Insight 增强

> 调整说明（2026-06-15）：Vision 在新叙事下不是孤立能力，而是"Aura 记得你看见了什么"——图片作为 evidence 进入 memory，进而可被 Pattern 跨模态引用。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §9。
> **M4 部分落地**（commit `1b826d1` + 2026-06-17 PR B）：vision→memory→dream 闭环 + 本地 vision 路径已通。CameraX UI、Connection insight 端到端、Pattern 跨 mood+memory+图片三种数据源生成仍未做。

目标：让 Aura 看见的不只是文字，还有你看见的世界。

- 添加 CameraX 预览与拍照。（未做 — 当前走 Photo Picker 选图）
- 视需要添加图库/图片选择。✅（Photo Picker 已支持）
- 发送前压缩图片 payload。✅（`ChatImageProcessor` JPEG/质量压缩已落）
- 通过现有 runtime 路径发送 `UserInput.Vision`。✅（`SendMessageUseCase` 触发 `UserInput.Vision`）
- 权限拒绝时优雅退回纯文本聊天。（Photo Picker 路径无需运行时权限）
- 补充图片大小限制与 prompt 构建测试。✅（SendMessageUseCaseTest 含 vision input 用例）
- **【新增，叙事主轴】** ✅ 视觉内容进入 memory 表（"你在 2026-06-15 拍了张夕阳"）。`MemoryEntity.imageBase64/imageMediaType` + `MIGRATION_7_8` + `MemoryRepository.saveVisionMemory` + `SendMessageUseCase` 自动落库，全链路 11 个测试覆盖。
- **【新增，叙事主轴】** ✅ DreamDataCollector 把 image memory metadata（**不含 base64**）注入 `## 视觉证据` section，作为 Pattern insight 的跨模态 evidence。
- **【新增，叙事主轴】** ✅ 本地 provider（`LlmProvider.LOCAL_QWEN`）也支持 vision（2026-06-17 PR B）。`LocalQwenRequest.imageBase64/imageMediaType` → `MnnLlmBridge.generateWithImage(imageBytes, mediaType)` → JNI `submitWithImageNative` → stb_image 解码 → MNN `ImageProcess` 448x448 float tensor → `MultimodalPrompt.images["image"]` → `llm->response(multimodal_prompt, ...)`。**真机 NDK 验证待补**（CMake 路径假设 `../mnn/3rd_party/imageHelper`）。
- **【新增，叙事主轴】** Pattern insight 可跨 mood + memory + 图片三种数据源生成。（Dream Prompt section 结构已就绪，待 `patternDetect` 跑通后真机验证）
- **【新增，叙事主轴】** Connection 类 insight 第一版可触发（找到 2 条不相关数据的潜在联系，confidence 必须 < 0.5，宁缺毋滥）。`core/insight/InsightPrompts.connectionDetect` 字面量已定义，M4 复用 `LocalQwenExecutor.execute()` 即可落地。（待 PoC Qwen 模型下载后端到端跑通）
- **MCP Gateway 准备**（plan §8 收窄到"信息回写"主轴）：`AppPreferences` 已加 `mcpProviderId` / `mcpApiKey` 两个 Key + Flow + setter，M4 起可直接对接。

### M5：Pulse + Insight 主线化

> 调整说明（2026-06-15）：Pulse 不再只是"主动关怀通知"，而是 Weekly Insight + 重要日期 + 外部回写的统一调度。
> 详见 [`plan/insight-driven-product.md`](./plan/insight-driven-product.md) §3.1 + §9。

目标：让 Aura 在你不在时也真的在"为你工作"。

- 添加 WorkManager pulse worker（DreamLoop 周期任务已落（6h + 电量约束），剩离线衰减/回归反应/主动通知 Worker 未做）。
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

- 扩展隐私控制、数据删除、数据导出。（基础面板已完成，细粒度策略待补）
- 扩展聊天、设置、记忆、权限等 instrumented tests。
- 持续完善 CI 工作流。（已加入发布元数据、签名密钥、单测、lint 与 release 构建门禁）
- 验证 release signing 和 shrinker 行为。（本地 shrinker 构建已通过；正式签名仍需由 GitHub Secrets 在 tag 工作流中验收）
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

> 调整说明（2026-06-15）：M0-M3 全部落地（commits `1bad958` / `5b77241` / `4ee7758` / `ba6ebad` / `9fa58ab` / `85cb87c`）。下一阶段 M4+ 优先级按"长期认识你"主线推进 — Vision（图片 evidence）→ Pulse/Weekly（持续在场）→ 双轨拆分（云端对话体 / 本地觉察面正式分离）→ 远端 Agent Server（信息回写主轴）。

1. ~~M0 稳住当前文本 Demo。~~（已完成）
2. ~~M1 设置与配置 MVP。~~（已完成，连通性检查 + Save 按钮 + api_key 实时保存全已落）
3. ~~M2 记忆 + Insight 框架 MVP。~~（已完成，`insights` 表 + Validator + Onboarding 5 问 + 主页 Insight 卡片 + 数据透明面板全落）
4. ~~M3 情绪 + Insight Pattern MVP。~~（已完成，DreamLoop pipeline + Mood Trend Chart + Prefill 路由打通；PoC 阶段待 Qwen 模型下载后端到端可跑通）
5. **M3 PoC 完善：用户在真机触发 Qwen 模型下载 → DreamLoop 跑出第一条 LLM 真实生成的 insight**（已写好 `LocalQwenModelDownloader` UI 入口，仅需用户操作 + 等待下载完成）。
6. **M4 Vision + Insight 增强**：**vision→memory→dream 闭环已落（commit `1b826d1`）+ 本地 vision 路径已落（PR B）**；余下 CameraX UI、Connection insight 端到端、Pattern 跨 mood+memory+图片三种数据源生成验证。
7. **M5 Pulse + Weekly Insight**：离线衰减 / 回归反应 Worker、AnniversaryScanner 周期任务、Weekly Insight 自动汇总 + 通知、InsightLog 用户反馈回路（👍/👎/文字 → 训练样本）。本地 vision 已就绪后 Pulse Worker 可复用 vision 元数据驱动"记得你拍过的照片"型通知。
8. ~~**dual-mind Phase 1**：拆云端对话体 / 本地觉察面。~~ **已重新评估（2026-06-17）**：经核实，`KoogAgentFactoryImpl` 的 2-way 分支（云端 / 本地）已是合理的 Provider 切换形态；本地的"觉察面"职责由 `core/presence/runtime/` 下的 `DreamLoopWorker` / `LocalQwenExecutor` 等独立组件承担，不在 `KoogAgentWrapper` 主路径里。**不再需要拆 dual-mind Phase 1**，专注把 Pulse Worker（M5）和本地 vision 路径走通即可。AuraMemoryStore 文件系统层属于 M5+ 配套（auto memory PoC 验证后启动）。
9. ~~**抽象 `AgentRuntime`**：~~ **已重新评估（2026-06-17）**：经讨论，`KoogAgentWrapper` 4 方法契约已能覆盖云端 / 本地两端（PR A 对齐后行为契约一致）。**不引入新接口层**；M7 远端 Agent Server 落地时如需切换入口，可在 `CompanionRuntime` 上做小范围重构，不应预先抽 `AgentRuntime` 接口。
10. **M6 产品化加固**：扩展 instrumented tests（CameraX / WorkManager 真实场景）；GitHub Release 已具备版本一致性、签名密钥、单测、lint 与构建门禁，正式签名待 tag 发布验收。
11. **云端核心智能体规范落地**：按 `docs/plan/cloud-agent-core-guidelines.md` 先做 P0-P3（配置可信度、Tool Policy、Post-turn 兜底、Vision 稳定增强），再启动远端工具 MVP。
12. **M7 远端 Agent Server**（plan §8 收窄到"信息回写"主轴）：最小 Aura Agent Server 先支持文本输入 / 流式输出 / 只读远程工具；`AppPreferences` 已加 `mcpProviderId` / `mcpApiKey` 字段，M4 起可对接。
