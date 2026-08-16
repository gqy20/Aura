# Android 项目 - 开发环境

## 项目文档

- **[README](README.md)** — 项目概览、当前已实现能力、构建/测试命令。
- **[Roadmap](docs/roadmap.md)** — 当前处于“文本聊天技术闭环 / Phase 1 agent tools”，列出已实现、部分实现、未实现和 M0-M6 里程碑。
- **[技术架构文档](docs/architecture.md)** — 架构目标与当前实现状态，包含技术栈、数据层、Agent Core、Vision/Pulse 规划。
- **[工程化规范](docs/engineering-standards.md)** — CI/CD、测试策略、代码规范、质量门禁。
- **[Koog API 参考](docs/koog-api-reference.md)** — 从 Gradle 缓存 JAR (`javap -p -s`) 提取的 Koog v0.8.0 完整 API 签名，覆盖 Agent/Builder/Strategy/Service/Tool/Pipeline/Prompt&LLM 等 15 个模块，含架构图和类型签名表。
- **[Koog ↔ Android 集成指南](docs/koog-android-integration.md)** — Koog 在 Android 上的集成状态：当前已接入真实 `AIAgent`、流式文本和工具事件；保留线程规则（禁止 runBlocking/KG-750 死锁）、生命周期模式、后续待补项。
- **[Agent 编排层文档](docs/agent-architecture.md)** — Aura 云端 Agent 编排层（Provider 路由、Graph Strategy、Tool 系统、Memory Reflection、流式 UX 节流）的详细技术说明。与 `architecture.md` §5 配合阅读：顶层讲位置，这篇讲实现。

## 当前项目状态

- 当前阶段：**Phase 1（文本聊天闭环 / agent tools）已完成，进入 Phase 2+「长期认识你」主线**——Dream Loop 后台觉察、Insight 卡片、本地 LLM、Vision→Memory 闭环、Health 多源已实质落地。M0–M4 大部分完成；M5 Pulse / M6 产品化 / M7 远端 Agent Server 待做（详见 roadmap）。
- 已实现（按代码核对，2026-08-09）：Compose 聊天页 + `AuraHomeScreen` 角色主屏、`ChatViewModel`、`CompanionRuntime`（Koog `AIAgent` 多轮 tool-use + SSE 流式）、停止/重试/重新生成/编辑后重发/复制/离底新消息提示；`AnthropicMessagesLLMClient`（手写 OkHttp SSE 解析、并行 tool index 累积、Android 裸字符串兼容）；**Room v10**（9→10 新增 FTS5 trigram 全文搜索 `message_search_docs_fts`）/DataStore/Hilt；NavHost 六条路由；`SettingsScreen` + `McpSettingsScreen`；`MemoryRoomScreen`；`OnboardingScreen`；**11 个内置 Agent tools** + **MCP 渐进加载**（按请求选择 server、裁剪 tools、spec 缓存、失败冷却）；工具系统双开关；`PresenceController` + `PresenceReactionPolicy`；Reminder 四件套；本地 LLM 全链路；Dream Loop + POST_CHAT 即时洞察；EvidenceResolver 中文兼容；InsightValidator；Vision→Memory；Health 双源；`InsightCardList` + `MoodTrendChart`；`LlmConnectivityChecker`；`DataTransparencySection`。
- 部分实现：Vision 以 Photo Picker 选图为 MVP，CameraX 拍摄 UI 仍缺；情绪/关系头像由 Compose Canvas 自绘替代，Rive/Lottie 动画资源仍缺；记忆的完整编辑/全文搜索框/批量管理仍缺；`InsightPrompts` 已启用 `patternDetect` + `conversationReflection`（POST_CHAT）两个 prompt，`anniversaryScan` / `connectionDetect` 已写好但无调用入口；`AutoMemoryStore` MVP 阶段 Onboarding 答案只当 String 存、未接 LLM 解析；本地 vision 的 native `aura_mnn_llm.so` 真机 NDK 编译验证待补。
- 尚未实现：`SpeechRecognizer`/`TextToSpeech` 语音 I/O（仅 DataStore 开关、无实现）、`PulseWorker`（离线衰减/回归反应/主动通知；Dream Loop 周期任务已落，Reminder 走 OneTimeWorkRequest）、Weekly Insight / AnniversaryScanner / InsightLog 反馈回路、Rive/Lottie 状态机动画、Instrumented UI 测试、CI 工作流、远程 Agent Server / `RemoteAgentRuntime`。
- 已验证：`./gradlew.bat testDebugUnitTest` 通过（**680 个测试，0 失败**；最近核对 2026-08-16，含聊天恢复交互、agent 回复体验专项（意图段保留/工具步骤时间线/节奏驱动流式/key 稳定）、复合 MCP 任务、工具安全重试、Android 工具参数兼容、Dream Loop、InsightValidator、EvidenceResolver、ReactiveCompanion、FTS5、Health 双源与 ChatViewModel POST_CHAT 触发等用例）。

