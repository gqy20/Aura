# Android 项目 - 开发环境

## 项目文档

- **[Koog API 参考](docs/koog-api-reference.md)** — 从 Gradle 缓存 JAR (`javap -p -s`) 提取的 Koog v0.8.0 完整 API 签名，覆盖 Agent/Builder/Strategy/Service/Tool/Pipeline/Prompt&LLM 等 15 个模块，含架构图和类型签名表。
- **[Koog ↔ Android 集成指南](docs/koog-android-integration.md)** — Koog 在 Android 上的集成方案：当前项目审计（Stub 工厂/MainActivity 反模式/URL 不一致）、两阶段实现策略（非流式验证 → 流式生产）、线程规则（禁止 runBlocking/KG-750 死锁）、生命周期模式、完整可运行代码。

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
| Kotlin | 由 Gradle plugin 管理（目标 2.0+） |
| AGP | 8.6.1 |
| SDK Root | `C:\Users\gqy17\AppData\Local\Android\Sdk` |
| SDK Platform | android-36.1 |
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
- **不要尝试跨 test class 共享 DB 实例** — Robolectric 的 `ShadowLegacySQLiteConnection` 不支持，会导致连接状态冲突

### 测试运行规范

**一次运行，一次分析。** 不要反复重跑测试：

```bash
./gradlew testDebugUnitTest 2>&1 | tee build/test-run.log
grep -E "FAILED|^e:" build/test-run.log                    # 编译/测试错误
grep -oh 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | awk -F'"' '{s+=$2} END {print s}'
```

- 用 `tee` 保存完整日志，从日志和 XML 报告中提取所有信息
- 首次构建较慢（~40s），缓存命中后仅需 ~6s（已开启 configuration-cache）
