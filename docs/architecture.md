# Android 智能体应用 — 技术架构文档

> 设计原则：**自研"灵魂"，外包"器官"。**
> 创新点在 Companion Core（情绪状态机 / 关系系统 / Prompt 组装 / LLM 输出解释器），
> 基础设施全部使用 Android/Kotlin 生态成熟组件。
>
> Agent 框架：**Koog（JetBrains）** | LLM：**GLM-5v-turbo（默认）/ Kimi 2.6（可切换）**

---

## 当前实现状态

> Last verified: 2026-05-15. 详细里程碑见 [roadmap.md](./roadmap.md)。

当前代码已经达到 **文本聊天技术闭环 / Phase 1 agent tools** 阶段：

- 已实现 Compose 聊天页、`ChatViewModel`、`CompanionRuntime` 和 Koog `AIAgent` 流式调用链路。
- 已实现 Room/DataStore/Hilt 基础设施，消息、记忆、情绪快照、工具调用记录可持久化。
- 已实现 `save_memory`、`search_memory`、`update_mood`、`update_relationship` 四个 Agent tools。
- 已通过 `testDebugUnitTest` 与 `assembleDebug` 验证。

仍处于规划或部分实现状态的模块：

- 设置页、导航、记忆房间 UI、角色主屏、Lottie 表情层。
- CameraX 拍照/选图、多模态 UI、运行时权限 UX。
- SpeechRecognizer/TextToSpeech 语音能力。
- WorkManager pulse、通知、离线衰减、主动关怀。

## 一、基础技术栈

| 层面 | 选型 | 说明 |
|------|------|------|
| JDK | **21**（Oracle LTS 21.0.6） | 与当前本地环境一致 |
| 语言 | **Kotlin 2.3.21** | 由 Gradle plugin 管理 |
| Android Gradle Plugin | **9.2.0** | 以 `gradle/libs.versions.toml` 为准 |
| SDK | compileSdk 36 / minSdk 26 / targetSdk 36 | 当前项目实际配置 |
| UI | Jetpack Compose | 官方现代声明式 UI，状态驱动 |
| 架构 | MVVM + Repository + UseCase | 标准分层，职责清晰 |
| 并发 | Kotlin Coroutines + Flow | 异步事件流，天然适配 Agent 场景 |
| Agent 框架 | **Koog 0.8.0**（JetBrains） | Agent 运行时 |
| LLM 默认模型 | **GLM-5v-turbo**（智谱 AI） | 多模态（Vision），兼容 Anthropic Messages API |
| LLM 备选模型 | **Kimi 2.6**（Moonshot AI） | 可切换，同样兼容 Anthropic Messages API |
| API 协议 | **Anthropic Messages API**（兼容格式） | 统一接口，切换模型只需改 base_url + model name |
| 依赖注入 | Hilt | 与 ViewModel/Navigation/WorkManager 深度集成 |
| 数据库 | Room / SQLite | 持久化消息、记忆、状态 |
| 配置存储 | DataStore | 轻量键值对配置（API key、主题等） |
| 后台任务 | WorkManager | 可延迟、可持久化的后台调度（Pulse） |
| 序列化 | kotlinx.serialization | 结构化 JSON 输入输出 |
| 动画 | Lottie / Rive | 依赖已规划，角色表情层尚未实现 |
| 语音 | Android TTS + SpeechRecognizer | 规划中 |
| 相机 | CameraX | 依赖已接入，拍照/选图 UI 尚未实现 |
| 图片加载 | Coil | Compose 原生图片加载 |
| 日志 | Timber | 轻量日志库 |
| 崩溃分析 | Firebase Crashlytics / Sentry | 生产级监控 |

---

## 二、项目分层

