# 对话体验调研与优化方案（豆包 / 千问 / ChatGPT / Claude / 开源项目）

> 调研日期：2026-08-16。基于当前 `feature/chat/` 代码实况勘察 + 竞品公开资料 + 开源 chat/agent 项目的 UX 模式提炼。
> 结论先行：**当前对话体验的核心问题不是"缺功能"，而是三件事——流式过程的"存在感"没做出来（硬跳滚动、无光标、chunk 一坨坨）、复合工具调用没有"过程叙事"（单 pill 覆盖式替换）、陪伴特质在聊天页几乎只剩头像帧动画（mood/关系/洞察有数据无展示）。**

---

## 一、现状诊断（按代码核对）

聊天模块平铺在 `app/src/main/java/com/xiaoqi/companion/feature/chat/`，核心文件：`ChatScreen.kt`(820 行) / `ChatViewModel.kt`(1443 行) / `MessageBubble.kt` / `ChatInputBar.kt` / `MarkdownMessageText.kt` / `StreamingMarkdownChunker.kt` / `ToolCallDetailSheet.kt` / `SendMessageUseCase.kt`。

### 做得好的（保持，不回退）

- **滚动 pin 策略**：只有真实拖动（`UserInput` source）解除置底、离底 32px 自动回 pin、IME 动画结束补滚——这套逻辑比多数开源项目细。
- **流式渲染架构**：`StreamingMarkdownChunker` 增量按行 commit + draft 尾巴 + block 级复用，方向正确（等价于一个手写版增量 markdown 树）。
- **首字前安抚文案轮播**（`ThinkingHintCarousel`，2/5/12/25/50s 六档文案升级）是陪伴产品特有的好模式，竞品没有。
- **错误恢复闭环**：停止/重试/重新生成/编辑重发/持久错误卡已齐。
- **工具详情 Sheet**（`ToolCallDetailSheet`）的地图"重新查询"交互是 proto-artifact 的雏形。

### 短板清单（按体验伤害排序）

| # | 问题 | 位置 | 伤害 |
|---|------|------|------|
| 1 | 复合工具调用无序列展示：`ChatMessage.toolStatus` 单 String 字段，多工具连续调用时 pill 文案覆盖式替换，无"第 2/3 步"过程感 | `SendMessageUseCase.kt` updateAssistantToolStatus | agent 感崩塌的最大来源 |
| 2 | 滚动全是 `scrollToItem(0)` 硬跳（初滚/发送/流式跟随/FAB/IME 共 5 处）；错误卡、权限卡、图片预览进出无动画；发送/停止/加载三按钮硬切 | `ChatScreen.kt` / `ChatInputBar.kt` | 流式过程"一顿一顿" |
| 3 | 流式无 caret/光标（设计上故意去掉了），16ms/64 字符批量 flush 在慢网络下文字"一坨坨"出现 | `StreamingMessageText.kt` | 停顿的流和完成的流看起来一样 |
| 4 | Markdown 手写解析器不支持链接、有序列表、表格、嵌套；全模块无 `SelectionContainer`，流式中和完成后都不能选中局部文本 | `MarkdownMessageText.kt` | 长回复可读性差 |
| 5 | AI 消息无头像、无气泡、无身份锚点；无时间戳、无日期分隔 | `MessageBubble.kt` | 陪伴产品的"对方感"缺失 |
| 6 | 回车即发送、无换行手段（3 行上限形同虚设）；无语音/相机入口；发送失败不回填输入框 | `ChatInputBar.kt` | 输入摩擦 |
| 7 | 空态无引导（无建议话题/快捷 prompt）；`relationshipLabel`（陌生/初识/熟悉/亲密）定义了但零渲染；POST_CHAT 洞察指示器只在主页，聊天页割裂 | `ChatScreen.kt` EmptyChatState / `ChatUiState.kt` | companion 特质未上桌 |
| 8 | 性能隐患：消息全量加载无 LIMIT；图片消息完整 base64 常驻 UiState 随 copy 放大；40+ 字段单一 `ChatUiState` | `MessageDao.kt` / `ChatMappers.kt` / `ChatViewModel.kt` | 长对话/多图会话退化 |
| 9 | FTS5 消息搜索索引已落库但无 UI 入口 | `on-device-experience-report.md` | 纯缺一层皮 |
| 10 | 仅浅色主题，`ChatColors` 4 个硬编码色值 | `ui/theme/ChatColors.kt` | 暗色用户不可用 |

