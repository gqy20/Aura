# Aura · 奥拉

一款具有情绪感知和关系记忆的 Android AI 陪伴应用。

核心理念：**自研"灵魂"，外包"器官"** — 情绪状态机、关系模型、Prompt 引擎等创新能力全部自研，基础设施依托 Android/Kotlin 生态成熟组件。

## 特性

- **情绪系统** — 多维情绪状态机，支持实时衰减与触发
- **关系模型** — 亲密度等级随交互自然演进
- **多模态** — CameraX 视觉输入 + GLM-5v-turbo Vision 图片理解
- **模型可切换** — GLM-5v-turbo（默认）/ Kimi 2.6，统一 Anthropic Messages API 协议
- **流式对话** — Koog Agent 驱动的 SSE 流式输出，逐字渲染
- **记忆持久化** — Room 存储消息、记忆、情绪快照等
- **生命脉冲** — WorkManager 后台调度，离线期间状态衰减与主动关怀

## 技术栈

| 层面 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 并发 | Coroutines + Flow |
| Agent 框架 | [Koog](https://github.com/JetBrains/koog) (JetBrains) |
| LLM | GLM-5v-turbo (智谱) / Kimi 2.6 (月之暗面) |
| DI | Hilt |
| 数据库 | Room |
| 配置存储 | DataStore |
| 后台任务 | WorkManager |
| 相机 | CameraX |
| 动画 | Lottie / Coil |

## 项目结构

```
app/src/main/java/com/xiaoqi/companion/
├── feature/                  # UI 层 — 聊天、设置等功能模块
├── core/                     # 核心逻辑 — Agent 运行时、情绪、关系、Prompt
│   ├── companion/            #   CompanionRuntime 主循环 + Koog 集成
│   ├── prompt/               #   Prompt 组装引擎 + 模板
│   ├── emotion/              #   情绪状态机
│   └── relationship/         #   关系亲密度模型
├── data/                     # 数据层 — Room DAO/Entity、DataStore、Repository
│   ├── db/
│   ├── datastore/
│   └── repository/
├── platform/                 # 平台能力 — 语音、相机、通知、权限
└── di/                       # Hilt 依赖注入模块
```

## 快速开始

### 环境要求

- JDK 21+
- Android SDK（compileSdk 36.1, minSdk 26）
- Android Studio (推荐) 或 Gradle 命令行

### 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

### Makefile 快捷命令

```bash
make build       # 构建 Debug APK
make release     # 构建 Release APK
make test        # 运行单元测试
make run         # 构建 + 安装 + 启动
make devices     # 列出已连接设备
make logcat      # 查看应用日志
make help        # 查看所有命令
```

### 测试

```bash
# 单元测试
./gradlew testDebugUnitTest

# 仪器测试（需连接设备/模拟器）
./gradlew connectedDebugAndroidTest
```

## Agent 核心流程

```
用户输入 (文本/图片/语音)
  → PromptBuilder 组装（注入情绪 + 关系 + 记忆上下文）
    → Koog Agent 调用 LLM（流式输出）
      → OutputParser 解析结构化响应
        → 情绪状态机更新
        → 关系模型更新
        → 记忆存储
        → UI 响应（表情 / 气泡 / 动作）
```

## 配置

LLM 模型配置通过 DataStore 管理，在设置页切换：

```kotlin
data class LlmConfig(
    val provider: LlmProvider = LlmProvider.GLM,
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v1",
    val apiKey: String,
    val modelName: String = "glm-5v-turbo",
)
```

## 文档

- [技术架构文档](docs/architecture.md) — 完整的分层设计、LLM 选型、Agent Core 流程
- [工程化规范](docs/engineering-standards.md) — CI/CD、测试策略、代码规范、质量门禁

## License

Private