```
app/
├─ feature/                    # 功能模块（UI 层）
│  ├─ chat/                    #   聊天对话（已实现）
│  ├─ avatar/                  #   角色主屏（规划中）
│  ├─ memory_room/             #   记忆房间（规划中）
│  ├─ settings/                #   设置页（规划中）
│  └─ onboarding/              #   新手引导（规划中）
│
├─ core/                       # 核心业务逻辑（自研 + Koog）
│  ├─ companion/               #   ★ CompanionRuntime 主循环（自研）
│  ├─ llm/                     #   Anthropic Messages 兼容 LLM client / executor
│  ├─ prompt/                  #   ★ Prompt 组装引擎（自研）
│  ├─ tools/                   #   Agent tools（已实现基础能力）
│  ├─ logging/                 #   日志封装与字段脱敏
│  └─ pulse/                   #   ★ 生命脉冲策略（规划中）
│
├─ data/                       # 数据层
│  ├─ db/                     #   Room DAO / Entity
│  ├─ datastore/              #   DataStore 配置
│  ├─ repository/             #   Repository 实现
│  └─ assets/                 #   本地资源文件
│
└─ platform/                   # 平台能力封装
   ├─ speech/                 #   语音输入输出
   ├─ camera/                 #   相机能力
   ├─ notification/           #   通知推送
   ├─ widget/                 #   桌面小组件
   └─ permissions/            #   权限管理
```

> 上面包含目标形态。当前源码中已经落地 `feature/chat`、`core/companion`、`core/llm`、`core/prompt`、`core/tools`、`core/logging`、`data`、`di`；`platform` 与多数非聊天 feature 仍在 roadmap 中。

### 自研范围（标 ★）

只有以下模块需要深度自研：

- `core/companion` — Agent 主循环运行时（调用 Koog AIAgent，但主流程自研）
- `core/emotion` — 情绪状态机
- `core/relationship` — 关系亲密度模型
- `core/pulse` — 生命感脉冲策略
- `core/prompt` — Prompt 组装与模板引擎

**Koog 覆盖的部分：** LLM 客户端（Anthropic 兼容）、对话历史管理、流式输出、结构化 Tool Use、错误重试。

---

## 三、LLM 选型与多模态设计

### 3.1 模型选择

#### 主力模型：GLM-5v-turbo（默认）

| 属性 | 值 |
|------|-----|
| 提供商 | 智谱 AI（ZhipuAI） |
| 多模态 | **原生支持 Vision**（文本 + 图片理解） |
| API 格式 | **Anthropic Messages API 兼容** |
| 长上下文 | 支持 |
| 流式输出 | SSE streaming |
| 结构化输出 | Tool Use / JSON mode |

选择 GLM-5v-turbo 的原因：
- **原生多模态**：Vision 能力是核心需求（CameraX → 图片理解）
- **Anthropic 兼容**：通过项目内 `AnthropicMessagesLLMClient` 适配 Koog executor，统一 GLM/Kimi 调用形态
- **国内服务**：延迟低、稳定性好
- **成本优势**：相比 Claude 有竞争力

#### 备选模型：Kimi 2.6（可切换）

| 属性 | 值 |
|------|-----|
| 提供商 | Moonshot AI（月之暗面） |
| API 格式 | **Anthropic Messages API 兼容** |
| 特点 | 长文本能力强，适合记忆密集场景 |

切换方式：**只改 base_url + api_key + model name，代码零改动。**

```kotlin
// 模型配置（DataStore 存储，设置页切换）
data class LlmConfig(
    val provider: LlmProvider = LlmProvider.GLM,     // GLM 或 KIMI
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v1",
    val apiKey: String,
    val modelName: String = "glm-5v-turbo",          // 或 "kimi-latest"
)
```

### 3.2 Anthropic 兼容协议

GLM-5v-turbo 和 Kimi 2.6 都兼容 **Anthropic Messages API** 格式，这意味着：

```
App 代码
  ↓
Koog PromptExecutor + AnthropicMessagesLLMClient(baseUrl, apiKey, model)
  ↓ （自动组装 Anthropic 格式请求）
{ "model": "glm-5v-turbo", "messages": [...], "stream": true }
  ↓
GLM / Kimi API Server（兼容 Anthropic 格式）
  ↓ （返回 Anthropic 格式响应）
{ "type": "content_block_delta", "delta": { "type": "text_delta", "text": "..." } }
  ↓
Koog 自动解析 → Flow<AgentEvent>
```

**关键收益：**
- App 业务层只依赖 `CompanionRuntime` / `KoogAgentFactory`，不直接接触 HTTP 细节
- Anthropic Messages 请求、SSE 解析和工具 schema 适配集中在 `core/llm`
- 模型切换只需换配置，Runtime/Chat UI 接口不变

### 3.3 API 能力映射