---

## 二、竞品调研要点

### 1. 豆包（字节）

- **消息呈现**：双侧圆角气泡，用户蓝色、AI 灰色卡片；黑白主色 + 线性图标 + emoji 点缀降低严肃感。
- **拟人化**：可定制智能体（头像/昵称/人设/声音），"打造自己的 AI"是核心兴奋点。
- **语音**：沉浸式实时语音对话（独立通话界面），端到端低延迟。
- **快捷入口**：输入框上方横滚功能图标（拍题/生图/翻译/写作）。
- **追问**：回答后带"猜你想问"追问 chips。

### 2. 千问 App（阿里，2026 深度报告）

- **极简首屏**：头像 + **个性化问候（"你好，小普"，带用户昵称）+ 三条场景化提示词**引导冷启动，功能不铺首页。
- **思考可视化**：Thinking 模式思考过程可见，知乎实测评价"推理清晰、条理分明"是口碑来源之一；报告明确建议"模型能力感知化：思考过程可视化、回答质量标注、上下文相关的追问引导"。
- **Agent 任务展示**：任务助理自动拆解步骤、调用工具、**显示计划过程**，关键数据第三方复核；办事流程生成订单卡片供确认。
- **被骂的点**（引以为戒）：键盘弹出卡顿、新对话入口反直觉导致"上下文污染"、历史消息里复制/分享按钮消失。诊断结论："**能力过关，体验欠打磨**——正面评价来自功能上限，负面来自体验下限。"

### 3. ChatGPT（OpenAI）

- **消息形态**：AI 侧扁平全宽消息（无 SMS 圆气泡），用户侧软气泡——"工具感 > 聊天感"。
- **消息状态**：思考中是**可折叠区块 + 时长标签**（"Thought for 12s"）；搜索/工具是内联状态行（"Searching the web…"）+ 引文编号。
- **逐条操作**：点击消息出 bottom sheet（复制/朗读/分享/编辑分支）；重新生成支持**左右轮播历史变体**对比。
- **建议 chips**：新会话/回答后给追问建议，可关闭。
- **输入**：多行 + 附件 + 语音听写按钮常驻，typing↔dictation 平滑切换。
- 教训：假打字动画拖慢快模型曾被用户骂到下线——**平滑 ≠ 强行限速**。

### 4. Claude（Anthropic）

- **thinking 折叠块**：`✻ Thought for Xs`，答案上方，默认收起——把"推理"变成一段可审计的叙事。
- **Artifacts 侧板**：对话与产出物分离，聊天迭代、产出常驻，满意再发布——"聊天是过程，artifact 是结果"。
- **工具行为内联叙事**：research 时内联"Searching… / Reading…"状态行序列。
- 移动端消息操作极简：复制/重试，hover/tap 出现，不常驻。

### 5. 开源项目（lobe-chat / open-webui / assistant-ui / Vercel AI SDK）

- **lobe-chat / open-webui**：工具调用为**可折叠区块**（"Using tool…"运行中 spinner → 完成显示摘要），thinking 链同样折叠；open-webui 的引文/来源展示（Perplexity 模式）成熟。
- **assistant-ui**：把"重新生成变体轮播"做成了组件级能力（branch 数据结构）；工具调用有 loading/success/error 组件化状态。
- **Vercel AI SDK chatbot**：`smoothChunk` **平滑流式队列**——token 到达先进队列，按 30-60ms 稳定节奏出字，积压时加速追平；这是解决"一坨坨"的标准做法（和假打字的区别：队列排空即真实速度，不拖慢整体）。
- **gpt_mobile / Operit**（项目已调研过）：架构参考价值大，UX 层无成熟范式可抄，聊天 UX 需自己定义。

