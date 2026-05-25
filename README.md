# Aura · 奥拉

一款具有情绪感知和关系记忆的 Android AI 陪伴应用。

核心理念：**自研"灵魂"，外包"器官"** — 情绪状态机、关系模型、Prompt 引擎等创新能力全部自研，基础设施依托 Android/Kotlin 生态成熟组件。

## 特性

- **当前版本：0.1.3** — 发布与稳定性增强版本，聚焦响应链路稳定、结构化记忆写入、远程工具/定位/天气/提醒诊断日志和 release 发布流程
- **已实现：文本聊天闭环** — Compose 聊天页 + ChatViewModel + CompanionRuntime + Koog Agent
- **已实现：流式对话** — Anthropic Messages 兼容 SSE 流式输出，聊天气泡逐字渲染
- **已实现：长期记忆体系增强** — Room 存储消息、记忆、摘要、情绪快照、工具调用记录；`MemoryRepository` 统一保存、搜索、prompt selection 和访问时间更新；回复完成后由 LLM reflection 判断是否写入记忆
- **已实现：Agent 工具调用** — 只读上下文工具、记忆搜索、摘要搜索、设备/时间/天气/提醒与远程 MCP 工具；记忆/情绪/关系写入已从工具阶段移到后置系统阶段
- **已实现：情绪与关系核心** — 情绪状态机和关系模型已接入 Agent 主循环
- **已实现：模型与工具设置** — 设置弹层支持 provider、模型名、Base URL、API Key、MCP HTTP URL 与上下文工具开关
- **部分实现：多模态** — 图片选择、Vision prompt 和底层图片输入已接入；Vision 主回复禁用 tools，但支持后置 reflection 记忆整理
- **规划中：生命脉冲** — WorkManager 依赖已接入，后台 pulse/通知/主动关怀尚未实现

## 技术栈

| 层面 | 选型 |
|------|------|
| JDK | 21 |
| 语言 | Kotlin 2.3.21 |
| Android Gradle Plugin | 9.2.0 |
| SDK | compileSdk 36 / minSdk 26 / targetSdk 36 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 并发 | Coroutines + Flow |
| Agent 框架 | [Koog](https://github.com/JetBrains/koog) 0.8.0 |
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
├── feature/                  # UI 层 — 当前已实现 chat
├── core/                     # 核心逻辑 — Agent 运行时、情绪、关系、Prompt
│   ├── companion/            #   CompanionRuntime 主循环 + Koog 集成
│   ├── llm/                  #   Anthropic Messages 兼容 LLM client / executor
│   ├── prompt/               #   Prompt 组装引擎 + 模板
│   ├── tools/                #   Agent tools + 工具调用记录
│   └── logging/              #   Timber 日志封装与字段脱敏
├── data/                     # 数据层 — Room DAO/Entity、DataStore、Repository
│   ├── db/
│   ├── datastore/
│   └── repository/
└── di/                       # Hilt 依赖注入模块
```

> `platform/`、`feature/settings`、`feature/memory_room`、`core/pulse` 仍属于 roadmap 中的计划模块，当前源码目录尚未落地。

## 快速开始

### 环境要求

- JDK 21+
- Android SDK（compileSdk 36, minSdk 26）
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
  → MemoryRepository 选择相关记忆/摘要
  → PromptBuilder 组装（注入情绪 + 关系 + 记忆上下文）
    → Koog Agent 调用 LLM（流式输出，可用只读/外部工具）
      → OutputParser 解析结构化响应
        → 保存 assistant 消息
        → 情绪状态机更新
        → 关系模型更新
        → LLM reflection 判断并写入值得保留的记忆
        → UI 响应（表情 / 气泡 / 动作 / 已记住提示）
```

当前已跑通文本输入链路和图片选择 Vision 输入链路；文本与 Vision 场景都会先完成主回复，再用后置 reflection 判断是否保存记忆。语音输入和 CameraX 拍摄 UI 仍在 roadmap 中。

## 配置

LLM 模型配置通过 DataStore 与 BuildConfig 管理。可以从 `.env` / 环境变量读取默认值，也可以在应用内设置弹层切换 provider、模型名、Base URL 和 API Key：

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
- [Roadmap](docs/roadmap.md) — 当前进度、下一阶段任务和里程碑

## License

Private
