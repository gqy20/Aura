# Aura Roadmap

> 最后核对：2026-05-15
>
> 本文档用于跟踪当前实现进度，并把 `README.md` / `docs/architecture.md` 中的产品愿景拆成可执行里程碑。

## 当前状态

项目当前处于 **文本聊天技术闭环 / Phase 1 agent tools** 阶段。

已验证命令：

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

以上命令均已在 2026-05-15 验证通过。

## 已实现

- 单模块 Android App：`:app`。
- Compose 聊天页，链路为 `MainActivity` -> `ChatScreen` -> `ChatViewModel`。
- `CompanionRuntime` 主流程：Prompt 构建、记忆注入、Koog 执行、输出解析、情绪更新、关系更新。
- Koog `AIAgent` 真实集成，支持流式文本事件。
- Anthropic Messages 兼容 LLM client，支持 SSE streaming、tool schema 序列化、底层图片 content 组装。
- Room 持久化：messages、memories、agent_state、mood_snapshots、tool_calls。
- DataStore 配置仓库：API key、provider、model name、theme mode。
- Agent tools：`save_memory`、`search_memory`、`update_mood`、`update_relationship`。
- App 启动时恢复 Room 中的聊天历史。
- 聊天页顶部展示当前情绪和关系状态。
- 情绪/关系状态写入 `agent_state`，App 重启后可恢复。
- 聊天页展示最近的长期记忆，只读可见。
- 只读记忆房间弹层可查看全部当前记忆。
- 聊天页显示模型配置状态；缺少 API Key/Base URL/model 时会禁用发送并给出明确提示。
- 聊天页提供模型设置弹层，可编辑 Provider、模型名称和本机 API Key。
- 单元测试覆盖 core runtime、prompt、parser、tools、DAO、repository、DataStore、ChatViewModel、消息 UI 等。
- Debug APK 构建链路。

## 部分实现

- **模型切换**：Repository/Config 基础能力、聊天页配置状态提示和设置弹层已存在；仍缺少独立设置页与连通性检查。
- **Vision**：`UserInput.Vision` 和 LLM client 图片 content 支持已存在，但 CameraX 拍照/选图 UI 尚未实现。
- **情绪与关系**：核心状态更新、持久化恢复和聊天页可视化已接入，但头像/表情层尚未完成。
- **记忆**：工具保存/搜索、prompt 注入、聊天页只读展示和只读记忆房间弹层已实现，但还没有完整记忆管理能力。
- **Release 构建**：ProGuard 与 debug 签名 fallback 已有，真实 release keystore 仍需验证。

## 尚未实现

- 设置页。
- 聊天页之外的导航。
- CameraX 预览、拍照、图库选择流程。
- Android 运行时权限 UX。
- `SpeechRecognizer` / `TextToSpeech` 语音输入输出。
- WorkManager pulse、离线衰减、回归反应、主动通知。
- 角色主屏、Lottie 表情层或更完整的陪伴感 UI。
- 隐私、导出、删除数据等用户控制能力。
- Instrumented UI 测试套件和 CI 工作流验证。

## 里程碑

### M0：稳住当前文本 Demo

目标：让当前聊天 demo 稳定、可运行、可排障。

- 保持 `testDebugUnitTest` 和 `assembleDebug` 通过。
- 补充 `.env` / API key 配置排障说明。
- App 启动时从 Room 恢复聊天历史。（已完成）
- 完善当前聊天页的空状态、错误状态、加载状态。（部分完成）
- 用真实 API 行为确认 GLM/Kimi base URL，并记录默认配置。

### M1：设置与配置 MVP

目标：不重新构建 App 也能配置模型。

- 添加导航框架。
- 添加设置页。
- 添加 API key 输入与本地持久化。
- 添加 GLM/Kimi provider 与 model selector。
- 聊天页设置弹层。（已完成）
- 添加模型连通性检查动作。
- 聊天页配置状态提示。（已完成）
- 补充配置写入与 ViewModel 行为测试。

### M2：记忆 MVP

目标：让记忆可查看、可理解、可控制。

- 如果 DAO 继续外溢到 runtime/UI，补一个 memory repository facade。
- 添加只读版记忆房间。（聊天页只读记忆条已完成，独立页面待做）
- 按需要添加删除、置顶、归档动作。
- 在聊天 UI 中更清楚地展示保存/搜索记忆的过程。
- 补充记忆排序、prompt 注入数量限制、工具结果展示测试。

### M3：情绪 MVP

目标：让陪伴对象不只是回复文本，而是能表现状态。

- 持久化情绪和关系快照，支持重启恢复。
- 在聊天页或独立主页增加紧凑状态/头像展示。（状态持久化和聊天页状态条已完成，头像层待做）
- 将 parsed mood/intensity 映射到可见 UI 状态。
- App 回到前台时补算时间衰减。
- 补充持久化、衰减、关系阈值变化测试。

### M4：Vision MVP

目标：支持一次性图片理解。

- 添加 CameraX 预览与拍照。
- 视需要添加图库/图片选择。
- 发送前压缩图片 payload。
- 通过现有 runtime 路径发送 `UserInput.Vision`。
- 权限拒绝时优雅退回纯文本聊天。
- 补充图片大小限制与 prompt 构建测试。

### M5：Pulse 与主动陪伴

目标：不用前台服务，也能有基础生命感。

- 添加 WorkManager pulse worker。
- 实现离线情绪衰减和回归反应。
- 添加通知权限流程。
- 添加用户可选择的主动通知。
- 补充 worker 调度与状态更新测试。

### M6：产品化加固

目标：准备更长时间的日常使用。

- 添加隐私控制、数据删除、数据导出。
- 扩展聊天、设置、记忆、权限等 instrumented tests。
- 添加或验证 CI 工作流。
- 验证 release signing 和 shrinker 行为。
- 在隐私预期清晰后再接入崩溃分析。

## 近期建议顺序

1. 设置页与 API key/model 配置。
2. 聊天历史恢复。
3. 只读版记忆房间。
4. 情绪/关系持久化。
5. CameraX 单张图片发送。
6. WorkManager pulse。

## 维护规则

- 架构文档可以保留目标形态，但必须明确标注当前实现状态。
- 每当里程碑从 planned 进入 implemented，都同步更新本文档。
- 每个功能里程碑尽量让测试和行为一起落地。