### 6. Coding Agent（Claude Code / Cursor / Cline）

最值得抄的是它们的**过程叙事**：

- **垂直时间线/stepper**：每个工具调用一行 = 状态图标（spinner→✓/✗）+ 动词化标签（"Reading file…"）+ 耗时，点击展开参数/结果详情。
- **诚实标签**：状态文案直接说在干什么（"Searching memory…"），不用含糊的 "Working on it"。
- **失败可见可恢复**：每步失败单独标注原因 + 重试入口，不折叠成统一兜底文案。
- **setproduct 设计指南**的对应结论：多步自动化任务不要只用聊天呈现——"聊天隐藏了计划"，应展示步骤列表；排队态用脉动占位而非假进度条；错误必须分类型给不同恢复动作。

---

## 三、提炼：陪伴类 AI 聊天的四条设计原则

1. **过程即陪伴**。对陪伴产品，"等待"是高频情绪场景：流式 caret、思考文案轮播、工具时间线都是在把不可见的推理翻译成"对方在认真回应你"的叙事。ChatGPT 把它叫 thinking，我们叫"在想"——但**叙事必须有结构**（时间线/折叠块），不能是一个 pill 文案闪来闪去。
2. **聊天是过程，卡片是结果**（Claude artifact 原则）。地图结果、提醒创建、记忆保存都该沉淀为内联结构化卡片（我们已有 `MapResultCard` 雏形），正文保持对话感，工具产物走卡片。
3. **陪伴感来自身份锚点 + 状态外露**。AI 消息需要头像/名字锚点（豆包/千问模式），mood/关系/洞察这些 companion 数据要有轻量的可视化出口——有数据无展示等于白做。
4. **体验下限 > 功能上限**（千问报告诊断）。键盘卡顿、硬跳滚动、错误无恢复动作这类"下限问题"对口碑的伤害大于任何新功能。先修下限。

---

## 四、优化方案（按优先级）

### P0 — 流式过程的"存在感"（感知层，1-2 周）

这部分决定"舒不舒服"，全部在既有架构上小改：

1. **平滑流式队列**：在 `StreamingMarkdownChunker` flush 前加一个输出队列——delta 进队，按 ~40ms/批稳定节奏出字，队列积压超阈值（如 200 字符）时加速排空，`Complete` 时一次性 flush。参照 Vercel `smoothChunk`。效果：慢网络下从"一坨坨"变成匀速流出，且不拖慢总时长。
2. **流式 caret**：在 `StreamingMessageText` 的 draft 尾巴后追加一个独立 Composable 的呼吸小圆点/竖条（不进 markdown 解析，之前污染解析的顾虑只针对拼 `...` 字符串）。暂停的流和完成的流必须可区分。
3. **滚动动效分层**：用户主动动作（发送、FAB 回底）用 `animateScrollToItem`；流式跟随保持即时 `scrollToItem(0)`（快速增高时动画反而抖）。错误卡/权限卡/图片预览包 `AnimatedVisibility`；输入栏右按钮用 `AnimatedContent`（Send↔Stop morph）。
4. **复合工具时间线**（本批最大架构改动）：`ChatMessage.toolStatus: String` 升级为 `List<ToolStep>`（id/label/status/耗时），气泡下方渲染成可折叠时间线（运行中 spinner→✓/✗ + 动词化文案 + 耗时），点开对齐 `ToolCallDetailSheet`。正好落 roadmap M5"工具调用升级为角色行为反馈"：SEARCHING→"翻记忆中…"，REMEMBERING→"记下来啦"。`ChatViewModel` 的 `RECENT_TOOL_CALL_LIMIT=3` 相应放宽或改为按消息关联查询。

