# 更新日志

项目使用带 `v` 前缀的语义化版本标签，格式参考 [Keep a Changelog](https://keepachangelog.com/)。

## [0.1.5] - 2026-06-22

距 0.1.4 (2026-06-16) 共 67 个提交。核心主题：Insight 生成率大幅提升 + 多会话支持 + 工具系统双开关 + ToolScope 分层 + 本地模型性能优化 + MCP Auth Token + 网站场景化重构 + MiSans 字体内嵌 + Benchmark 体系。

### 新增

**Insight 生成链路增强**
- Insight 生成率大幅提升（commit `c5c3687`）：DreamLoopWorker 6h 周期 + ChatViewModel POST_CHAT 3min 冷却即时触发，双路径并行生成。
- `EvidenceResolver` 后置证据解析（commit `6e2924f`）：本地小模型无法输出有效 evidence_ids 时，通过去 ASCII 过滤 + snapshot 兜底策略后置补全，中文 FTS5 兼容。
- `InsightValidator` 低门槛调优：MIN_CONFIDENCE=0.2、EVIDENCE_REALITY_THRESHOLD=10%、HEADING_SIMILARITY=85%。
- POST_CHAT 卡片 UI：蓝色 AutoAwesome 徽标 + InsightAnalyzingIndicator 动画，分析完成后自动展示。

**多会话支持**
- 新增多会话管理（commit `3264065`），支持动态 sessionId 切换，聊天历史可按会话独立浏览。

**工具系统双开关 + ToolScope 分层**（commit `827a411` / `155a87c`）
- `mcpEnabled` MCP 总开关 + `systemToolsEnabled` 系统内置工具开关，`CompanionToolRegistry.create()` 按开关短路。
- `ToolScope` 分层（MCP / LOCAL / SYSTEM）+ 参数 Schema 注入到 prompt，让 LLM 感知工具参数类型。
- 记忆主动性 L1：Settings 开关控制本地模型是否触发工具调用。

**本地模型性能优化**
- 应用启动时预加载 MNN 模型（commit `b3c934e`），减少首次聊天等待。
- Dummy inference warmup（commit `34fbdc8`），预热推理路径。
- Batch streaming render（commit `34fbdc8`），批量渲染 token 减少 UI 刷新频率。
- Cap max_new_tokens（commit `3f105fc`），控制本地生成上限。
- System prompt 拆分利用 MNN prefix cache（commit `e67b387`），提升多轮推理速度。
- 模型下载器重写（commit `f027fac`）：支持断点续传 + 强制重新下载。

**聊天 UI 陪伴感增强**
- 首字到达前安抚文案轮播（commit `90151ca` / `d618038`），随时间升级文案，本地模型加载态走 carousel。
- PerformancePill 性能指示器：assistant 消息下方显示 tok/s + 耗时。
- 消息字体升级 bodyMedium（commit `f48e005`），流式去掉拼接省略号。
- 切换历史对话无响应修复（commit `853eae4`）：主动清空 + 放宽 streaming 保护 + isCurrent 短路。

**日志体系完善**（commit `8fade43`）
- PII 修复 + release 短路 + 本地 ring buffer，诊断日志更安全。

**MCP 改进**
- Auth Token 输入框（commit `2e3710c`），支持自定义鉴权头。
- 删除 MCP 编辑页接入地址只读展示块（commit `2a5e734`）。
- Preserve disabled state（commit `75600e2`），修复禁用状态丢失问题。

**.env 配置注入**（commit `397d5e0` / `1bddd53`）
- Debug 构建支持从 `.env` 注入 API Key、Base URL 等配置，支持 per-provider API key。

**MiSans 字体内嵌**（commit `565d322`）
- 内嵌 MiSans 字体替换系统 SansSerif，统一视觉体验。

**Benchmark 体系**
- 设备自适应 benchmark flow（commit `2af2c57`），后端调优 + 结果文档（commit `e3b88e4`）。
- 切换到主进程 APK flow（commit `d606bec`），不走 androidTest。

**Prompt 优化**
- 回复正文去掉结构化标签，情绪改走 update_state（commit `3f3c5f0`）。
- Persona 去重 + output format 注入 + identity 收紧（commit `e4d2bac`）。
- 设备定位上下文注入 system prompt（commit `7657ce4`）。

**网站场景化重构**
- 主页围绕三个真实生活场景重构（commit `406d8f8`）。
- 6 个场景手机丰富场景化差异内容（commit `538aab5` / `e7da4bf`）。
- Hero 8 张浮动卡片 + 场景化手机内容（commit `ae1c9b9`）。
- 子页面动画增强 + Tech 3D Hero（commit `3437dff`）。
- 设计 token 统一（commit `1a4ea6e` / `a2c6f89`），去掉 Lenis 改原生 CSS snap。

### 变更

- Launcher 月牙图标放大并左偏修正视觉重心（commit `65e125a`）。
- Onboarding 称呼默认值清零 + MessageBubble 字体与工具状态显示优化（commit `b83b0e2`）。
- 主动请求定位 + 天气查询体验优化（commit `2e3710c`）。
- 关系模型实现内联重构（commit `ff7606e`）。
- 从正则标签迁移到 Agent Tool 主动调用（commit `b1c32d0`）。
- 统一本地/云端工具结果回灌策略（commit `dcdeabc`）。
- 测试并行 + Makefile test-fast/test-db + .env UTF-8 修复（commit `b947780`）。
- CI 工作流精简（commit `5ed24fb`）。
- MCP 并行构建 + soc model API guard（commit `75600e2` / `f14712f`）。
- 设置页布局与动效系统重构（commit `0fb0bf7`）。
- Theme tokens + 删除遗留 TopAppBar adapter（commit `dd3e6a5` / `1596e7d`）。

### 修复

- 切换历史对话无响应（commit `853eae4`）。
- 本地模型加载态安抚文案 carousel 替代静态 pill（commit `d618038`）。
- 模型下载 Range 请求 404 时删 partial 从头重试（commit `d9326f6`）。
- MNN runtime max token 配置不生效（commit `c8294e4`）。
- Benchmark threadNum + headless app flow（commit `5056e1a`）。
- Settings 和 detail layouts 稳定性（commit `fd261f1`）。
- 本地模型加载状态歧义（commit `adcb8ca`）。
- Lint MissingPermission 手动抑制（commit `045e2e7`）。

### 工具链

- 测试清理：撤回 isReturnDefaultValues + 冗余用例清理（commit `8e43124`）。
- P1 单 fork Robolectric runtime 全局复用 + P2 抽取共享 Fake 类（commit `6c60a9c`），测试性能提升。
- 测试基线：561 个单元测试全绿。
- 创意方案 + 演示视频脚本 + PDF 构建配置（commit `3b07f88`）。

### 文档

- 同步 CLAUDE.md / AGENTS.md 测试相关内容（commit `c19cfc0`）。
- 全面更新 README / AGENTS / CLAUDE / roadmap（commit `e832c86`）。

### 备注

- v0.1.5 是 Insight 生成率从"偶尔触发"到"几乎每次都有"的关键版本。
- 67 个提交覆盖 Insight、多会话、工具分层、本地性能、网站重构五大方向。

---

## [0.1.4] - 2026-06-16

距 0.1.3 (2026-05-25) 共 100 个提交。核心主题：M2 Insight 框架 + M3 Presence 端到端 + M4 Vision→Memory→Dream 闭环 + 本地 LLM 全链路（MNN + ModelScope）+ Health Connect + 项目官网上线 + 品牌体系统一 + UI/UX 全面打磨。

### 新增

**M2 Insight 框架**（commit `5b77241`）
- 新增 `insights` 表 / Entity / DAO / Repository，承载"长期认识你"叙事的数据底座。
- 新增 `InsightValidator`（8 边界单元测试：缺 evidence / 全部 hallucinate / 重复 / 信心度低 / evidence 不存在）。
- 主页 `InsightCard` / `InsightCardList` / `InsightLongPressDialog` 落地，4 动作：本周不再说 X 类 / 知道了 / 查看依据 / 和 Aura 聊聊。
- Onboarding 5 问（最近挂心事 / 未来重要日期 / 称呼 / 关系人 / 作息）—— 全部可选可跳过，模板表单不入 LLM（`4ee7758`）。
- `DataTransparencySection`：设置页条数 + 导出 JSON via 系统 SAF + 3 个清空按钮 + 二次确认。

**M3 端到端**（commit `ba6ebad`）
- `ReactiveCompanion`（dual-mind Phase 0）：原 `LocalQwenAgentWrapper` 重命名，体现"对用户消息的本地觉察响应"语义。
- `core/presence/runtime/` 目录：`LocalQwenExecutor` / `DreamDataCollector` / `DreamLoopWorker` / `DreamLoopScheduler` / `BatteryHelper` 落地。
- `DreamLoopScheduler` 7 档周期（OFF / 15min / 30min / 1h / 3h / 6h(默认) / 12h）+ 立即跑一次按钮；改档位走 `ExistingPeriodicWorkPolicy.UPDATE` 立即生效。
- `MoodTrendChart` Canvas 4 根周柱状图（高/中/低 3 档配色），按周聚合近 28 天 mood_snapshots。
- Insight 与对话体打通："和 Aura 聊聊" → prefill prompt → ChatScreen `LaunchedEffect(pendingPrefill)` 消费。

**M3 PoC 修复**（commit `9fa58ab`）
- `seedDemoInsights` evidence 真实化（不再 hallucinate）。
- Onboarding 5 问改为"5/5 必填，1/5 可空"。

**M4 vision→memory→dream 闭环**（commit `1b826d1`）
- `MemoryEntity` 升级到 v8（`MIGRATION_7_8` 新增 `imageBase64` + `imageMediaType` 两列）。
- `MemoryRepository.saveVisionMemory` 把"用户发图"事件作为 FACT 记忆写入。
- `SendMessageUseCase` 注入 `MemoryRepository`，发送带图消息时 fire-and-forget 调 `saveVisionMemory`（失败仅 log 不阻塞主流程）。
- `DreamDataCollector.collectLast7Days` 拉最近 5 张图 metadata（**不含 base64**）进 `Snapshot.imageMemories` 注入 DreamPrompt `## 视觉证据` section。

**本地 LLM 全链路**
- MNN native runtime 完整链路：`NativeMnnLlmBridge` → `MnnLocalQwenEngine` → `MnnInferenceConfig`（设备自适应推理参数）（`54254b2` / `da8ed2b` / `e50e555`）。
- ModelScope（魔搭）本地 Qwen 模型下载器，端内一键下载安装（`8e445b3`）。
- 本地模型 A+B 共享：云端 provider 与本地 provider 共享同一模型实例，MNN 默认路径自动配置（`94870ef`）。
- 本地工具调用（tool call）与聊天入口对齐，本地模型也能触发 agent tools（`460b058`）。
- 本地模型运行时边界检查 + 诊断信息，未安装模型时阻止进入聊天（`948133f` / `780f4fd`）。
- MNN prompt cache 与结构化 turn 同步，改善多轮本地推理一致性（`d3fd6c7`）。

**Health Connect 集成（M7）**
- Health Connect 多源适配层，统一健康数据读取接口（`9b78c34`）。
- SensorManager 步数源作为兜底，Health Connect 不可用时自动降级（`84a4e1f`）。

**魔搭 ModelScope provider**
- 新增 ModelScope provider（Anthropic Messages 兼容协议），可直接使用 Kimi / GLM 等国产模型（`503425d`）。

**MCP multi-server preset**
- multi-server preset 架构，支持 key-only onboarding（填 key 即用，无需手动配 URL）（`25d2cec`）。

**品牌体系统一**
- Aura logo — crescent ring design 月牙环设计（`fb7a791`）。
- `brand.properties` 作为品牌 single source of truth，`colors.xml` + `BuildConfig` 统一读取（`a8edfc0`）。

**i18n**（commit `fe7b1df`）
- settings + chat UI 中文本地化 + 冗余清理。

### 变更

**UI / 设计 token 收敛**
- `ChatColors` / `ChatStatusColors` 抽取（`e1b9f8c`），统一状态色 + 卡片色映射。
- `ChatCardSurface` 抽取（`f6f599b`），收敛 8 个 Composable 重复的 `Surface` 模板。
- 颜色编码当主信号、文字作辅助（`0708baa`）—— 进一步去文字徽章。
- 主页 Insight 短按弹层 + 关闭按钮统一（`2b6b458`）。
- tool chip 动态文案 + streaming draft 过滤 LLM 控制标签（`7bca3cf`）。

**Save 按钮**（commit `85cb87c`）
- TopAppBar actions 永久显示 Save 按钮；api_key 字段实时保存（不依赖 Save 按钮）。

**envelope 协议**（commit `5203538`）
- 工具调用 envelope + 错误结构化 + ToolCall 详情面板。

**UI 打磨**（commit `68986c9` / `5f74e18`）
- B/C 类对齐优化 + 文案统一。

**Presence animation mapping**
- 新增 Presence animation state mapping，角色状态与动画帧映射（`3997506`）。

### 修复

- 修本地模型"两个未安装"显示（`5d1920b`）：`LocalQwenModelDownloader.status()` 不再填充 message，状态文本完全由 UI 端派生。
- 拆 `MessageDao` → `MessageSearchDao`，删 LIKE fallback，加 androidTest 真 FTS5 验证（`1c639f0`）。
- 稳定本地 Qwen 与后台状态同步（`4a03436`）。
- 修主页 Insight 短按弹层交互（`2b6b458`）。
- 修聊天 WeChat IME 面板卡住（`ccf7f27` / `dd1d8ff`）。
- 修 settings page Save 按钮 + api_key 实时保存（`85cb87c`）。

### 工具链

- 日志体系 P0-P1 补缺（`1df684e`）：隐私字段脱敏 + 关键 catch 兜底打点 + `Insight/Feature` tag 覆盖。
- 注释规范文档化（`4bc1f4b`）：AGENTS.md / CLAUDE.md 新增"注释规范"章节。
- 注释清理（`5d1920b` 等）：A 类删 19 处（步骤编号、字面复述、考古注释、防御性解释），B 类合并（章节标题砍半）。
- CI lint 解锁（`c103384`）：`RestrictedApi` + `PropertyEscape` 修复。

### 文档

- `docs/fallbacks.md` full sweep（`a65bc18`）：sync stale entries + 11 个新 module chapters。
- 归档 6 个过时 plan docs（`b54025e`）。
- 更新 `README.md`（加 logo + 徽章 + 详细化）、`docs/roadmap.md`（最后核对 2026-06-16 + 近期打磨段）。
- 新增创意赛方案 + LifeScore 设计（`d7f3a95`）。
- 新增 dual-mind 架构方案（`dfb0c7b`）。
- 重写 agent 编排层文档（M4 vision memory 同步）（`f739090`）。

### 项目官网

- 新增项目官网：Next.js 15 + Tailwind v4 + monorepo（`web/`）（`835cb30`）。
- Hero 3D 主视觉 + 滚动叙事 + 磁性光标（`fdb7fe7` / `0f19022`）。
- 三个特性深页（/presence, /memory, /agent）含 3D + stats 立体结构（`2b89d80` / `d0fdab0`）。
- T2 节奏感：3 特性页背景渐变 + GSAP timeline 点亮 + reaction 强度条（`96e4d6f`）。
- 站点文案全量中文化（`c89afe7`）。
- Vercel 部署配置 + 多轮修复（`59dd743` / `9c3f53c` / `522ebf1` / `e12ed22` / `de74a89` / `96ded35`）。

### 备注

- M2-M4 已在代码里完整跑通，0.1.4 是"合入 release"的过程性版本。
- 372 个单元测试通过（commit `1b826d1` 时基线），打磨类工作未新增测试用例。

---

## [0.1.3] - 2026-05-25

Aura 0.1.3 是 0.1.2 之后的稳定性与发布流程增强版本，重点收敛聊天响应链路、后置记忆写入结构和真机排障日志，并让 GitHub Release 能直接产出可下载包。

### 变更

- Release workflow 改为 tag 推送后直接创建正式 GitHub Release，并上传版本化 release APK 与 SHA-256 校验文件。
- 记忆后置 reflection 写入改为结构化结果处理，降低非结构化模型输出导致记忆保存不稳定的风险。
- MCP 初始化上报的 Aura Android client 版本升级到 `0.1.3`。
- Android 应用版本升级为 `versionCode = 4` / `versionName = "0.1.3"`。

### 修复

- 稳定聊天 response pipeline，减少运行时异常、空响应或阶段状态不一致时对 UI 和持久化链路的影响。

### 工具链

- 增强 Koog agent、LLM client、MCP HTTP client、定位、天气、提醒通知、工具调用记录和记忆仓库的诊断日志，便于真机通过 logcat 定位问题。

---

## [0.1.2] - 2026-05-21

Aura 0.1.2 是 0.1.1 之后的体验与可靠性增强版本，覆盖最近对话上下文、文本记忆恢复、提醒管理、MCP 专用设置、宠物头像与聊天 UI 打磨，并补齐模型配置安全化和 prompt 预算控制。

### 新增

**上下文与记忆**
- 新增 `ConversationContextBuilder`，将最近对话历史注入主 prompt；上下文窗口按约 `20K token` 预算保留最近消息，而不是只按固定条数截断。
- 恢复并强化文本消息后置记忆抽取，新增 `TextMemoryExtractor`，支持从用户文本与助手回复中提取应保存的长期记忆。
- 长期记忆 prompt 注入改为约 `10K token` 预算，候选最多取 200 条；超长单条记忆会按剩余预算截断。
- 摘要 prompt 注入改为约 `5K token` 预算，支持超过 2 条摘要进入上下文；超长摘要会截断并保留摘要来源 id。
- 新增最近消息 DAO / repository 查询能力，为运行时上下文、手动场景测试和后续记忆房间联动打基础。

**提醒管理**
- 新增 Room `reminders` 表、`ReminderDao`、`ReminderRepository` 与数据库 schema version 5，用于本地持久化提醒。
- 新增提醒管理 UI，支持在聊天页查看、完成、删除本地提醒。
- 新增精确闹钟权限流程：创建提醒时可检测 `SCHEDULE_EXACT_ALARM` 能力，并在 UI 中引导用户授权。
- 新增 `ReminderAlarmReceiver` 与 `ReminderNotificationPoster`，将提醒触发、通知展示和 WorkManager 兜底拆分得更清晰。

**设置与工具**
- 新增独立 MCP 设置入口，聊天页可单独管理 MCP HTTP URL 与远程工具开关。
- 内置 GLM 与 Kimi 的默认 provider 配置，用户可直接切换配置后填入 API Key。
- GLM 默认模型为 `glm-5v-turbo`，Kimi 默认模型为 `kimi-for-coding`。
- Kimi 默认 Base URL 更新为 `https://api.kimi.com/coding`，GLM 默认 Base URL 使用项目配置中的 GLM 兼容接口。

**聊天体验**
- 新增 `AuraPetAvatar` 组件、宠物 spritesheet 与 manifest 资源，用于主界面的动态宠物头像展示。
- 聊天页增加更完整的状态视图：模型配置、MCP 设置、提醒管理、记忆房间统计和权限提示可以在同一页面内更顺畅地切换。

### 变更

- API Key 不再写入 `BuildConfig`，改为仅通过运行时用户配置/DataStore 使用，避免明文打进 APK。
- 调整 prompt 模板与 persona 规则，使最近对话、长期记忆、摘要记忆和文本记忆抽取的职责更清晰。
- MCP 工具适配器与工具注册表支持更完整的设置项、状态展示和记忆房间统计去重。
- 提醒工具 `create_local_reminder` 增加持久化、精确闹钟可用性检查、日志和错误信息，减少只调度不落库或失败不可见的问题。
- 聊天气泡和主界面视觉细节继续打磨，降低工具状态、记忆统计和设置面板对输入区的干扰。
- MCP 初始化上报的 Aura Android client 版本升级到 `0.1.2`。
- Android 应用版本升级为 `versionCode = 3` / `versionName = "0.1.2"`。

### 修复

- 修复 0.1.1 后文本记忆 prompt 注入和后置文本记忆抽取没有完整恢复的问题。
- 修复模型只返回结构化标签、解析后正文为空时仍被当作成功回复并保存空 assistant 消息的问题；现在会返回 ParseError，避免写入空消息、触发情绪/关系更新或后置记忆抽取。
- 修复提醒通知链路中 Hilt Worker / Receiver 日志不足、失败难定位的问题。
- 修复提醒调度、通知展示和提醒状态更新中的若干边界问题，提升真机调试时的可观测性。
- 修复记忆房间统计重复计数的问题。

### 工具链

- 更新 release 版本号相关 Gradle 配置、MCP client version 和文档示例。
- 新增/更新 `ConversationContextBuilderTest`、`ConversationContextManualScenariosTest`、`TextMemoryExtractorTest`、`CreateLocalReminderToolTest`、`ChatViewModelTest`、`MemoryRepositoryTest` 等单元测试。
- 已验证 `testDebugUnitTest` 与 `assembleDebug`。

---

## [0.1.1] - 2026-05-17

Aura · 奥拉的记忆系统增强版本，重点完善长期记忆基础设施、prompt 记忆选择、摘要记忆接入、Vision 后置记忆和远程工具扩展，为后续记忆房间管理与端云协同打基础。

### 新增

**记忆系统**
- 新增 `MemoryRepository`，统一封装记忆保存、搜索、prompt selection、`lastAccessed` 更新和轻量去重合并。
- 新增 `memory_summaries` 摘要记忆表与 `save_summary` / `search_summaries` 工具，用于保存日、会话、主题、项目和关系弧线摘要。
- `MemoryEntity` 新增 `confidence`、`sourceMessageIds`、`updatedAt`、`expiresAt` 和 `sensitivity` 元数据，为可信度、来源追踪、过期和隐私策略打底。
- prompt 记忆注入从固定 top 8 改为「当前输入相关 + 高重要性 + 少量最近 + 摘要」的混合选择。
- Vision 回复完成后新增后置记忆抽取，用户明确表达“这是/记住/提醒我”等场景时会保存视觉相关记忆。

**工具与上下文**
- 新增远程 MCP HTTP tools 注册能力，可通过设置中的 MCP HTTP URL 动态接入外部工具。
- 新增记录搜索、摘要搜索、天气、本地提醒、设备状态、用户上下文设置等工具能力。
- 工具调用状态继续写入 Room，并可驱动 Presence avatar 的搜索、保存记忆、失败等状态反馈。

**体验**
- Aura home 和聊天页记忆展示扩展到更多长期记忆条目。
- Presence avatar 增加更细的加载、流式说话、工具调用、错误和触摸反馈状态。
- 图片消息会保存 base64 并可从历史消息中恢复显示。

### 变更

- `save_memory` 和 `search_memory` 从直接访问 DAO 改为通过 `MemoryRepository` 执行。
- 记忆搜索从简单工具内逻辑下沉到 DAO/repository，并加入候选集、评分排序和访问时间更新。
- prompt selection 会过滤过期记忆；`private` / `sensitive` 记忆只在当前输入强相关时注入。
- 图片输入仍禁用主 agent tools，但主回复前可注入只读记忆上下文，主回复后再执行后置记忆抽取。
- `MessageRepository.sendMessage` 与 `saveAssistantMessage` 现在返回消息 id，便于记忆来源追踪。
- Android 应用版本升级为 `versionCode = 2` / `versionName = "0.1.1"`。

### 修复

- 修复记忆工具非法类型处理和搜索结果排序不稳定的问题。
- 修复 Vision 普通问句被误保存为记忆的风险，问句类图片识别请求会跳过后置记忆抽取。
- 修复 prompt 注入中重要但已过期或敏感的记忆可能被无关场景带入的问题。

### 工具链

- Room 数据库 schema 升级到 version 4，并新增 `MIGRATION_3_4`。
- 补充 `MemoryRepository`、Vision 后置记忆抽取、DAO 查询、runtime 集成和消息 id 返回相关单元测试。
- 已验证 `testDebugUnitTest` 与 `assembleDebug`。

---

## [0.1.0] - 2026-05-16

Aura · 奥拉的首个 Android 技术预览版本，完成文本聊天闭环、Koog Agent 流式调用、基础本地存储、模型配置、上下文工具和设置页工具控制。这个版本重点验证「可对话、可记忆、可配置、可调用本地工具」的 Phase 1 agent tools 基线。

### 新增

**聊天与 Agent**
- 新增 Compose 聊天主界面，包含顶部状态栏、消息列表、输入栏、图片选择入口、记忆房间入口和设置入口。
- 接入 `CompanionRuntime`、Koog `AIAgent` 和流式事件消费，支持助手消息边生成边展示。
- 新增流式 Markdown 分块渲染，减少长回复过程中整段 Markdown 反复重排。
- Agent 输出支持更新 mood、intensity 和 relationship level，并在主界面展示情绪与关系标签。

**模型配置**
- 新增 GLM / Kimi provider、模型名、API Key 和 Base URL 配置。
- 设置页支持保存当前 LLM 调用配置到 DataStore。
- provider 切换时会同步建议模型名和默认 Base URL；Kimi 默认 Base URL 固定为 `https://api.moonshot.cn/v1`。

**本地工具**
- 新增记忆保存、记忆搜索、情绪更新、关系更新、当前时间、最近互动、设备状态、天气、用户上下文设置和本地提醒等工具能力。
- 设置页新增上下文与工具清单，可查看工具名称、能力说明、权限要求，并控制设备状态、定位、天气、提醒和通知开关。
- 助手消息气泡可展示工具执行状态，工具状态文案统一为中文。

**数据与状态**
- 接入 Room、DataStore 和 Hilt，支持消息、记忆、状态、工具调用记录和用户偏好持久化。
- 聊天输入支持图片附件的准备流程，为后续 CameraX UI 和视觉对话打底。
- 环境上下文工具支持读取设备状态、最近互动、当前时间，以及基于城市或授权位置查询天气。

### 修复

- 修复流式输出中助手消息过早结束或状态丢失的问题。
- 修复工具调用状态显示在输入框上方造成信息噪声的问题，改为只在助手消息气泡内呈现。
- 修复模型配置不完整时缺少明确提示的问题，配置状态会展示 API Key、Base URL 或模型名缺失原因。
- 修复图片准备失败时 UI 状态无法恢复的问题，失败后会清理待发送图片并显示错误提示。
- 修复设置页英文/中文混排较多的问题，工具区和主要配置文案统一为中文。

### 变更

- 主页面顶部状态栏改为更紧凑的布局，突出 Aura、在线状态、情绪和关系标签。
- 设置页从单纯模型配置扩展为「模型配置 + 工具能力控制台」。
- 工具执行提示从全局最新工具提示改为跟随具体助手消息，减少聊天底部视觉干扰。
- 模型配置类偏好统一走 DataStore；Room 保持用于聊天消息、记忆、状态和工具调用等业务数据。

### 工具链

- 新增 GitHub Actions `CI` workflow，对齐本地 pre-commit / pre-push hooks，并运行 lint 与 debug build。
- 修复 Android Gradle Wrapper 在 GitHub Actions Linux runner 上的执行权限。
- 新增签名 Release workflow：校验签名 secrets、构建 release APK、生成版本化 APK 文件名和 SHA-256 校验文件。
- 完整验证 `testDebugUnitTest`、`assembleDebug` 和 `assembleRelease`。
- 保持 Android 36 / AGP 9.2 / Kotlin 2.3.21 / JDK 21 构建链路可用。

### 文档

- 新增并整理 `CHANGLOG.md`，采用与 `ai-pad` 一致的 Keep a Changelog 风格。
- 更新 `AGENTS.md`、`README.md`、`docs/roadmap.md`、`docs/architecture.md` 和 Koog Android 集成文档，记录当前 Phase 1 状态与验证入口。
