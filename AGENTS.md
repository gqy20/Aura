# Android 项目 - 开发环境

## 当前项目状态

- 项目名称：Aura · 奥拉（Android AI 陪伴应用）
- 当前阶段：**文本聊天技术闭环 / Phase 1 agent tools**，已进入 Phase 2+ 的 Presence Layer / 本地 LLM / Reminder 系统雏形
- 已实现（按代码核对）：Compose 聊天页 + `AuraHomeScreen` 角色主屏、`ChatViewModel`、`CompanionRuntime`、Koog `AIAgent` 流式调用；Room/DataStore/Hilt；Agent tools（记忆/情绪/关系/Health/Insight/Reminder）；**工具系统双开关**（`mcpEnabled` MCP 总开关 + `systemToolsEnabled` 系统内置工具开关，`CompanionToolRegistry.create()` 按开关短路，与 per-server `enabled` 是"总闸 vs 分闸"关系）；**MCP 渐进加载**（`McpServerRouter` 按请求选择服务器、`McpToolSelector` 在服务器内裁剪工具，tool spec 缓存、失败冷却与选择指标）；**LLM 工具协议兼容**（Anthropic SSE 按 `content_block.index` 并行累积工具调用、扁平 JSON 裸字符串保守修复、HTTP 错误体脱敏摘要与请求结构指纹、Koog `Failure`/`ValidationError` 结果终止错误循环）；NavHost 六条路由（Home/Chat/Settings/McpSettings/MemoryRoom/Onboarding）；`SettingsScreen` + `McpSettingsScreen`；`MemoryRoomScreen`；`OnboardingScreen`；`PresenceController` + `PresenceReactionPolicy`（状态推导逻辑）；Reminder 模块（AlarmManager + `ReminderNotificationWorker` + `ReminderNotificationPoster`）；本地 LLM 链路（`LocalQwenEngine` / `MnnLocalQwenEngine` / `NativeMnnLlmBridge` / `LocalQwenModelDownloader`）；Memory Summary（DAO/Entity + `SearchSummariesTool`）；`LlmConnectivityChecker`；`DataTransparencySection`；`HealthSyncManager` / `HealthDataSection`；**Insight 双路径**（`DreamLoopWorker` 6h 周期 + `ChatViewModel` POST_CHAT 3min 冷却即时触发）；**EvidenceResolver** 中文 FTS + 兜底策略；**InsightValidator** 低门槛（confidence 0.2 / evidence reality 10%）；POST_CHAT 卡片 UI（蓝色徽标 + 分析中指示器）
- 部分实现：Vision 以 Photo Picker 选图为 MVP，CameraX 拍摄 UI 仍缺；情绪与关系的头像/表情层由 Compose Canvas 临时替代；Presence Layer 的 Rive/Lottie 动画资源仍缺；Pulse 的离线衰减/回归反应/主动通知仍缺
- 尚未实现：`SpeechRecognizer`/`TextToSpeech` 语音 I/O、`PulseWorker`（仅 reminder 使用 OneTimeWorkRequest）、Rive/Lottie 状态机动画、Instrumented UI 测试、CI 工作流、远程 Agent Server / `RemoteAgentRuntime`
- 详细进度见：`docs/roadmap.md`
- 验证日期：`./gradlew.bat testDebugUnitTest` 于 2026-08-08 通过（**624 个测试，0 失败**；含 MCP 渐进加载、SSE 多工具索引累积、工具参数兼容、Koog 错误结果短路、地图交互与既有 DAO/Insight/ChatViewModel 测试。Robolectric 单 fork 复用）

## 常用验证命令

优先使用 Makefile 作为日常入口；Windows 下建议在 Git Bash / WSL 中运行：

```bash
make test
make build
make release
make check
make test-fast   # 跳过 Robolectric,只跑纯 JVM 测试(~10s)
make test-db     # 只跑 DAO/Repo
make test-one T=CompanionRuntimeTest
```

其中：