### P1 — 可用性基线（2-3 周，可与 P0 并行）

5. **输入区**：IME action 改 `None` + 软键盘换行，发送只走按钮（或提供设置项）；发送失败回填输入框（真机报告遗留项）；预留语音按钮位（`SpeechRecognizer` 实现前先不渲染，避免死按钮）。
6. **Markdown 补课**：加链接（`LinkAnnotation`）、有序列表、表格（低频可后置）；流式期间放开复制（已生成部分可复制）。`SelectionContainer` 需评估与长按菜单的冲突，可只在完成后启用。
7. **身份锚点与时间感**：AI 消息左侧加 28-32dp `AuraPetAvatar` mini（流式时用 THINKING/SPEAKING mode，天然是"正在说话"的表情）；日期分隔 + 消息时间戳（长按菜单或 hover 显示亦可）。气泡形态二选一：走豆包灰卡片（陪伴感）或 ChatGPT 扁平（工具感）——**陪伴定位建议灰卡片**，但与现有裸文本风格差异大，需出一版视觉稿再定。
8. **空态引导 + 追问 chips**：新会话空态放 3 条场景化建议 prompt（参照千问，但内容走陪伴向："今天有点累，想吐槽一下"/"帮我记住一件小事"/"随便聊聊今天发生的事"）；AI 回复完成后可选追问 chips（可关闭，避免打扰感——建议只在空态和冷场时出现）。
9. **错误分型**：`formatError` 已有文案映射，补恢复动作（超时→重试；离线→提示等恢复；配额→去设置换模型），错误卡按类型给不同按钮。

### P2 — 陪伴特质上桌（差异化，随 Phase 2 节奏）

10. **聊天页内的 Insight 指示**：对话结束后 3s 触发的本地分析，在**聊天页**消息流底部显示一条轻量系统行（"Aura 正在想刚才的对话…"→完成后变为可点击的洞察卡片入口），不再只依赖主页。这本来就是 POST_CHAT 语义——"聊完接着想"应该发生在聊天现场。
11. **关系/情绪可视化**：`relationshipLabel`（陌生→亲密）挂到顶栏状态行或 ChatHeader 的头像旁（小徽标或文案），mood 变化在 Complete 后用头像表情 + 一句话轻反馈外露（"听起来今天心情不错"级别的系统行，注意限频）。数据已有，缺的只是渲染位。
12. **prefill 提示**：从 Insight 卡片带 prompt 进聊天页时，顶栏显示来源提示条（真机报告遗留项）。
13. **消息搜索**：FTS5 索引已就绪，在 ConversationListSheet 或顶栏加入口即可，纯 UI 工作。
14. **语音**：`SpeechRecognizer`/TTS 是 roadmap 既有项，豆包/千问都把语音当核心差异化；陪伴产品的语音优先级应高于更多 agent 工具。

### P3 — 工程与性能（防患，穿插做）

15. **消息分页**：`observeBySession` 加 LIMIT + 向上加载更多（key 已稳定，改动集中在 VM）。
16. **图片消息文件化**：base64 落地成 file，`ChatMessage` 只存 URI；顺带解决 UiState copy 开销。
17. **暗色主题**：`ChatColors` 进 theme 体系，删硬编码色值。
18. **流式→完成分支统一**：`Complete` 时若 renderBlocks 末态与全文解析一致则不清空重解析，消除切换瞬间的内容跳变。

### 不建议照抄的

- **输入框上方 15 个功能图标**（千问被批评认知成本高）：陪伴产品保持单一对话入口。
- **每条消息常驻操作按钮排**：移动端点击出 bottom sheet（ChatGPT 模式）比常驻"⋮"更干净，现有 DropdownMenu 可顺势升级。
- **假打字限速**：平滑队列必须"排空即真实速度"。
- **把 80% 场景塞进聊天**：设提醒、查地图这类高频操作值得聊天内嵌卡片，但不要为工具感牺牲陪伴感。

