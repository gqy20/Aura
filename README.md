<div align="center">

<img src="docs/brand/aura-logo.svg" width="120" alt="Aura · 奥拉" />

# Aura · 奥拉

**一个有情绪、有记性的 Android AI 陪伴 App。**

会记住几个月前你说过的烦心事，会感知到你今天语气不太好，
会隔一段时间主动找你聊聊。不是聊天机器人，更像一个老朋友。

[官网](https://aura.gqy20.top) · [Releases](https://github.com/gqy20/Aura/releases) · [架构文档](docs/architecture.md) · [Roadmap](docs/roadmap.md)

</div>

<div align="center">

[![Website](https://img.shields.io/badge/%E5%AE%98%E7%BD%91-aura.gqy20.top-7B6EF6?style=flat-square)](https://aura.gqy20.top)
[![Release](https://img.shields.io/github/v/release/gqy20/Aura?include_prereleases&style=flat-square&label=%E7%89%88%E6%9C%AC&color=blue)](https://github.com/gqy20/Aura/releases)
[![CI](https://img.shields.io/github/actions/workflow/status/gqy20/Aura/ci.yml?style=flat-square&logo=github-actions&label=CI)](https://github.com/gqy20/Aura/actions)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)]()
[![License](https://img.shields.io/badge/License-Private-red?style=flat-square)]()

</div>

## 它能做什么

- **长期记忆** — 跨会话记住你的偏好、习惯、重要的人和事
- **情绪感知** — 识别你当下的心情，对话风格随之调整
- **关系建模** — 认识得越久，越懂你的说话方式
- **图片理解** — 可以发图，能记住画面里的内容
- **主动陪伴** — 到点提醒喝水、休息，偶尔主动发起对话
- **本地可选** — 不想上云？可切换到本地 Qwen 模型，聊天数据不出手机

## 现在能用什么 / 还差什么

**已可用**：文本聊天、图片理解、记忆、情绪、提醒、设置
**还在路上**：语音对话、CameraX 拍照、主动推送、动画角色

完整进度见 [Roadmap](docs/roadmap.md)。

## 怎么用

### 安装

```bash
make run    # 构建 + 安装 + 启动
```

### 第一次启动

1. 进设置页填入 LLM Provider / API Key（云端），或下载本地模型
2. 回到主页，点底部 **+** 开始聊天
3. 发图：点输入框旁的图片按钮，从相册选

## 给开发者

### 环境

- JDK 21+
- Android SDK（compileSdk 36, minSdk 26）
- Android Studio（推荐）或 Gradle 命令行

### 常用命令

```bash
./gradlew assembleDebug       # 构建 Debug APK
./gradlew testDebugUnitTest   # 跑 372 个单元测试
make run                       # 构建 + 安装 + 启动
make logcat                    # 看日志
make help                      # 查看所有 make 命令
```

### 技术栈

| 层 | 选型 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 并发 | Coroutines + Flow |
| Agent | [Koog](https://github.com/JetBrains/koog) 0.8.0 |
| LLM | GLM-5v-turbo / Kimi 2.6 / 本地 Qwen (MNN) |
| 数据 | Room + DataStore |
| DI | Hilt |
| 后台 | WorkManager |

### 核心流程

```
用户输入（文本 / 图片）
  → 检索相关记忆与摘要
  → 组装 Prompt（注入情绪 + 关系 + 记忆）
  → Koog Agent 调用 LLM（流式输出，可用工具）
  → 解析响应
  → 保存消息 / 更新情绪 / 更新关系 / reflection 写入新记忆
  → UI 渲染
```

### 项目结构

```
app/src/main/java/com/xiaoqi/companion/
├── feature/        # UI 层（聊天、设置、首页、记忆室、引导）
├── core/           # 核心逻辑（Agent、情绪、关系、Prompt）
│   ├── companion/  #   CompanionRuntime 主循环 + Koog 集成
│   ├── llm/        #   Anthropic Messages 兼容 LLM client
│   ├── prompt/     #   Prompt 组装引擎 + 模板
│   └── tools/      #   Agent tools
├── data/           # 数据层（Room、DataStore、Repository）
└── di/             # Hilt 模块
```

完整架构见 [docs/architecture.md](docs/architecture.md)。

## 文档

- [技术架构](docs/architecture.md) — 分层设计、Agent Core、模块清单
- [Roadmap](docs/roadmap.md) — M0-M6 里程碑进度
- [工程化规范](docs/engineering-standards.md) — CI/CD、测试、代码规范
- [Koog API 参考](docs/koog-api-reference.md) — Koog 0.8.0 完整 API 签名

## License

Private
