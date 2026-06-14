# Android 项目 - 开发环境

## 当前项目状态

- 项目名称：Aura · 奥拉（Android AI 陪伴应用）
- 当前阶段：**文本聊天技术闭环 / Phase 1 agent tools**，已进入 Phase 2+ 的 Presence Layer / 本地 LLM / Reminder 系统雏形
- 已实现（按代码核对）：Compose 聊天页 + `AuraHomeScreen` 角色主屏、`ChatViewModel`、`CompanionRuntime`、Koog `AIAgent` 流式调用；Room/DataStore/Hilt；Agent tools（记忆/情绪/关系）；NavHost 五条路由（Home/Chat/Settings/McpSettings/MemoryRoom）；`SettingsScreen` + `McpSettingsScreen`；`MemoryRoomScreen`；`PresenceController` + `PresenceReactionPolicy`（状态推导逻辑）；Reminder 模块（AlarmManager + `ReminderNotificationWorker` + `ReminderNotificationPoster`）；本地 LLM 链路（`LocalQwenEngine` / `MnnLocalQwenEngine` / `NativeMnnLlmBridge` / `LocalQwenModelDownloader`）；Memory Summary（DAO/Entity + `SearchSummariesTool`）
- 部分实现：模型配置（设置页已落地，连通性检查仍缺）、Vision（输入结构已支持，CameraX UI 仍缺）、情绪与关系（持久化与可视化已接入，头像层由 Compose Canvas 临时替代）、记忆（完整页面与 prompt 注入已实现，编辑/归档动作仍缺）、Presence Layer（状态控制器已落地，Rive/Lottie 动画资源仍缺）
- 尚未实现：CameraX 预览/拍照/选图流程、`SpeechRecognizer`/`TextToSpeech` 语音 I/O、`PulseWorker`（仅 reminder 使用 OneTimeWorkRequest，缺少离线衰减 / 回归反应 / 主动通知）、Rive/Lottie 状态机动画、Instrumented UI 测试、隐私/导出/删除控制、CI 工作流、远程 Agent Server / `RemoteAgentRuntime`
- 详细进度见：`docs/roadmap.md`
- 验证日期：`./gradlew.bat testDebugUnitTest` 于 2026-06-14 通过（41 个测试，0 失败）

## 常用验证命令

优先使用 Makefile 作为日常入口；Windows 下建议在 Git Bash / WSL 中运行：

```bash
make test
make build
make release
make check
```

其中：

- `make test`：运行 debug 单元测试。
- `make build`：构建 debug APK。
- `make release`：构建 release APK。
- `make check`：运行 lint + test + debug build 级别检查。

如果当前 shell 是 PowerShell，或 `make` 不可用，再直接使用 Gradle Wrapper：

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat build
```

以上 Gradle 命令已在 2026-06-14 验证通过（`testDebugUnitTest` 41 个测试全绿）。

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

## ADB 日志与本地数据排查

调试手机上“发了消息但 UI/记忆不对”时，优先确认当前前台包、进程、logcat 和 Room 数据库。debug 包名通常是 `com.xiaoqi.companion.debug`，release 包名是 `com.xiaoqi.companion`。

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