- `make test`：运行 debug 单元测试（全量 ~60-90s,缓存后 ~20s）。
- `make test-fast`：开发期迭代,跳过 DAO/UI/Downloader。
- `make test-db`：修改 schema / DAO 查询后验证。
- `make test-one T=<类名>`：单一类。
- `make build`：构建 debug APK。
- `make release`：构建 release APK。
- `make check`：运行 lint + test + debug build 级别检查。

如果当前 shell 是 PowerShell，或 `make` 不可用，再直接使用 Gradle Wrapper：

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat build
```

以上 Gradle 命令已在 2026-08-08 验证通过（`testDebugUnitTest` **624 个测试全绿**）。测试性能优化详见 CLAUDE.md "并发配置：单 fork 复用 Robolectric Runtime" 段落。

## MNN Benchmark 流程

- `make benchmark-mnn` 走主 App 进程 benchmark，不走 androidTest。
- 标准顺序：
  1. `./gradlew.bat assembleDebug`
  2. `D:\tools\ADB_Cli\adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`
  3. `D:\tools\ADB_Cli\adb.exe shell am start ...` 触发 `MainActivity`
  4. `run-as com.xiaoqi.companion.debug` 拉取 `files/benchmarks/local-qwen-benchmark.json`
- `scripts/mnn_benchmark.yml` 维护 benchmark 默认参数，常改字段是 `apk_path`、`app_package`、`model_name`、`prompt_len`、`decode_len`、`warmup_runs`、`measure_runs`。

## 工具路径与运行时环境

> 此节内容与 [`CLAUDE.md`](CLAUDE.md) 同步。**只在一处更新，然后同步到另一处**；如有冲突，以 CLAUDE.md 为准。
>
> 包含：ADB_Cli / Android Studio / Android_Studio_Cli 路径，以及 JDK 21.0.6 / Kotlin 2.3.21 / AGP 9.2.0 / SDK Platform android-36 / Build Tools 37.0.0 / Git 2.50.0 / Node.js v22.14.0 / Python 3.12.8 等版本信息，`ANDROID_HOME` 需设为 `C:\Users\gqy17\AppData\Local\Android\Sdk`。

## 关键文档

- `README.md` — 项目概览、当前能力、构建命令
- `docs/roadmap.md` — 当前进度、里程碑、近期建议顺序
- `docs/architecture.md` — 架构目标与当前实现状态
- `docs/koog-api-reference.md` — Koog v0.8.0 完整 API 签名（从 Gradle 缓存 JAR 提取），Agent/Builder/Strategy/Service/Tool/Pipeline/Prompt&LLM 等 15 个模块
- `docs/koog-android-integration.md` — Koog Android 集成状态与注意事项（线程规则、生命周期模式、待补项）
- `docs/engineering-standards.md` — 测试、CI、代码规范
- `docs/reference-android-ai-projects.md` — 同类 Android AI 聊天/陪伴开源项目调研（Operit / skydoves-chatgpt-android / gpt_mobile 等）

## 注释规范

> 与 [`CLAUDE.md`](CLAUDE.md) 同步：以下条目在两处都有，但权威定义以 CLAUDE.md 为准。修改时同时改两处。

写代码注释的边界：**信息量 ≥ 1 行的非显然决策才写**，否则让代码自解释。

### 不写

- **步骤编号注释**：`// 1. xxx` / `// 2. yyy` 给 `init {}` 或函数体编号。代码顺序已自表达，直接删。
- **复述字面代码**：`if (line.startsWith("#"))` 上不写 `// Skip comments`；`key == "sections"` 上不写 `// "sections:" opener`。条件表达式本身就是文档。
- **考古/历史注释**：`// 方案 A` / `// 原行为只跑 markClicked` / `// 之前 dismissButton 放关闭` —— git blame 里有，删。
- **空头 TODO**：`// 未来 v2 baseline 持久化后，会写 7 天的 diff` / `// Phase 1 拆开后这条分支应改` —— 应开 issue，不在源码里埋雷。
- **过度解释防御性代码**：`runCatching { ... }` 上不写 3 行解释"为什么用 runCatching"。
- **1 行废话**：`// 同步本地 count` / `// 1) 大段文本(>= 48 chars)→ 立即 flush` 紧跟字面代码。

