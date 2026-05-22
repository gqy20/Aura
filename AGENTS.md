# Android 项目 - 开发环境

## 当前项目状态

- 项目名称：Aura · 奥拉（Android AI 陪伴应用）
- 当前阶段：**文本聊天技术闭环 / Phase 1 agent tools**
- 已实现：Compose 聊天页、`ChatViewModel`、`CompanionRuntime`、Koog `AIAgent` 流式调用、Room/DataStore/Hilt 基础设施、Agent tools（记忆/情绪/关系）
- 部分实现：模型配置、Vision 底层输入结构、情绪与关系核心、记忆注入
- 尚未实现：设置页、CameraX UI、语音、WorkManager pulse、通知、角色主屏、记忆房间
- 详细进度见：`docs/roadmap.md`

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

以上 Gradle 命令已在 2026-05-16 验证通过。

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
| Kotlin | **2.3.21**（由 Gradle plugin 管理） |
| AGP | **9.2.0** |
| SDK Root | `C:\Users\gqy17\AppData\Local\Android\Sdk` |
| SDK Platform | android-36 / android-36.1 |
| Build Tools | 37.0.0 / 36.1.0 |
| Git | 2.50.0 |
| Node.js | v22.14.0 |
| Python | 3.12.8 |

> **注意**: `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 需设置为 `C:\Users\gqy17\AppData\Local\Android\Sdk`

## 关键文档

- `README.md` — 项目概览、当前能力、构建命令
- `docs/roadmap.md` — 当前进度、里程碑、近期建议顺序
- `docs/architecture.md` — 架构目标与当前实现状态
- `docs/koog-android-integration.md` — Koog Android 集成状态与注意事项
- `docs/engineering-standards.md` — 测试、CI、代码规范

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
'@ | python -
```

排查重点：

- `messages` 有用户消息但没有 assistant：通常是 Runtime/LLM 失败或发送前配置失败。
- `messages` 里 assistant 只有 `[mood:...]` 标签：模型输出了结构标签但正文为空，需要看 parser/兜底逻辑。
- `tool_calls` 只有 `search_memory` 且结果 `count:0`：说明模型查了记忆但没有写入。
- `memories` 没有对应内容：说明后处理抽取或保存没有命中。
