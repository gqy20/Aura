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