```
文本对话（Chat）          → Messages API          ✅ GLM/Kimi 均支持
流式输出（Streaming）     → SSE stream             ✅ 通过项目内兼容层接入 Koog
多模态视觉（Vision）      → image content block    ✅ GLM-5v-turbo 原生支持
结构化输出（Tool Use）    → tool_use + JSON schema ✅ 通过项目内兼容层接入 Koog tools
长上下文                 → 大窗口                 ✅ 两者均支持
Prompt 缓存（Caching）   → cache_control          ⚠️ 取决于提供商实现
```

---

## 四、各层详细设计

### 4.1 UI：Jetpack Compose

所有界面使用 Compose 声明式实现：

| 页面 | 状态驱动要素 |
|------|-------------|
| 聊天页 | reply streaming → 气泡逐字显示 |
| 角色主屏 | mood 变化 → 表情/姿态变化 |
| 记忆房间 | memories → 卡片列表 |
| 设置页 | 配置项 → 表单控件（含模型切换） |
| 关系天气 | relationship level → 天气可视化 |
| 小纸条 | secret note → 弹出动画 |

```kotlin
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
```

Compose 的优势：强状态驱动产品天然匹配 —— mood 变了 UI 自动变，reply streaming 自动刷新气泡。

---

### 4.2 数据库：Room

Room 负责持久化的核心数据：

| 表名 | 存储内容 |
|------|----------|
| messages | 聊天消息记录（已实现） |
| memories | 长期记忆条目（已实现） |
| agent_state | Agent 当前状态快照（已实现基础表） |
| mood_snapshots | 情绪历史快照（已实现） |
| tool_calls | Agent 工具调用记录（已实现） |
| life_events | 生活事件时间线（规划中） |
| agent_profile | 角色档案配置（规划中） |
| memory_objects | 记忆物品（照片、地点等，规划中） |
| scheduled_actions | 待执行的定时动作（规划中） |

```kotlin
implementation("androidx.room:room-runtime:<version>")
implementation("androidx.room:room-ktx:<version>")
ksp("androidx.room:room-compiler:<version>")
```

不要自己写 SQLiteOpenHelper，没必要。

---

### 4.3 配置：DataStore

DataStore 存储轻量键值对配置：