---

## 五、与既有规划的衔接

- P0-4 工具时间线 = roadmap M5 "工具调用升级为角色行为反馈" 的第一步（先做叙事结构，再做角色行为动画）。
- P1-5/8/9 = `on-device-experience-report.md` 遗留项（换行、失败回填、prefill 提示）+ 千问"体验下限"诊断的对应修复。
- P2-10/11 呼应 architecture.md "UI 状态驱动核心卖点"，把 Presence/情绪数据从"只有头像帧"扩展到消息流叙事。
- P2-14 语音对齐 roadmap 未实现清单的 SpeechRecognizer/TTS。

## 六、建议落地顺序

```
第 1 批（感知层，立刻见效）：平滑队列 + caret + 滚动/卡片动效 + 按钮morph
第 2 批（agent 感）：工具时间线（架构改动）+ 错误分型
第 3 批（可用性）：输入区换行/回填 + markdown 链接 + 时间戳/头像 + 空态引导
第 4 批（陪伴差异化）：聊天页 insight 行 + 关系/情绪外露 + 搜索 + 语音
穿插：分页/base64 文件化/暗色（P3）
```

每批做完跑 `make test` + 真机走一遍 `on-device-experience-report` 的复测清单，避免"下限"回退。

---

## 七、深挖：一轮复合工具回复的逐帧诊断（回复不舒服的机理）

> **状态（2026-08-16）**：本节四项修法已全部落地——意图段保留（`intentText`）、key 稳定（`persistedId` + `stabilizePersistedMessages`）、工具步骤时间线（`toolSteps`）、节奏驱动出字（33ms ticker）+ caret，另加 PerformancePill 仅 debug 可见。详见 `docs/roadmap.md` 2026-08-16 条目；测试 672→680。

> 针对核心痛点"agent 回复的时候不舒服"，把回复生命周期逐帧还原后，机理可以概括为一句话：
> **agent 回复本来是一段有章节的故事（想 → 做 → 看 → 说），但当前 UI 把它渲染成一块"自我擦除的画板"——正文被擦两次、状态 pill 被反复覆盖、出字节奏跟着网络抖动，最后完成时还要闪一下。**

### 时间轴（以"帮我查附近麦当劳并设个提醒"这类复合请求为例）

| 时刻 | 发生什么 | 用户看到什么 |
|------|---------|-------------|
| T0 发送 | assistant 占位消息（isStreaming, content=""） | 安抚文案轮播（✓ 好） |
| T1 模型先说话 | 流出"我看一下地图哈…" | 文字**一坨坨**蹦出（无光标；flush 跟着网络到达节奏走） |
| T2 工具启动 | `resetStreamingForToolCall()` 发 `StreamingReset`（`CompanionRuntime.kt:144-154`，`ToolStarted`/`ToolCallUpdated(STARTED)` 都会触发） | ⚡**闪断 #1**：刚读的句子被整段擦掉，回到和 T0 一模一样的三点 loading——像对话"重启/缓冲"了一次 |
| T3 工具执行中（数秒） | pill = "查询地图中…"，正文空白 | 静态 pill + 三点。多工具时：第 2 个工具启动又擦一次、pill 文案**覆盖式替换**（"已找到麦当劳"被"创建提醒中…"顶掉），**过程叙事只剩最后一章** |
| T4 工具完成→模型重新生成 | 短暂无事件（模型在推理） | 停留着上一工具的旧 pill，无任何"还在动"的信号 |
| T5 最终回答流式 | 到达驱动的批量 flush | 文字按网络节奏一坨坨出现，间歇性卡顿 |
| T6 完成 | ①消息 id 从 transient 换成 persisted（`SendMessageUseCase.kt:469`，或更早在 `ChatMessageReconciler` 合并时发生）→ LazyColumn 按 id 作 key 视为"删旧插新"，`animateItem(fadeIn=tween250))` 把整条回复**当新条目重新淡入**；② isStreaming=false → `StreamingMessageText` 切 `MarkdownMessageText` 全文重解析（代码块/列表可能重排）；③ PerformancePill（"3.2s · 18 tok/s"）弹出；④ Room 收集器同一时刻全列表刷新 | ⚡**闪断 #2**：完成的瞬间气泡重新淡入 + 内容重排 + 工程指标弹出，三件事叠在一帧 |

