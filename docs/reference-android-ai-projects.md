# Android AI 聊天/陪伴应用 — 开源项目参考

> 本文档整理了与当前项目（companion: Kotlin + Compose + Hilt + Room + Anthropic API）技术栈相似、架构优雅的开源项目，供开发时参考借鉴。
>
> 截至 2026-06-16 仍为有效参考。期间未补充新调研（端侧推理方向可参考 `docs/on-device-qwen-mnn-research.md` 中的 PocketPal 调研）。

---

## 1. Operit — 最强大的 Android AI Agent

| 属性 | 值 |
|------|-----|
| **仓库** | https://github.com/AAswordman/Operit |
| **Stars** | 4,562 |
| **协议** | GPL-3.0 |
| **最后更新** | 2026-05-12 |
| **minSdk** | 26 / **targetSdk** | 34 |
| **JDK** | 17 |

### 技术栈

Kotlin + Compose (Material3) + Room + ObjectBox + DataStore + OkHttp/SSE + HNSW 向量搜索

**AI 引擎：**
- 端侧推理：llama.cpp / MNN (CMake NDK, arm64-v8a)
- 云端 API：多模型支持
- MCP 协议集成 (`io.modelcontextprotocol.sdk:mcp:1.1.0`)

**其他亮点：**
- 插件系统 (`plugins/`)
- 终端模拟器 (`terminal/`, QuickJS)
- 3D 渲染 (Google Filament glTF)
- 文档处理 (Apache POI, PDFBox, iText)
- Shizuku / Tasker 集成
- 桌面小部件 (Glance)

### 架构分层

```
app/src/main/java/com/ai/assistance/operit/
├── api/              # API 接口定义
├── core/             # 核心业务逻辑
├── data/
│   ├── api/          # 网络层实现
│   ├── dao/          # Room 数据库访问
│   ├── db/           # 数据库定义与迁移
│   ├── model/        # 数据模型 (Entity)
│   ├── repository/   # Repository 层
│   ├── skill/        # 技能/插件数据
│   ├── mcp/          # Model Context Protocol
│   ├── preferences/  # DataStore 封装
│   └── ...
├── integrations/     # 第三方服务集成
├── plugins/          # 插件系统
├── provider/         # ContentProvider
├── services/         # Android Service
├── ui/               # Compose UI 层
├── widget/           # Glance AppWidget
└── util/             # 工具类
```

### 与当前项目的对比

| 维度 | companion (我们的) | Operit |
|------|-------------------|--------|
| 定位 | AI 情感陪伴 | 全能 AI Agent |
| 架构 | 单模块 Clean Architecture | 单模块多层 + 子模块 (llama/mnn/terminal 等) |
| DI | Hilt | 手动组装 / Dagger |
| 数据库 | Room | Room + ObjectBox |
| 网络 | Koog Agent 框架 | OkHttp + SSE |
| AI | Anthropic API (云端) | 端侧 llama.cpp + 多云端 API |
| 测试 | TDD (MockK + Robolectric) | Mockito/MockK |

**值得借鉴：**
- `data/repository/` 的抽象模式 — 将 DAO 和网络层统一为 Repository
- `data/skill/` 插件化思路 — 如果未来扩展 AI 能力
- MCP 协议集成方式 — 对接外部工具的标准方案
- 端侧推理子模块组织 (`llama/`, `mnn/`) — NDK CMake 项目结构

---

## 2. skydoves/chatgpt-android — 多模块架构典范

| 属性 | 值 |
|------|-----|
| **仓库** | https://github.com/skydoves/chatgpt-android |
| **Stars** | 3,870 |
| **作者** | Jaewoong Eum (skydoves) — Android 社区知名开发者 |
| **协议** | Apache 2.0 |
| **最后更新** | 2026-05-12 |

### 技术栈

Kotlin + Compose + Hilt + Stream Chat SDK + Firebase (Analytics/Crashlytics/Messaging) + Retrofit + Landscapist (图片加载)

### 多模块架构（核心亮点）

```
project root/
├── app/                    # Application shell（不含业务逻辑）
├── build-logic/            # Gradle 约定插件 (Convention Plugins)
├── buildSrc/               # 共享依赖版本目录 (Version Catalog)
├── core-data/              # 数据层抽象
├── core-designsystem/      # 设计系统 (Theme/Color/Typography/Components)
├── core-model/             # 共享数据模型 (跨模块传递)
├── core-navigation/        # 导航逻辑封装
├── core-network/           # 网络层 (Retrofit/OkHttp)
├── core-preferences/       # DataStore 封装
├── feature-chat/           # 聊天功能模块
├── feature-login/          # 登录功能模块
├── benchmark/              # Baseline Profile (启动优化)
└── spotless/               # 代码格式化配置
```

### 工程实践亮点

- **Convention Plugins** (`build-logic/`) — 用 Gradle 自身管理构建配置，避免重复脚本
- **Version Catalog** (`buildSrc/libs.versions.toml`) — 统一依赖版本管理
- **Baseline Profile** — 编译期生成启动优化配置，减少冷启动时间
- **Spotless** — 自动代码格式化，保证团队风格一致
- **Design System 独立模块** — UI 组件和主题与业务解耦