### 压到 1-2 行

- 5 行布局解释 → 1 行（`// Text 显式 height(48.dp) + wrapContentHeight 让 glyph 视觉中心对齐外 Row`）。
- 3 行英文注释 → 1 行中文（`// 中心裁剪：保留前 1/3 + 后 2/3，丢中间`）。
- `when` 5 个分支每个加章节标签 → 全删，表达式自解释。

### 保留

- **跨文件协议/契约引用**：`CreateLocalReminderTool.kt:122 不走 envelope，直接 buildJsonObject` / `魔搭 (ModelScope) Inference 端点，兼容 Anthropic Messages 协议：鉴权用 x-api-key，请求路径 ${baseUrl}/v1/messages`。
- **hack 原因**：`// contract 自身也走 enforceValidPackage，在 realme 上不可靠，需 fallback` —— 不写会让人重蹈覆辙。
- **非显然决策**：`// Don't increment i; fall through to process lines[i] as normal` —— 解释为什么不 ++i，防止重写时误加。
- **1 行简明信号**：`// 本地模型走 MNN，无网络依赖，直接视为可达` / `// 任何 source 跑成功 = 整体成功`。

### 章节标题密度

`// --- xxx ---` 风格密度太高（> 5 个）时合并：Converters.kt 10 个标题 → 1 个；`when` 5 个分支每个加标题 → 全删，表达式自解释。`//region xxx` 大块分隔（> 30 行）可保留作导航。

## ADB 日志与本地数据排查

协议兼容问题先用脚本和 JVM 测试定位，避免每轮都启动模拟器：

```powershell
.\scripts\llm_tool_protocol_probe.ps1 -Provider GLM -Scenario All
.\scripts\llm_tool_protocol_probe.ps1 -Provider MODELSCOPE -Scenario All
.\gradlew.bat testDebugUnitTest --tests "com.xiaoqi.companion.core.llm.AnthropicMessagesLLMClientTest"
```

脚本负责比较 baseline、单/双工具流和 tool-result follow-up，并输出 SSE 事件类型与 `index`，不输出 API Key。MockWebServer 测试负责固定复现交错工具块、畸形参数、错误体和重试。只有脚本与单测通过后，才安装 APK 做一次真实地图 MCP 端到端验收。

调试手机上“发了消息但 UI/记忆不对”时，再确认当前前台包、进程、logcat 和 Room 数据库。debug 包名通常是 `com.xiaoqi.companion.debug`，release 包名是 `com.xiaoqi.companion`。

### 1. 确认设备、前台包和进程

```powershell
& 'D:\tools\ADB_Cli\adb.exe' devices
& 'D:\tools\ADB_Cli\adb.exe' shell dumpsys window | Select-String -Pattern 'mCurrentFocus|mFocusedApp|topResumedActivity'
& 'D:\tools\ADB_Cli\adb.exe' shell dumpsys activity activities | Select-String -Pattern 'topResumedActivity|ResumedActivity|com.xiaoqi.companion'
& 'D:\tools\ADB_Cli\adb.exe' shell pidof com.xiaoqi.companion.debug
```

### 2. 查看日志

拿到 PID 后用 `--pid` 过滤当前 App。重点看 `pipeline_started`、`message_send_started`、`request_built`、`response_received`、`agent_error_received`、`pipeline_failed`、`AndroidRuntime`、`FATAL EXCEPTION`。

```powershell
$pid = (& 'D:\tools\ADB_Cli\adb.exe' shell pidof com.xiaoqi.companion.debug).Trim()
& 'D:\tools\ADB_Cli\adb.exe' logcat -d --pid=$pid -v time -t 1000
& 'D:\tools\ADB_Cli\adb.exe' logcat -d --pid=$pid -v time '*:W' -t 300
& 'D:\tools\ADB_Cli\adb.exe' logcat -d -v time -t 2000 |
  Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime|xiaoqi|companion|Aura|pipeline_|agent_error|LLM|Runtime|Chat|MCP|HTTP|Exception|ANR|crash'
```