### 四个根因（都在代码里坐实）

1. **`StreamingReset` 清屏**（`CompanionRuntime.kt:144` + `SendMessageUseCase.kt:344-358`）：模型在调工具前说的话被从屏幕上抹掉；且 `rawResponse` 同时被清零，**这段"意图叙事"也不会被持久化**——过程的消失是屏幕和数据双重的。Claude/Claude Code 恰恰相反：thinking、工具调用、中间文本全部留在 transcript 里。
2. **单 `toolStatus` 字段**（`SendMessageUseCase.kt:245-253`）：复合工具 = 一个槽位反复覆盖，没有步骤序列的载体。
3. **完成瞬间的 key 切换**：transient id → persisted id 的替换让 LazyColumn 认为是新消息，触发 animateItem 重淡入；叠加分支切换全文重解析 + PerformancePill 弹出。
4. **出字是"到达驱动"而非"节奏驱动"**（`scheduleStreamingRender`，`SendMessageUseCase.kt:167-187`）：实测逻辑是"≥4 字符且无排队 job 就立即 flush、≥64 字符直接 flush"——16ms 批处理只在同一帧内有效，渲染节奏 ≈ 网络到达节奏，天然一坨坨。无 caret，暂停的流和完成的流不可区分。

### 对应修法（按止血效果排序）

1. **止血两处闪断**：完成时保持 LazyColumn key 稳定（persisted id 只存字段、不换 item id，或全程用 transient id 作 key）；`StreamingReset` 不清屏——已渲染的 pre-tool 文本保留（可降透明度变成"意图段"），新正文接着流。
2. **工具时间线**（见 P0-4）：每个工具一行 = spinner→✓/✗ + 动词化文案 + 耗时，多工具天然有序；T3/T4 的"静态 pill 死时间"变成可见的步骤推进。
3. **平滑出字队列 + caret**（见 P0-1/2）：delta 进队列、按 ~40ms 稳定节奏出字、积压加速排空——把"到达驱动"换成"节奏驱动"。
4. **PerformancePill 收进详情面板**（长按/Sheet），陪伴产品的正文区不放 tok/s 工程指标。

---

## 附：主要参考来源

- [Designing AI chat interfaces: Anatomy, patterns, pitfalls（setproduct）](https://www.setproduct.com/blog/ai-chat-interface-ui-design) — 消息状态机/流式模式/反模式最系统的整理
- [千问App深度体验报告（人人都是产品经理，2026）](https://www.woshipm.com/evaluating/6388078.html)
- [豆包产品体验报告（人人都是产品经理）](https://www.woshipm.com/evaluating/6102782.html)
- [唠唠豆包：面向未来的 AI 产品设计（知乎）](https://zhuanlan.zhihu.com/p/716827396)
- [ChatGPT Design Breakdown（925 Studios）](https://www.925studios.co/blog/chatgpt-interface-design-breakdown)
- [Claude Artifacts UX teardown（AI UX Playground）](https://aiuxplayground.com/teardowns/claude/artifacts/)
- [assistant-ui](https://www.assistant-ui.com/) / [open-webui](https://github.com/open-webui/open-webui) / lobe-chat — 开源 chat UI 组件模式
- 项目内：`docs/reference-android-ai-projects.md`（gpt_mobile/Operit/skydoves 架构调研）、`docs/plan/on-device-experience-report.md`（真机审计遗留项）