## Compose UI: Window Insets 速查

> 来源：[Android 官方 - About window insets](https://developer.android.com/develop/ui/compose/system/insets) + [Set up window insets](https://developer.android.com/develop/ui/compose/system/insets-ui)

### 核心概念

**Insets** = 系统 UI（状态栏/导航栏/键盘/刘海）占用的空间信息，用于确保应用内容不被遮挡。Android 15 (SDK 35+) **强制 edge-to-edge**，必须处理 insets。

### 常用 Inset 类型

| 类型 | 含义 | 聊天场景 |
|------|------|---------|
| `WindowInsets.statusBars` | 顶部状态栏 | 确保内容不被状态栏遮挡 |
| `WindowInsets.navigationBars` | 底部/侧边导航栏 | 确保输入框不被导航栏遮挡 |
| `WindowInsets.ime` | 软键盘高度 | **聊天界面核心** — 键盘弹出时推动列表 |
| `WindowInsets.displayCutout` | 刘海/挖孔屏 | 避免内容进入刘海区域 |
| `WindowInsets.safeDrawing` | statusBars + navigationBars + captionBar 并集 | 最常用的"安全区域" |

### Compose Modifier 一览

```kotlin
// Padding 方式（最常用）— 将 inset 值作为内边距
Modifier.safeDrawingPadding()                              // 四周安全区域
Modifier.statusBarsPadding()                               // 仅顶部
Modifier.navigationBarsPadding()                           // 仅底部（导航栏）
Modifier.imePadding()                                      // 键盘高度作为 padding
Modifier.windowInsetsPadding(WindowInsets.systemBars)      // 指定任意 inset 类型

// 尺寸方式 — 将 inset 值设为组件尺寸（常用于 Spacer）
Modifier.windowInsetsTopHeight(WindowInsets.statusBars)    // 顶部 spacer = 状态栏高度
Modifier.windowInsetsBottomHeight(WindowInsets.ime)        // 底部 spacer = 键盘高度

// 消费方式 — 标记 inset 已被处理（不施加 padding，仅标记）
Modifier.consumeWindowInsets(WindowInsets.systemBars)
```

### Inset Consumption（消费机制）

Inset padding modifiers 会自动 **consume（消费）** 已使用的 inset 部分，嵌套的子组件不会重复计算同一块空间：

```
Column(Modifier.imePadding()) {          // 外层消费了 IME inset
    LazyColumn { ... }                   // 内部不再重复 IME padding
    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
    // ↑ 当 IME 打开时此 Spacer 高度 → 0（已被 imePadding 消费）
    //   当 IME 关闭时此 Spacer 高度 → 导航栏高度
}
```

> **关键**: LazyColumn 中最后一个 TextField 上方用 `Spacer + windowInsetsBottomHeight(systemBars)` 而非 `contentPadding`，否则 IME 弹出时可能遮住输入框。

### 聊天界面推荐模式

```kotlin
// ChatScreen — 键盘安全的聊天布局
Column(Modifier.fillMaxSize().imePadding()) {
    // 消息列表 — 占据剩余空间，键盘弹出时自动收缩
    LazyColumn(
        modifier = Modifier.weight(1f),
        reverseLayout = true,           // 最新消息在底部
    ) {
        items(messages) { MessageBubble(it) }
        // 底部留出系统导航栏空间（IME 关闭时生效）
        item {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }

    // 输入栏 — 固定底部，始终可见
    ChatInputBar(
        onSendMessage = { ... },
    )
}
```

### Edge-to-edge 启用

```kotlin
// Activity.onCreate()
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()   // Android 15+ 强制，低版本推荐启用
    setContent { /* ... */ }
}
```

### 与 Compose Phase 的关系

Inset 值在 **composition 之后、layout 之前** 更新。内置 Modifier 已延迟到 layout 阶段读取，确保同帧生效。如直接在 composition 中读 `WindowInsets.asPaddingValues()` 可能有一帧延迟，优先使用上述 Modifier。

---

## 参考文档

- **[开源项目参考](docs/reference-android-ai-projects.md)** — 与本项目技术栈相似的 Android AI 聊天/陪伴开源项目调研（Operit / skydoves-chatgpt-android / gpt_mobile 等），含架构对比和借鉴建议。

## 工具优先级

**信息检索/调研时，工具使用优先级（从高到低）：**
1. **`gh` (GitHub CLI)** — 搜索 issue、PR、release notes、代码
2. **Playwright CLI** (`mcp__playwright__*`) — 浏览器自动化，抓取网页内容
3. MCP 工具 / Web Search — 仅在前两者不可用时使用

> 原因：gh 和 Playwright 更稳定、可控，不依赖第三方 API 配额。

## 工具路径

### ADB_Cli — Android 调试工具集
- **路径**: `D:\tools\ADB_Cli`
- **版本**: ADB 1.0.41
- **用途**: 与 Android 设备/模拟器交互（调试、刷机、安装应用）

## MNN Benchmark 约定

- `scripts/mnn_benchmark.py --mode app` / `make benchmark-mnn` 统一走 **主 App 进程 benchmark**，不走 instrumentation。
- 固定顺序是：
  1. `./gradlew.bat assembleDebug`
  2. 用 `D:\tools\ADB_Cli\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk` 直接安装 APK
  3. 用 `adb shell am start` 触发 `MainActivity` 的 benchmark action
  4. 用 `run-as com.xiaoqi.companion.debug` 从 `files/benchmarks/` 拉回结果
- benchmark 统一配置文件是 `scripts/mnn_benchmark.yml`；`apk_path`、`app_package`、`model_name`、`prompt_len`、`decode_len`、`warmup_runs`、`measure_runs` 优先在这里维护。

### Android Studio — IDE
- **路径**: `D:\tools\Android_Studio`
- **入口**: `D:\tools\Android_Studio\bin\studio64.exe`

### Android_Studio_Cli — 命令行工具
- **路径**: `D:\tools\Android_Studio_Cli`
- **核心内容**: `cmdline-tools/bin` — sdkmanager、avdmanager 等

## 运行时环境

| 项目 | 版本/路径 |
|------|-----------|
| JDK | **21.0.6** (Oracle LTS) |
| Kotlin | **2.3.21**（由 Gradle plugin 管理） |
| AGP | **9.2.0** |
| SDK Root | `C:\Users\gqy17\AppData\Local\Android\Sdk` |
| SDK Platform | android-36 / android-36.1 |
| Build Tools | 37.0.0 / 36.1.0 |
| Git | 2.50.0 |
| Node.js | v22.14.0 |
| Python | 3.12.8 |

> **注意**: `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 需设置为 `C:\Users\gqy17\AppData\Local\Android\Sdk`

## 测试规范

### ViewModel + StateFlow 测试模式（官方推荐）

ViewModel 在 `@Before` 中创建（`runTest` 外），其 `viewModelScope` 的 Dispatcher 必须手动替换：

```kotlin
private val testDispatcher = UnconfinedTestDispatcher()

@Before fun setUp() {
    Dispatchers.setMain(testDispatcher)   // 1. 先设测试 dispatcher
    viewModel = MyViewModel(fakeDep)      // 2. 再创建 ViewModel（viewModelScope 绑定到测试 dispatcher）
}
@After fun tearDown() { Dispatchers.resetMain() }
```

**断言方式：** 优先用 `viewModel.uiState.value` 直接断言当前状态，避免 StateFlow conflation 导致的中间值丢失问题。需要验证状态序列时才用 Turbine `test {}`。

**参考资料：**
- [Android 官方：测试 Kotlin 数据流](https://developer.android.com/kotlin/flow/test)
- [kotlinx.coroutines#3143](https://github.com/Kotlin/kotlinx.coroutines/issues/3143)

### TDD 流程

- Red-Green-Refactor 循环
- DAO 测试：Robolectric + Room inMemory + Turbine
- ViewModel 测试：MockK + runTest + Dispatchers.setMain(UnconfinedTestDispatcher())
- 所有测试通过后才提交

### DAO 测试基类

所有 DAO 测试继承 `BaseDaoTest`（位于 `data/db/BaseDaoTest.kt`），遵循 Google Now in Android 官方模式：

- `@Before` 创建 Room in-memory DB + 调用 `initDaos()` 初始化 DAO
- `@After` 关闭 DB
- 子类只需声明 DAO 字段并实现 `initDaos()`
- **每个测试类创建独立 Room in-memory DB** — `@Before`/`@After` 保证隔离
- **Robolectric RuntimeEnvironment 全进程复用** — 依赖 `maxParallelForks = 1`(见下文),8 个 DAO 测试类在同 JVM 内依次跑,启动成本只付一次

### 测试运行规范

**开发期快速迭代优先用 Makefile 快捷目标：**

```bash
make test-fast    # 跳过 Robolectric 类(DAO/UI/Downloader),只跑纯 JVM 测试 (~10s)
make test-db      # 只跑 DAO/Repo(Robolectric + Room 集成)
make test-one T=CompanionRuntimeTest   # 单一类
make test         # 全量(~60-90s 首跑,缓存后 ~20s)
```

**全量验证（一次运行，一次分析）：**

```bash
./gradlew testDebugUnitTest 2>&1 | tee build/test-run.log
grep -E "FAILED|^e:" build/test-run.log                    # 编译/测试错误
grep -oh 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | awk -F'"' '{s+=$2} END {print s}'
```

- 用 `tee` 保存完整日志，从日志和 XML 报告中提取所有信息
- 首次构建较慢（~40s），缓存命中后仅需 ~6s（已开启 configuration-cache）

### 并发配置：单 fork 复用 Robolectric Runtime

`app/build.gradle.kts` 中 `testOptions.unitTests.all.maxParallelForks = 1` 是有意为之：

- 多 fork 时每个 fork 独立加载 Android runtime,8 个 DAO 测试类各自启动一次 Robolectric (~3-5s/次)
- 单 fork 后 RuntimeEnvironment 在进程内全局复用,MemoryDaoTest 从 13.77s 降到 0.96s (-93%)
- 纯 JVM 测试串行损失远小于 Robolectric 启动节省,实测总耗时下降 ~40s
- **不要改回 `maxParallelForks = Runtime.getRuntime().availableProcessors()`**

## 注释规范

写代码注释的边界：**信息量 ≥ 1 行的非显然决策才写**，否则让代码自解释。

### 不写

- **步骤编号注释**：`// 1. xxx` / `// 2. yyy` 给 `init {}` 或函数体编号。代码顺序已自表达,直接删。
- **复述字面代码**：`if (line.startsWith("#"))` 上不写 `// Skip comments`;`key == "sections"` 上不写 `// "sections:" opener`。条件表达式本身就是文档。
- **考古/历史注释**：`// 方案 A` / `// 原行为只跑 markClicked` / `// 之前 dismissButton 放关闭` —— git blame 里有,删。
- **空头 TODO**：`// 未来 v2 baseline 持久化后,会写 7 天的 diff` / `// Phase 1 拆开后这条分支应改` —— 应开 issue,不在源码里埋雷。
- **过度解释防御性代码**：`runCatching { ... }` 上不写 3 行解释"为什么用 runCatching"。
- **1 行废话**：`// 同步本地 count` / `// 1) 大段文本(>= 48 chars)→ 立即 flush` 紧跟字面代码。

### 压到 1-2 行

- 5 行布局解释 → 1 行(`// Text 显式 height(48.dp) + wrapContentHeight 让 glyph 视觉中心对齐外 Row`)。
- 3 行英文注释 → 1 行中文(`// 中心裁剪:保留前 1/3 + 后 2/3,丢中间`)。
- `when` 5 个分支每个加章节标签 → 全删,表达式自解释。

### 保留

- **跨文件协议/契约引用**:`CreateLocalReminderTool.kt:122 不走 envelope,直接 buildJsonObject` / `魔搭 (ModelScope) Inference 端点,兼容 Anthropic Messages 协议:鉴权用 x-api-key,请求路径 ${baseUrl}/v1/messages`。
- **hack 原因**:`// contract 自身也走 enforceValidPackage,在 realme 上不可靠,需 fallback` —— 不写会让人重蹈覆辙。
- **非显然决策**:`// Don't increment i; fall through to process lines[i] as normal` —— 解释为什么不 ++i,防止重写时误加。
- **1 行简明信号**:`// 本地模型走 MNN,无网络依赖,直接视为可达` / `// 任何 source 跑成功 = 整体成功`。

### 章节标题密度

`// --- xxx ---` 风格密度太高(> 5 个)时合并:Converters.kt 10 个标题 → 1 个;`when` 5 个分支每个加标题 → 全删,表达式自解释。`//region xxx` 大块分隔(> 30 行)可保留作导航。

## 网站截图规范

所有网站（`web/apps/web`）相关截图、视觉审计素材、参考站对比图都放在**固定目录**：

```
docs/plan/visual-audit-assets/
```

约定：

- **不入库**：已在 `.gitignore` 显式忽略，纯本地工作产物（避免仓库膨胀 4-5MB）
- **不 commit**：截图不参与版本控制；本地用完即可
- **不要**在别的目录零散放截图（`tmp-*.png`、`screenshot_*.png` 根目录等），统一到这里
- **命名规范**（建议）：
  - `<page>-hero.png` / `<page>-full.png` — 视口 / fullPage 截图
  - `<page>-<section>.png` — 某段特写（如 `t2-reaction-zoomed.png`）
  - `ref-<site>.png` — 参考站对比（ref-vercel、ref-stripe、ref-linear、ref-apple）
  - 前缀按任务/批次命名（`t2-`、`p1-`、`audit-`），方便后续清理
- **新引用**：在 `docs/plan/visual-audit-p*.md` 审计文档里通过相对路径 `![](../../visual-audit-assets/...)` 引用

清理命令（截图不再需要时）：

```bash
rm -rf docs/plan/visual-audit-assets/   # 整目录清空
```
