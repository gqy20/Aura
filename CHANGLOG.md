# 更新日志

项目使用带 `v` 前缀的语义化版本标签，格式参考 [Keep a Changelog](https://keepachangelog.com/)。

## [Unreleased]

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