| 配置项 | 类型 |
|--------|------|
| API Key | String?（已实现） |
| 主题模式 | Enum (Light/Dark/System，已实现） |
| **LLM Provider** | **Enum (GLM / KIMI，已实现）** |
| **当前模型名称** | **String (glm-5v-turbo / kimi-latest 等，已实现）** |
| 当前角色 ID | String（规划中） |
| 语音开关 | Boolean（规划中） |
| 是否允许主动通知 | Boolean（规划中） |
| 用户隐私设置 | Preferences（规划中） |

```kotlin
implementation("androidx.datastore:datastore-preferences:<version>")
```

---

### 4.4 后台 Pulse：WorkManager

"生命感"不靠常驻后台服务，而是通过以下机制组合：

| 触发方式 | 用途 |
|----------|------|
| 打开 App 时补算状态 | 冷启动恢复 |
| WorkManager 低频 pulse | 定期情绪衰减/回忆触发 |
| 通知触发 | 用户交互响应 |
| 定时小纸条 | 主动关怀推送 |
| 夜间复盘 | 离线期间状态更新 |
| 离开后反应 | 回归时的欢迎逻辑 |

```kotlin
implementation("androidx.work:work-runtime-ktx:<version>")
implementation("androidx.hilt:hilt-work:<version>")
ksp("androidx.hilt:hilt-compiler:<version>")
```

---

### 4.5 网络：Koog + Anthropic Messages 兼容层

当前项目通过 `core/llm/AnthropicMessagesLLMClient.kt` 实现 Anthropic Messages 兼容请求，再接入 Koog 的 `PromptExecutor` / `AIAgent`：

- HTTP 连接管理（基于内部 HttpClient）
- 请求序列化（Anthropic Messages 格式）
- 响应反序列化（SSE stream 解析）
- 错误处理与重试
- 超时控制

App 业务层只需通过 DataStore / BuildConfig 提供 `baseUrl`、`apiKey`、`modelName`，由 `KoogPromptExecutorFactory` 和 `KoogAgentFactoryImpl` 负责创建执行器与 Agent。

> **注意：** App 中仍可能需要 Ktor/Retrofit 用于非 LLM 的其他网络请求（如崩溃上报、 analytics 等）。如有需要再单独引入。

---

### 4.6 依赖注入：Hilt

选 Hilt 而非 Koin 的原因：
- 与 Jetpack ViewModel / Navigation / WorkManager 官方集成
- 编译期校验，错误更早发现
- Android 项目标准实践

```kotlin
implementation("com.google.dagger:hilt-android:<version>")
ksp("com.google.dagger:hilt-compiler:<version>")
implementation("androidx.hilt:hilt-navigation-compose:<version>")
implementation("androidx.hilt:hilt-work:<version>")
ksp("androidx.hilt:hilt-compiler:<version>")
```

---

### 4.7 动画与角色显示

分阶段演进策略：

| 阶段 | 方案 | 说明 |
|------|------|------|
| MVP | Lottie + 静态立绘 | 轻、成熟、够用 |
| 进阶 | Rive | 交互动画更强，支持状态机式角色动画 |
| 高级 | Live2D / Unity | 真实角色表现力 |

```kotlin
implementation("com.airbnb.android:lottie-compose:<version>")
implementation("io.coil-kt:coil-compose:<version>")
```

---

### 4.8 语音

第一版使用系统原生能力，封装为接口便于后续替换：

```kotlin
interface SpeechInput {
    fun startListening(): Flow<SpeechEvent>
}

interface SpeechOutput {
    suspend fun speak(text: String, style: VoiceStyle)
}
```

- ASR：`SpeechRecognizer`（系统语音识别）
- TTS：`TextToSpeech`（系统语音合成）

后续可无缝替换为云端方案（Whisper API / ElevenLabs 等），只换实现不改接口。

**第一版不做：** 实时全双工语音、唤醒词、打断检测（VAD）。

---

### 4.9 相机：CameraX → Vision 多模态

相机拍帧通过 **GLM-5v-turbo Vision** 能力实现真正的"看见"用户世界：

#### 多模态交互场景

| 场景 | 输入 | 模型理解 | Agent 响应 |
|------|------|---------|-----------|
| "你看我今天的穿搭怎么样" | CameraX 拍帧 | 衣服颜色、风格、搭配 | 情绪化评价 + 关系亲昵度影响语气 |
| "帮我看看这个" | 用户拍照/选图 | 物体识别、场景理解 | 记忆存储 + 情绪反应 |
| 分享屏幕截图 | 截图 | UI 内容、文字提取 | 上下文感知对话 |
| 食物照片 | 拍帧 | 菜品识别、热量估算 | 关心/调侃（取决于关系等级） |
| 外出风景 | 拍帧 | 天气、地点、氛围 | 共鸣情绪 + 记忆标记 |

#### 数据流

```
CameraX ImageCapture
    ↓ JPEG Bitmap
ImageProcessor (压缩 / 裁剪 / base64 编码)
    ↓ base64 字符串 (~500KB 以内)
Koog AIAgent.run(
    content = [
        TextBlock("用户说：你看这个"),
        ImageBlock(base64, media_type="image/jpeg")  // Anthropic image block 格式
    ]
)
    ↓ Koog 自动以 Anthropic 格式发送给 GLM-5v-turbo
    ↓ 模型 Vision 理解图片内容
AgentOutputParser 解析结构化响应
    ↓ 情绪信号 + 文字回复 + 记忆动作
UI 更新（表情 / 气泡 / 记忆卡片）
```

```kotlin
implementation("androidx.camera:camera-core:<version>")
implementation("androidx.camera:camera-camera2:<version>")
implementation("androidx.camera:camera-lifecycle:<version>")
implementation("androidx.camera:camera-view:<version>")
```

关键设计：
- **图片压缩**：CameraX 拍帧需压缩到合理大小（建议 < 500KB），避免 API 成本过高和延迟
- **base64 编码**：Anthropic 兼容 API 接受 inline base64 图片，无需外部 URL
- **缓存策略**：同一帧不重复发送，短时间内的连续请求复用上一次结果
- **权限管理**：运行时申请 CAMERA 权限，优雅降级（无权限时纯文本模式）
- **隐私保护**：图片数据仅用于单次 API 调用，不持久化存储原始帧

---

## 五、Agent Core 设计

### 5.1 核心运行时（基于 Koog）

Koog 负责 LLM 执行层，自研负责情绪驱动的完整主循环：

```kotlin
class CompanionRuntime(
    // Koog 提供的 LLM 执行器（通过 Hilt 注入，根据配置动态创建）
    private val koogAgentFactory: KoogAgentFactory,
    // 自研核心
    private val promptBuilder: PromptBuilder,         // Prompt 组装引擎
    private val emotionMachine: EmotionStateMachine,  // 情绪状态机
    private val relationship: RelationshipModel,      // 关系系统
    private val outputParser: OutputParser,           // LLM 输出解析器
    private val stateReducer: StateReducer,            // 状态归约
    private val memoryManager: MemoryManager,          // Room 记忆管理
    private val actionDispatcher: ActionDispatcher     // 动作分发
) {
    suspend fun send(input: UserInput): Flow<AgentEvent> = flow {
        // 1. 获取当前 LLM 配置（从 DataStore 读取，支持 GLM/Kimi 切换）
        val llmConfig = configRepository.getCurrentLlmConfig()
        val koogAgent = koogAgentFactory.create(llmConfig)

        // 2. 情绪状态影响 Prompt → 自研
        val emotionContext = emotionMachine.currentContext()

        // 3. 关系等级修饰 → 自研
        val relContext = relationship.contextModifier()

        // 4. 组装 Prompt（含记忆注入）→ 自研
        val enhancedInput = promptBuilder.build(input, emotionContext, relContext)

        // 5. 调用 Koog Agent → Koog 处理 LLM 调用（Streaming / 重试 / 压缩）
        val result = koogAgent.run(enhancedInput)

        // 6. 解析输出 → 自研
        val parsed = outputParser.parse(result)

        // 7. 情绪更新 → 自研
        emotionMachine.feed(parsed.emotionSignal)

        // 8. 关系更新 → 自研
        relationship.update(parsed.interactionSignal)

        // 9. 状态归约 → 自研
        stateReducer.reduce(parsed)

        // 10. 存储记忆 → Room
        memoryManager.store(parsed)

        // 11. 分发动作 → 自研
        actionDispatcher.dispatch(parsed.actions)

        emit(AgentEvent.Complete(parsed))
    }
}
```

### 5.2 多模态输入处理

主循环需支持 **文本 + 图片** 混合输入：

```kotlin
sealed class UserInput {
    data class Text(val content: String) : UserInput()
    data class Vision(
        val text: String,              // 用户文字描述
        val imageBase64: String,       // CameraX 拍帧 / 选图
        val mediaType: String = "image/jpeg"
    ) : UserInput()
    data class Speech(val transcript: String) : UserInput()  // STT 结果
}
```

### 5.3 Koog Agent 工厂（模型切换）

```kotlin
class KoogAgentFactoryImpl @Inject constructor(
    private val executorFactory: KoogPromptExecutorFactory,
    private val toolRegistry: AgentToolRegistry,
    private val toolCallRecorder: ToolCallRecorder,
) : KoogAgentFactory {

    override fun create(config: LlmConfig): KoogAgentWrapper =
        KoogPromptExecutorWrapper(
            config = config,
            executor = executorFactory.create(config),
            toolRegistry = toolRegistry,
            toolCallRecorder = toolCallRecorder,
        )
}
```

切换模型只需修改 DataStore 中的配置，下次 `send()` 调用时自动使用新模型。

### 5.4 不要自己造的轮子

| 能力 | 使用什么 |
|------|----------|
| LLM HTTP 客户端 | **项目内 Anthropic Messages 兼容层** |
| Streaming SSE 解析 | **项目内兼容层转换为 Koog streaming events** |
| 对话历史管理 / Token 压缩 | **Koog 能力，当前未启用压缩** |
| 结构化 Tool Use | **Koog tools + 项目内 tool schema 适配** |
| 错误重试 / 容错 | **规划中，当前以错误事件和 UI 提示为主** |
| JSON 解析 | kotlinx.serialization |
| 数据库 ORM | Room |
| 后台调度 | WorkManager |
| DI 容器 | Hilt |
| 日志系统 | Timber |
| 权限系统 | Google Accompanist Permissions |
| 动画播放 | Lottie / Rive |
| 图片加载 | Coil |

---

## 六、工程化规范（Gradle 项目结构）

### 6.1 目录结构总览

```
project-root/
├── app/                          # 主模块
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/xiaoqi/companion/
│       │   │   ├── CompanionApplication.kt
│       │   │   ├── feature/          # 功能模块（UI）
│       │   │   ├── core/             # 核心业务逻辑
│       │   │   ├── data/             # 数据层
│       │   │   └── platform/         # 平台能力
│       │   └── res/
│       ├── test/                   # 单元测试
│       └── androidTest/            # 仪器测试
│
├── buildSrc/                      # 或 gradle/ 目录
│   └── src/main/kotlin/            #   VersionCatalog / Dependencies
│
├── gradle/
│   ├── libs.versions.toml         # 版本目录（统一版本号管理）
│   └── conventions/                # Convention Plugins（复用构建逻辑）
│       ├── android.application.gradle.kts
│       ├── android.compose.gradle.kts
│       ├── android.hilt.gradle.kts
│       ├── android.room.gradle.kts
│       └── android.test.gradle.kts
│
├── build.gradle.kts               # 根构建脚本（plugin 管理）
├── settings.gradle.kts            # 模块注册
├── gradle.properties              # Gradle 配置
├── local.properties               # 本地路径（SDK 等）
└── gradlew / gradlew.bat
```

### 6.2 Version Catalog（版本目录）

所有依赖版本集中在 `gradle/libs.versions.toml` 管理：

```toml
# gradle/libs.versions.toml

[versions]
# Kotlin & AGP
kotlin = "2.3.21"
agp = "9.2.0"

# Core Android
compileSdk = "36"
minSdk = "26"
targetSdk = "36"
ndk = "27.0.12077973"

# Jetpack Compose
compose-bom = "2026.05.00"
compose-activity = "1.9.3"
navigation = "2.8.4"
lifecycle = "2.8.7"

# DI
hilt = "2.59.2"

# Database
room = "2.8.4"

# Background
work = "2.10.0"

# Serialization
kotlinx-serialization = "1.7.3"

# Media / UI
lottie = "6.6.0"
coil = "2.7.0"

# Camera
camerax = "1.4.0"

# Logging
timber = "5.0.1"

# Agent Framework
koog = "0.8.0"

# Testing
junit = "4.13.2"
androidx-test-ext-junit = "1.2.1"
espresso = "3.6.1"
turbine = "1.1.0"
mockk = "1.13.13"

[libraries]
# --- Compose UI ---
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
compose-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version.ref = "compose-activity" }

# --- Coroutines ---
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlin" }

# --- DI: Hilt ---
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
hilt-compiler-androidx = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }

# --- Database: Room ---
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# --- Config: DataStore ---
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version = "1.1.1" }

# --- Background: WorkManager ---
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }

# --- Serialization ---
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# --- Media / Animation ---
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottie" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# --- Camera ---
camera-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }

# --- Logging ---
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }

# --- Agent Framework: Koog ---
koog-agents = { group = "ai.koog", name = "koog-agents", version.ref = "koog" }

# --- Testing ---
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext-junit" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }

[bundles]
compose = ["compose-ui", "compose-material3", "compose-navigation", "compose-viewmodel"]
hilt = ["hilt-android", "hilt-navigation-compose", "hilt-work"]
room = ["room-runtime", "room-ktx"]
camera = ["camera-core", "camera-camera2", "camera-lifecycle", "camera-view"]
test = ["junit", "turbine", "mockk"]
androidTest = ["androidx-test-ext-junit", "espresso-core"]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.7" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
```

### 6.3 Convention Plugins（约定插件）

将重复的构建逻辑抽取为 Convention Plugin，各模块 `build.gradle.kts` 极简：

#### `conventions/android.application.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xiaoqi.companion"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xiaoqi.companion"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

#### `conventions/android.compose.gradle.kts`

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(libs.bundles.compose)
    implementation(libs.compose.activity)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

#### `conventions/android.hilt.gradle.kts`

```kotlin
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(libs.bundles.hilt)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.compiler.androidx)
}
```

#### `conventions/android.room.gradle.kts`

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    // 如果需要导出 Schema
    // room.schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
}
```

#### `conventions/android.test.gradle.kts`

```kotlin
dependencies {
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
}
```

### 6.4 app/build.gradle.kts（最终形态）

有了 Convention Plugin 后，app 模块的构建脚本非常干净：

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)

    // 应用自定义 Convention Plugins
    id("conventions.android.application")
    id("conventions.android.compose")
    id("conventions.android.hilt")
    id("conventions.android.room")
    id("conventions.android.test")
}

dependencies {
    // Compose UI
    implementation(libs.bundles.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // DI
    implementation(libs.bundles.hilt)

    // Database
    implementation(libs.datastore.preferences)
    implementation(libs.bundles.room)

    // Background
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Media / Animation
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    // Camera
    implementation(libs.bundles.camera)

    // Logging
    implementation(libs.timber)

    // Agent Framework: Koog
    implementation(libs.koog.agents)

    // Testing
    testImplementation(libs.bundles.test)
    androidTestImplementation(libs.bundles.androidTest)
}
```

### 6.5 包命名规范

```
com.xiaoqi.companion
├── CompanionApplication.kt          # Application 类
├── MainActivity.kt                  # Single Activity
│
├── feature                          # 功能模块（UI 层）
│   ├── chat
│   │   ├── ui/                     #   Compose Screen / ViewModel
│   │   └── navigation/             #   路由定义
│   ├── avatar/
│   ├── memory_room/
│   ├── settings/
│   └── onboarding/
│
├── core                             # 核心业务逻辑
│   ├── companion/                  #   Agent 主循环
│   │   ├── CompanionRuntime.kt
│   │   ├── KoogAgentFactory.kt
│   │   └── model/                  #     AgentEvent / UserInput / AgentState
│   ├── emotion/                    #   情绪状态机
│   │   ├── EmotionStateMachine.kt
│   │   ├── EmotionState.kt
│   │   └── model/
│   ├── relationship/               #   关系系统
│   │   ├── RelationshipModel.kt
│   │   └── model/
│   ├── pulse/                      #   生命脉冲
│   │   ├── PulsePolicy.kt
│   │   └── PulseScheduler.kt
│   ├── prompt/                     #   Prompt 引擎
│   │   ├── PromptBuilder.kt
│   │   ├── templates/              #     Prompt 模板文件
│   │   └── system/                 #     系统 Persona 定义
│   ├── memory/                     #   记忆管理
│   │   ├── MemoryManager.kt
│   │   └── MemorySelector.kt
│   └── actions/                    #   动作分发
│       ├── ActionDispatcher.kt
│       └── model/
│
├── data                            # 数据层
│   ├── db/
│   │   ├── dao/
│   │   ├── entity/
│   │   ├── database/               #     CompanionDatabase + migrations
│   │   └── converter/              #     TypeConverter
│   ├── datastore/
│   │   └── AppPreferences.kt
│   ├── repository/
│   │   ├── MessageRepository.kt
│   │   ├── MemoryRepository.kt
│   │   ├── ConfigRepository.kt      #     LlmConfig 读写
│   │   └── AgentStateRepository.kt
│   └── assets/
│
└── platform                        # 平台能力封装
    ├── speech/
    │   ├── SystemSpeechInput.kt
    │   └── SystemSpeechOutput.kt
    ├── camera/
    │   └── CameraVisionProvider.kt
    ├── notification/
    │   └── NotificationHelper.kt
    ├── widget/
    │   └── CompanionWidget.kt
    └── permissions/
        └── PermissionHandler.kt
```

---

## 七、代码量估算

### 当前技术闭环 Demo

**9k - 17k 行**

当前已覆盖：Compose 聊天 UI、Koog Agent 调用、项目内 Anthropic Messages 兼容 LLM client、Room 持久化、DataStore 配置、结构化 Tool Use、简单记忆系统、GLM-5v-turbo Vision 底层接入预留。

### 可玩的情绪 MVP

**20k - 33k 行**

包含：聊天、表情动画、TTS 语音、agent_state 状态机、memories 记忆、life_events 事件线、secret notes 小纸条、return reaction 回归反应、WorkManager pulse 脉冲、通知系统、设置页（含模型切换）、CameraX 多模态基础接入。

### 接近产品化

**43k - 72k 行**

包含：完整 UI、隐私/导出/删除功能、CameraX 多模态完整体验、桌面小组件、多角色切换、高级动画、错误恢复、模型热切换（GLM ↔ Kimi）、崩溃分析、付费/订阅体系。

### 使用成熟包后的代码节省

| 领域 | 节省来源 |
|------|----------|
| **LLM 客户端** | **通过项目内兼容层集中封装，业务层无需直接处理 HTTP/SSE** |
| Streaming / 历史压缩 | Streaming 已接入；历史压缩当前配置为 NoCompression |
| Tool Use / 结构化输出 | Koog tools + 项目内 Anthropic Messages 兼容层 |
| 错误重试 / 容错 | 当前基础错误处理，重试策略待补 |
| 数据库 | Room 替代手写 SQLite |
| 后台任务 | WorkManager 替代 Service+AlarmManager |
| DI | Hilt 替代手动工厂 |
| 动画 | Lottie/Rive 替代自定义动画引擎 |
| 图片加载 | Coil 替代手动缓存 |
| 语音 | 系统 API 替代自研引擎 |
| 相机 | CameraX 替代 Camera2 原生 API |

自研代码集中在：

- 情绪状态机（EmotionStateMachine）
- 关系状态模型（RelationshipModel）
- 记忆选择器（MemorySelector）
- PromptBuilder（Prompt 组装引擎）
- PulsePolicy（生命脉冲策略）
- Action 映射与分发
- CompanionRuntime 主循环编排

---

## 八、最终依赖清单

### 必选依赖

```kotlin
// === UI (Compose) ===
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.navigation:navigation-compose
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.activity:activity-compose

// === 异步 ===
org.jetbrains.kotlinx:kotlinx-coroutines-android

// === DI (Hilt) ===
com.google.dagger:hilt-android
androidx.hilt:hilt-navigation-compose
androidx.hilt:hilt-work

// === 数据库 (Room) ===
androidx.room:room-runtime
androidx.room:room-ktx
androidx.datastore:datastore-preferences

// === 序列化 ===
org.jetbrains.kotlinx:kotlinx-serialization-json

// === 后台任务 ===
androidx.work:work-runtime-ktx

// === 媒体 / UI 资源 ===
com.airbnb.android:lottie-compose
io.coil-kt:coil-compose

// === 相机 ===
androidx.camera:camera-core
androidx.camera:camera-camera2
androidx.camera:camera-lifecycle
androidx.camera:camera-view

// === 日志 ===
com.jakewharton.timber:timber

// === Agent Framework (Koog) ===
ai.koog:koog-agents:0.8.0
```

### 测试依赖

```kotlin
// Unit Test
junit:junit
app.cash.turbine:turbine          // Flow 测试
io.mockk:mockk                   // Mock 框架

// Instrumented Test
androidx.test.ext:junit
androidx.test.espresso:espresso-core
```

> **不再需要的依赖：** Ktor Client（LLM 网络层由 Koog 内部处理）。如果后续有非 LLM 的网络需求（如 analytics 上报），按需单独引入。

---

## 九、参考链接

### Android 生态

- [Jetpack Compose](https://developer.android.google.cn/jetpack/compose)
- [Room 持久化库](https://developer.android.google.cn/jetpack/androidx/releases/room)
- [DataStore](https://developer.android.google.cn/topic/libraries/architecture/datastore)
- [WorkManager](https://android-docs.cn/develop/background-work/background-tasks/persistent/getting-started)
- [Hilt + Jetpack 集成](https://developer.android.google.cn/training/dependency-injection/hilt-jetpack)
- [CameraX](https://developer.android.google.cn/media/camera/camerax)
- [Gradle Version Catalog](https://developer.android.google.cn/build/migrate-to-catalogs)
- [Build Variant](https://developer.android.google.cn/build/build-variants)

### Agent / LLM

- [Koog (GitHub)](https://github.com/jetbrains/koog)
- [Koog 官方文档](https://docs.koog.ai/)
- [Anthropic Messages API 文档](https://docs.anthropic.com/en/api/messages)
- [Anthropic Vision（多模态）](https://docs.anthropic.com/en/docs/vision)
- [Anthropic Tool Use（结构化输出）](https://docs.anthropic.com/en/docs/tool-use)
- [Anthropic Streaming](https://docs.anthropic.com/en/api/streaming)
- [GLM-5v-turbo（智谱 AI）](https://open.bigmodel.cn/)
- [Kimi 2.6（Moonshot AI）](https://platform.moonshot.cn/)