如果没有看到业务日志，不代表用户没有发消息；继续查 Room 数据库。

### 3. 拉取 debug Room 数据库

`run-as` 只能用于 debuggable 包。PowerShell 普通 `>` 会把 SQLite 二进制写坏成 UTF-16，必须用 `cmd /c` 做二进制重定向，或用其他二进制安全方式。

```powershell
$out = 'D:\C\Desktop\ai\android\tmp-adb-db'
New-Item -ItemType Directory -Force -Path $out | Out-Null
cmd /c "D:\tools\ADB_Cli\adb.exe exec-out run-as com.xiaoqi.companion.debug cat databases/companion.db > D:\C\Desktop\ai\android\tmp-adb-db\companion.db"
cmd /c "D:\tools\ADB_Cli\adb.exe exec-out run-as com.xiaoqi.companion.debug cat databases/companion.db-wal > D:\C\Desktop\ai\android\tmp-adb-db\companion.db-wal"
cmd /c "D:\tools\ADB_Cli\adb.exe exec-out run-as com.xiaoqi.companion.debug cat databases/companion.db-shm > D:\C\Desktop\ai\android\tmp-adb-db\companion.db-shm"
Format-Hex -Path "$out\companion.db" | Select-Object -First 2
```

正常 SQLite 文件头应显示 `SQLite format 3`。如果开头是 `FF FE 53 00...`，说明被 PowerShell 文本重定向写坏了，需要重新拉取。

### 4. 查询最近消息、工具调用和记忆

```powershell
$env:PYTHONIOENCODING='utf-8'
@'
import sqlite3, datetime
path = r'D:\C\Desktop\ai\android\tmp-adb-db\companion.db'
conn = sqlite3.connect(path)
conn.row_factory = sqlite3.Row

def ms(ts):
    if ts is None:
        return None
    return datetime.datetime.fromtimestamp(ts / 1000).strftime('%Y-%m-%d %H:%M:%S')

def preview(value, limit=220):
    return (value or '').replace('\n', ' ')[:limit]

print('recent messages:')
for r in conn.execute('select id, session_id, role, content, timestamp, imageBase64 is not null as hasImage from messages order by timestamp desc limit 20'):
    d = dict(r)
    content = d.pop('content') or ''
    d['time'] = ms(d.pop('timestamp'))
    d['content_len'] = len(content)
    d['content_preview'] = preview(content)
    print(d)

print('\nrecent tool calls:')
for r in conn.execute('select id, sessionId, toolName, status, createdAt, completedAt, errorMessage, substr(argumentsJson,1,260) as args, substr(resultJson,1,260) as result from tool_calls order by createdAt desc limit 20'):
    d = dict(r)
    d['createdAt'] = ms(d['createdAt'])
    d['completedAt'] = ms(d['completedAt'])
    print(d)

print('\nrecent memories:')
for r in conn.execute('select id, type, source, importance, confidence, timestamp, updatedAt, substr(content,1,260) as content from memories order by updatedAt desc limit 20'):
    d = dict(r)
    d['timestamp'] = ms(d['timestamp'])
    d['updatedAt'] = ms(d['updatedAt'])
    print(d)
'@ | uv run python -
```

排查重点：

- `messages` 有用户消息但没有 assistant：通常是 Runtime/LLM 失败或发送前配置失败。
- `messages` 里 assistant 只有 `[mood:...]` 标签：模型输出了结构标签但正文为空，需要看 parser/兜底逻辑。
- `tool_calls` 只有 `search_memory` 且结果 `count:0`：说明模型查了记忆但没有写入。
- `memories` 没有对应内容：说明后处理抽取或保存没有命中。

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
