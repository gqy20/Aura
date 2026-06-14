# Android 智能体应用 — 工程化规范文档

> 本文档定义项目的 CI/CD 流水线、测试策略、代码规范和质量门禁。
> 与 [architecture.md](./architecture.md) 配合使用：架构文档管"用什么"，本文档管"怎么写/怎么测/怎么发"。
>
> 当前状态（2026-05-15）：本地已验证 `testDebugUnitTest` 与 `assembleDebug` 通过；CI、ktlint、完整 instrumented tests 仍按本文档作为目标规范推进。

---

## 一、CI/CD 流水线

### 1.1 平台选择

**GitHub Actions** — 项目托管在 GitHub 上时最自然的选择。

### 1.2 Pipeline 阶段

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Lint 检查   │ → │  Unit Test  │ → │ Android Test│ → │   Build APK  │
│ (Kotlin/     │    │ (JVM 层)    │    │ (设备/模拟器) │    │ (Debug +     │
│  Android)    │    │             │    │              │    │  Release)    │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
       ↓                  ↓                  ↓                  ↓
   代码风格          核心逻辑正确性      UI/集成正确性        可交付产物
```

### 1.3 GitHub Actions 配置

#### `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true"

jobs:
  # === 阶段 1：Lint ===
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Run Android Lint
        run: ./gradlew :app:lint
      # If ktlint is added later:
      # - name: Run Kotlin Lint
      #   run: ./gradlew :app:ktlintCheck

  # === 阶段 2：Unit Tests ===
  test:
    runs-on: ubuntu-latest
    needs: lint
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Run Unit Tests
        run: ./gradlew :app:testDebugUnitTest --stacktrace
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: app/build/reports/tests/testDebugUnitTest/

  # === 阶段 3：Android Instrumented Tests ===
  android-test:
    runs-on: ubuntu-latest
    needs: test
    if: github.event_name == 'pull_request' || github.ref == 'refs/heads/main'
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Run Android Instrumented Tests (API 34)
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          script: ./gradlew :app:connectedDebugAndroidTest --stacktrace
      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: android-test-results
          path: app/build/reports/tests/connected/

  # === 阶段 4：Build APK ===
  build:
    runs-on: ubuntu-latest
    needs: android-test
    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/')
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '21'
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      - name: Build Debug APK
        run: ./gradlew :app:assembleDebug
      - name: Build Release APK
        run: ./gradlew :app:assembleRelease
        env:
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_STORE_FILE: ${{ secrets.SIGNING_STORE_FILE }}
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
      - name: Upload Release APK
        uses: actions/upload-artifact@v4
        with:
          name: release-apk
          path: app/build/outputs/apk/release/*.apk
```

### 1.4 触发策略

| 事件 | 执行阶段 | 说明 |
|------|----------|------|
| PR 到 main/develop | Lint → Unit Test | 快速反馈，不跑设备和构建 |
| Push 到 main | 全部 4 个阶段 | 完整流水线 |
| 打 Tag (`v*`) | 全部 4 个阶段 + Release 签名 | 发布版本 |

---

## 二、测试策略

### 2.1 测试金字塔

```
                ╱╲
               ╱ E2E ╲              ← 少量（关键路径）
              ╱────────╲
             ╱ Android  ╲            ← 少量（UI 集成）
            ╱────────────╲
           ╱   Unit Test  ╲          ← 大量（核心逻辑）
          ╱────────────────╲
         ╱  Core 自研模块优先 ╲
```

**原则：自研代码高覆盖，框架胶水代码低覆盖。**

### 2.2 分层测试要求

#### Unit Tests（`src/test/`）

目标覆盖率：**core/ 目录 ≥ 80%**

| 模块 | 测试重点 | 工具 |
|------|----------|------|
| `core/companion` | 主循环编排、Koog wrapper、parser、model | JUnit4 + MockK + Turbine |
| `core/prompt` | 模板渲染、变量替换、系统提示组装 | JUnit4 |
| `core/tools` | 工具参数、Room 记录、结果展示语义 | JUnit4 + MockK |
| `data/db` | DAO 查询、迁移、Room in-memory 行为 | JUnit4 + Room in-memory |
| `data/repository` | 数据 CRUD、缓存逻辑 | JUnit4 + Room in-memory |
| `feature/chat` | ViewModel 状态流、错误处理、消息展示 | JUnit4 + Turbine / Compose Test |
| `platform/speech` | 接口契约、事件流格式（规划中） | JUnit4 + Turbine |

```kotlin
// 示例：情绪状态机测试
class EmotionStateMachineTest {
    private lateinit var machine: EmotionStateMachine

    @BeforeEach
    fun setup() {
        machine = EmotionStateMachine(initialState = EmotionState.Neutral)
    }

    @Test
    fun `positive interaction should increase happiness`() {
        val signal = EmotionSignal.PositiveInteraction(intensity = 0.8f)
        machine.feed(signal)
        assertEquals(EmotionMood.Happy, machine.currentMood)
        assertTrue(machine.currentHappiness > 0.5f)
    }

    @Test
    fun `happiness should decay over time without interaction`() {
        machine.feed(EmotionSignal.PositiveInteraction(1.0f))
        machine.elapse(hours = 6)
        assertTrue(machine.currentHappiness < 1.0f) // 已衰减
    }
}
```

#### Android Instrumented Tests（`src/androidTest/`）

目标：**关键用户路径覆盖，不追求全面。**

| 测试场景 | 说明 |
|----------|------|
| 聊天页发送消息 → 收到回复 | 完整 UI 交互链路 |
| 设置页切换模型（GLM ↔ Kimi） | DataStore 持久化验证 |
| 相机拍照 → 发送给 Agent | 多模态端到端（Mock API） |
| 权限拒绝 → 优雅降级 | CAMERA / MICROPHONE 权限 |
| WorkManager Pulse 触发 | 后台任务执行验证 |

```kotlin
// 示例：聊天页 UI 测试
@RunWith(AndroidJUnit4::class)
class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun messageAppearsAfterSending() {
        composeTestRule.setContent {
            CompanionTheme {
                ChatScreen(viewModel = mockViewModel)
            }
        }

        composeTestRule.onNodeWithTag("message_input")
            .performTextInput("你好")
        composeTestRule.onNodeWithTag("send_button")
            .performClick()

        composeTestRule.onNodeWithText("你好")
            .assertIsDisplayed()

        // 等待 Agent 回复（使用 Turbine 验证 Flow）
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("agent_reply_bubble")
            .assertIsDisplayed()
    }
}
```

#### 不需要测试的部分

| 内容 | 原因 |
|------|------|
| Koog 内部行为 | 框架自身有测试，我们只测接口契约 |
| Room DAO 基础 CRUD | Room 编译期已验证 SQL 正确性 |
| Compose 组件渲染细节 | 变动频繁，投入产出比低 |
| Hilt DI 装配 | 编译期校验即可 |
| DataStore 读写 | Google 已充分测试 |

### 2.3 Mock 策略

```kotlin
// Koog Agent 的标准 Mock 方式
class CompanionRuntimeTest {
    @MockK
    private lateinit var mockAgent: AIAgent

    private lateinit var runtime: CompanionRuntime

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        runtime = CompanionRuntime(
            koogAgentFactory = object : KoogAgentFactory {
                override fun create(config: LlmConfig): AIAgent = mockAgent
            },
            // ... 其他依赖用真实或简化实现
        )
    }

    @Test
    fun `should parse agent response and update emotion`() {
        // Given
        every { mockAgent.run(any()) } returns """{
            "emotion": "happy",
            "reply": "今天天气真好呢！",
            "actions": ["show_happy_expression"]
        }"""

        // When
        val results = runtime.send(UserInput.Text("今天天气真好")).toList()

        // Then
        assertEquals(AgentEvent.Complete, results.last())
        verify { mockAgent.run(any()) }
    }
}
```

### 2.4 测试辅助工具

| 工具 | 用途 |
|------|------|
| **JUnit 4** | 当前项目使用的单元测试框架 |
| **MockK** | Kotlin 原生 Mock 框架（比 Mockito 更适合 Kotlin） |
| **Turbine** | Kotlin Flow 测试（收集 Flow emit 事件并断言） |
| **Espresso / Compose Test Rule** | Android UI 测试 |
| **Room in-memory** | 数据库单元测试（`:memory:` 模式） |
| **Robolectric**（可选） | JVM 上运行 Android API 测试，加速单元测试 |

---

## 三、代码规范

### 3.1 Kotlin 编码规范

遵循 **[Official Kotlin Coding Conventions](https://kotlin-lang.org/docs/coding-conventions.html)**，补充项目特定规则：

#### 命名

| 类型 | 规范 | 示例 |
|------|------|------|
| 类 / Interface | PascalCase | `CompanionRuntime`, `SpeechInput` |
| 函数 / 方法 | camelCase | `sendMessage()`, `buildPrompt()` |
| 属性 / 变量 | camelCase | `currentMood`, `apiKey` |
| 常量（顶层 / const） | UPPER_SNAKE_CASE | `MAX_TOKENS`, `DEFAULT_MODEL` |
| sealed class 子类 | PascalCase | `UserInput.Text`, `UserInput.Vision` |
| ViewModel | 以 ViewModel 结尾 | `ChatViewModel`, `SettingsViewModel` |
| DAO 接口 | 以 Dao 结尾 | `MessageDao`, `MemoryDao` |
| Entity 数据类 | 名词单数 | `Message`, `MemoryItem` |

#### 文件组织

```kotlin
// 标准文件结构顺序：
package com.xiaoqi.companion.core.emotion

// 1. imports（分组：Kotlin stdlib / Android / 项目内部 / 第三方）
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.stringPreferencesKey
import com.xiaoqi.companion.core.emotion.model.EmotionState
import ai.koog.agents.AIAgent

// 2. 类级别注解（如有）

// 3. 类 / 对象声明
class EmotionStateMachine(
    private val initialState: EmotionState = EmotionState.Neutral
) {
    // 4. public 属性
    var currentMood: EmotionMood = initialState.mood
        private set

    // 5. companion object（如果有）
    companion object {
        const val DECAY_HOURS = 6f
    }

    // 6. public 方法
    fun feed(signal: EmotionSignal) { ... }

    // 7. internal / protected 方法
    internal fun applyDecay(elapsedHours: Float) { ... }

    // 8. private 方法
    private fun calculateDecayFactor(elapsedHours: Float): Float { ... }

    // 9. 内部类（按需）
    data class StateSnapshot(...)
}
```

#### 具体规则

| 规则 | 说明 |
|------|------|
| 显式类型声明 | 公共 API 必须显式声明返回类型；局部变量可省略 |
| 避免 `!!` 操作符 | 使用 `?.let` / `?:` / `requireNotNull` 替代 |
| sealed class 优先于 enum | 有状态/数据携带需求时用 sealed class |
| 扩展函数谨慎使用 | 仅在确实提升可读性时使用，避免滥用 |
| 协程 dispatcher 明确 | `withContext(Dispatchers.IO)` 显式切换，不隐式依赖 |
| Flow 优先于 LiveData | 新代码统一用 Flow，LiveData 仅用于 Compose 观察 |
| 注解驱动序列化 | 用 `@Serializable` + `kotlinx.serialization`，不用 Gson/Moshi |
| Data Class 优先 | 值对象一律用 data class |
| Value Class 包装 | 类型安全 ID（如 `@JvmInline value class MessageId(val value: String)`） |

### 3.2 Compose 编码规范

```kotlin
// ✅ 好的实践
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MessageList(messages = uiState.messages)
        InputBar(
            text = uiState.inputText,
            onTextChange = viewModel::onInputChanged,
            onSend = viewModel::onSendMessage
        )
    }
}

// ❌ 避免
@Composable
fun BadExample() {
    var count by remember { mutableStateOf(0) }  // 状态应在 ViewModel 中
    LaunchedEffect(Unit) {                        // 复杂副作用应提取
        delay(1000)
        count++
    }
}
```

| 规则 | 说明 |
|------|------|
| 状态提升到 ViewModel | Compose 只做展示，不持有业务状态 |
| 无状态 Compose 优先 | 纯函数式组件，参数传入不依赖外部 |
| TestTag 用于测试 | 关键交互元素加 `Modifier.testTag("xxx")` |
| Preview 注解 | 每个 Screen 至少一个 `@Preview` |
| 避免深层嵌套 | 超过 3 层嵌套时提取子组件 |

### 3.3 Git 提交规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 列表：**

| Type | 用途 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(chat): add streaming reply bubble animation` |
| `fix` | Bug 修复 | `fix(emotion): correct decay calculation boundary` |
| `refactor` | 重构（不改行为） | `refactor(prompt): extract template engine to separate class` |
| `perf` | 性能优化 | `perf(memory): add index on messages table timestamp column` |
| `test` | 测试相关 | `test(companion): add unit tests for agent loop` |
| `chore` | 构建/工具 | `chore(gradle): upgrade room version to 2.6.1` |
| `docs` | 文档 | `docs(architecture): add vision multimodal section` |
| `style` | 代码格式 | `style: fix ktlint warnings in emotion package` |

**Scope 建议：** 使用模块名作为 scope —— `chat`, `emotion`, `companion`, `prompt`, `settings` 等。

### 3.4 分支策略

```
main (受保护)
  │
  ├── develop (开发主线)
  │     │
  │     ├── feature/chat-streaming
  │     ├── feature/emotion-state-machine
  │     ├── feature/vision-multimodal
  │     │
  │     └── fix/pulse-decay-bug
  │
  └── release/x.y.z (发布分支)
```

| 分支 | 用途 | 保护规则 |
|------|------|----------|
| `main` | 生产就绪代码 | 禁止直接 push，仅通过 PR 合并 |
| `develop` | 开发集成分支 | PR 到 main 时自动合并 |
| `feature/*` | 功能开发 | 从 develop 创建，PR 回 develop |
| `fix/*` | Bug 修复 | 从 develop 创建，PR 回 develop |
| `release/*` | 版本发布 | 从 main 创建，仅允许 hotfix 合入 |

### 3.5 Code Review 检查清单

每次 PR 应检查：

#### 功能性
- [ ] 功能是否符合设计文档描述？
- [ ] 边界情况是否处理？（空输入、网络失败、权限缺失）
- [ ] 多模态场景下图片大小是否有限制？

#### 代码质量
- [ ] 是否遵循命名规范？
- [ ] 是否有不必要的 `!!` 或 try-catch 吞异常？
- [ ] 协程是否在正确的 Dispatcher 上运行？
- [ ] Compose 状态是否正确提升？

#### 安全性
- [ ] API Key 是否硬？（必须从 DataStore / BuildConfig 读取）
- [ ] 敏感数据是否写入日志？（禁止 log apiKey / token）
- [ ] 图片 base64 数据是否持久化？（不应存储原始帧）

#### 性能
- [ ] Compose 是否有不必要的重组？（使用稳定类型）
- [ ] Room 查询是否有合适索引？
- [ ] 大列表是否使用了 LazyColumn？

### 3.7 开发边界（架构硬约束）

以下规则与本节其他规范具有同等优先级,违反应作为 PR 阻断项:

| 编号 | 规则 | 原因 |
|------|------|------|
| B1 | **不要在 Activity / Fragment / Compose UI 中直接创建 `AIAgent` 或 LLM client** | UI 层只消费 ViewModel 暴露的状态;Koog 细节留在 `runtime` / `factory` / `llm` 层 |
| B2 | **UI 层只消费 ViewModel 暴露的 StateFlow / SharedFlow** | Koog 事件、LLM 响应解析、Tool 协议不得泄漏到 Composable |
| B3 | **`CompanionRuntime` 是主流程编排层** | 新增 Agent 行为前先确认归属:runtime / tool / repository / UseCase / UI,避免在错误层堆逻辑 |
| B4 | **记忆 / 情绪 / 关系写入以"回复完成后系统阶段"为主** | 不要轻易重新暴露为主对话可写工具,避免模型自行写敏感状态 |
| B5 | **Vision 主回复禁用 tools** | Vision 路径只走图像理解,工具调用留给回复后的 reflection 做记忆整理 |
| B6 | **改 Room schema / DataStore key / Prompt 行为 / 工具协议时必须同步补测试** | 任何涉及持久化、配置或跨层契约的改动,先写测试再改实现 |

---

## 四、质量门禁

### 4.1 PR 合并门槛

| 条件 | 要求 |
|------|------|
| CI 全绿 | Lint + Test + Build 全部通过 |
| 至少 1 人 Approval | Code Review 通过 |
| 核心模块改动 | 需要对应模块的 Unit Test 覆盖 |
| `core/` 下文件改动 | 必须有新增或更新的测试 |

### 4.2 发布检查清单

| 步骤 | 检查项 |
|------|--------|
| 版本号 | `versionName` 和 `versionCode` 已递增 |
| 签名 | Release APK 已正确签名 |
| ProGuard | `minifyEnabled = true`，无警告遗漏 |
| 多模态 | CameraX 权限声明完整 |
| 隐私政策 | 数据采集说明已更新（如有变更） |
| 模型配置 | 默认 GLM-5v-turbo，Kimi 可切换正常 |
| 崩溃分析 | Crashlytics / Sentry SDK 已集成 |

---

## 五、开发环境配置

### 5.1 推荐的 IDE 设置

| 设置项 | 推荐值 |
|--------|--------|
| Kotlin 代码风格 | Official Kotlin conventions |
| Tab 大小 | 4 spaces（Kotlin 标准） |
| Import 排序 | kotlin / android / 第三方 / 项目内部 |
| 保存时自动格式化 | 开启 |
| 保存时自动优化 import | 开启 |
| Inspection 严重级别 | Error: `!!` / 未处理异常 / 空指针风险 |

### 5.2 推荐的 IDE 插件

| 插件 | 用途 |
|------|------|
| **Kotlin** | JetBrains 官方（内置） |
| **Hilt** | DI 注解支持与导航 |
| **Room** | SQL 高亮与 DAO 检查 |
| **Compose** | Preview + 语法高亮 + 检查 |
| **GitLens** | Git 历史可视化 |
| **Error Prime** | 错误信息增强 |
| **Ktlint** | Kotlin 代码风格检查（可选，CI 层面已有） |

### 5.3 本地开发配置

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

```bash
# local.properties（不提交到 Git）
sdk.dir=C:\\Users\\gqy17\\AppData\\Local\\Android\\Sdk
```