### 与当前项目的对比

| 维度 | companion | chatgpt-android |
|------|-----------|-----------------|
| 模块数 | 单模块 | **多模块** (core-* + feature-*) |
| DI | Hilt | Hilt |
| 导航 | Compose Navigation | **core-navigation** 封装 |
| 设计系统 | 内嵌 theme/ | **独立 core-designsystem** 模块 |
| 构建 | 标准 build.gradle.kts | **Convention Plugin** |
| 性能优化 | — | **Baseline Profile** |

**值得借鉴：**
- 多模块拆分策略 — 当项目增长后按 `core-` / `feature-` 拆分
- `core-designsystem` 独立 — Theme/Colors/Types 单独成模块
- Convention Plugin — 减少各模块 build.gradle.kts 重复配置
- Baseline Profile — 启动性能优化的标准做法

---

## 3. gpt_mobile — 多 LLM 客户端（最接近当前项目）

| 属性 | 值 |
|------|-----|
| **仓库** | https://github.com/Taewan-P/gpt_mobile |
| **Stars** | 1,109 |
| **协议** | GPL-3.0 |
| **minSdk** | 31 / **targetSdk** | 36 |
| **JDK** | 17 |

### 技术栈

Kotlin + Compose (Material3) + **Hilt** + **Ktor** (CIO engine) + **Room** + **DataStore** + Kotlinx Serialization

### 支持的 AI 提供商

OpenAI (GPT-4o/o1/o3) · Anthropic Claude · Google Gemini · DeepSeek R1 · xAI Grok · Mistral · Groq · OpenRouter · Ollama / LM Studio (本地)

### 架构分层

```
dev.chungjungsoo.gptmobile/
├── data/
│   ├── context/       # 应用上下文 / 会话管理
│   ├── database/      # Room 数据库 (Entity + Dao)
│   ├── datastore/     # DataStore Preferences
│   ├── dto/           # 数据传输对象 (API 请求/响应)
│   ├── model/         # 领域模型 (clean conversion from DTO)
│   ├── network/       # Ktor HttpClient + SSE 处理
│   └── repository/    # Repository 实现 (协调 data sources)
├── di/                # Hilt @Module / @Provides
├── presentation/      # ViewModel + Compose Screen
└── util/              # 扩展函数 / 工具类
```

### 关键设计决策

- **Ktor 替代 Retrofit** — 更轻量，原生支持 SSE 流式响应，Coroutine 友好
- **DTO → Model 转换** — data/dto 和 data/model 分离，clean architecture 实践
- **context 包** — 管理对话上下文窗口，对 LLM 应用很关键
- **AboutLibraries** — 自动生成第三方依赖声明页

### 与当前项目的对比

| 维度 | companion | gpt_mobile |
|------|-----------|------------|
| 定位 | AI 情感陪伴 | 多 LLM 通用客户端 |
| DI | **Hilt** | **Hilt** |
| 数据库 | **Room** | **Room** |
| 配置存储 | DataStore | **DataStore** |
| 网络 | Koog Agent | **Ktor (CIO)** |
| UI | Material3 Compose | **Material3 Compose** |
| 序列化 | kotlinx.serialization | **kotlinx.serialization** |
| 测试 | TDD (MockK) | JUnit (较轻) |
| minSdk | 较低 | **31** (较新) |

**相似度最高** — 几乎完全相同的技术栈选择。直接可参考：
- Ktor 网络层的封装方式（特别是 SSE 流式）
- 多 LLM Provider 的抽象/切换机制
- Room database schema 组织
- Hilt module 的组织方式

---

## 4. 其他值得关注的项目

### GetStream/gemini-android (388★)
- **仓库**: https://github.com/GetStream/gemini-android
- **特点**: Google Gemini + Stream Chat SDK for Compose，官方示例级质量
- **适合**: 学习 Chat SDK 集成模式和流式渲染

### lambiengcode/compose-chatgpt-kotlin (261★)
- **仓库**: https://github.com/lambiengcode/compose-chatgpt-kotlin-android-chatbot
- **特点**: Compose + MVVM + Retrofit + OpenAI GPT-3 流式响应
- **适合**: 入门参考，结构简单清晰

### android-ai GitHub Topic
- **链接**: https://github.com/topics/android-ai
- **内容**: 含端侧 LLM 方向（llama.cpp on Android）、隐私优先的本地推理应用

---

## 总结：参考优先级

```
需求场景                     推荐参考项目
─────────────────────────────────────────────
网络层 / 多 LLM 抽象    →   gpt_mobile (最接近)
多模块拆分 / 架构升级    →   skydoves/chatgpt-android
插件系统 / 端侧推理     →   Operit
Chat SDK 集成模式       →   GetStream/gemini-android
入门级简单示例          →   compose-chatgpt-kotlin
```

---

*文档生成日期: 2026-05-12 · 最后核对: 2026-06-16*
