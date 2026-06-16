# Aura 奥拉 · 真机体验报告

> **设备**:realme RMX3888(Android 15,SDK 35)
> **版本**:`com.xiaoqi.companion.debug`(`./gradlew.bat :app:assembleDebug` 产物,基于 `DualMigration` 修复后 build)
> **体验者视角**:首次安装、清 DataStore 模拟零状态用户,逐路由点击
> **截图**:`tmp-adb-screens/01-home.png` ... `30-onboarding-done.png`
>
> 报告目的是"从用户视角梳理当前是否合理,以及**最值得做的下一批改进**",不是设计审美评测。

---

## TL;DR

整体走完一遍,**首屏 → 设置 → MCP → 记忆 → 聊天 → Onboarding** 路径全程不闪退、不卡死,UI 节奏统一("暖、低饱和、卡片化")。已落地的关键闭环全部可点:

- Home 角色 + 心情趋势 + Aura 注意到的 Insight 卡片
- Settings 模型/能力开关/数据透明/Health fallback
- MCP 空态 + 添加模板(高德)+ 自定义 URL
- 记忆列表 + 详情 + 删除 + 4 维操作
- 聊天 + Tool 状态徽章 + Tool 详情面板
- Insight 长按 → 证据 → "和 Aura 聊聊 / 本周不聊 / 隐藏 / 知道了"四选项
- 5 步 Onboarding,5/5 必填,其余 4 步可空,完成后直进 Home

**已修复**:Room Migration 新 API 闪退(`DualMigration` 抽象,commit 中)

**最值得做的下一批改进**(按收益/工时排):

1. **Memory Room 卡片:长按入口不可见** —— 卡片上只有删除图标,用户根本不知道"长按有更多操作"
2. **Onboarding 5/5 必填,但 1/5 也"卡"** —— 实测"下一步"在 step 1 空文本时不响应,源码却写着 `canAdvance = true` —— 行为和注释不一致
3. **Chat 中文 IME 不可用** —— ADB `input text` + Gboard 配 OutlinedTextField 不通,真实用户用拼音能进但若键盘没切到中文/网络差会有"send 了但发不出去"的错觉(缺少发送中/失败重试明确指示)
4. **Home 卡片长按"隐藏依据"没有**撤销入口** —— 用户点完"本周不聊习惯"就找不到"我又想看了"怎么办
5. **Insight 卡片"和 Aura 聊聊"预填 prompt 但 Chat 顶栏没视觉提示** —— 用户不知道这是带过来的上下文
6. **MCP 自定义 URL 没有响应式校验** —— 填一个 `https://example.com` 也能保存,真正调一次才发现
7. **Onboarding 数据没有"重新走一遍"**入口** —— 写错或想改称呼,目前无路径

详细见下文。

---

## 1. 流程地图(从用户视角)

| # | 路由 | 截图 | 状态 | 关键发现 |
|---|---|---|---|---|
| 1 | Home `AuraHomeScreen` | 01 | ✅ | 角色 + 心情趋势 + Insight 卡片;首次 Insight 来自关键词统计(messages 关键词 4 次/近 7 天)|
| 2 | Settings | 02~05 | ✅ | 模型选择、能力开关、Dream Loop、Health fallback、数据透明三按钮 |
| 3 | MCP | 06~07 | ✅ | 空态 + 添加表单(高德模板 + 自定义 URL),无响应式校验 |
| 4 | 记忆 | 08 | ✅ | 2 facts,但**长按入口不可见**,只能删除 |
| 5 | Chat | 09~12 | ✅ | Tool 状态徽章可点 → 详情面板;中文 IME 不稳(ADB 限制) |
| 6 | Chat + MCP 标签 | 13 | ✅ | 顶栏显示 `tools: mcp` 开关状态 |
| 7 | Home Insight 长按 → 证据 | 16~17 | ✅ | 4 动作(和 Aura 聊聊 / 本周不聊 / 隐藏依据 / 知道了) |
| 8 | Onboarding 5 步 | 19~30 | ✅ | 1/5 看似可空,实测"下一步"不响应(与代码注释 `canAdvance = true` 不一致);5/5 必填;完成直进 Home |

---

## 2. 每页体验细节

### 2.1 Home(`AuraHomeScreen`)

**好**:
- 角色名"Aura" + 心形/扳手/设置三图标一字排开,层级清晰
- "心情趋势"有 Aura 提示"先收集情绪数据 — 几周后开始发现规律",**主动管理预期**而不是空着
- "Aura 注意到的"Insight 卡片是带 ⓘ 类别 + 标题 + 摘要的卡片,长按有彩蛋,闭环感强

**可改**:
- **Insight 卡片没有"已读"标记**, 多个 insight 同时显示时用户搞不清哪个是新的;建议右上角加未读小圆点 + 状态(`mutedUntil` 已经在 InsightEntity 上,只差 UI)
- **没有下拉刷新** — Insight 是被动推送,用户想"看看 Aura 又发现啥"无从入手

### 2.2 Settings(`SettingsScreen`)

**好**:
- 顶部"模型与能力"区域把云端/本地 LLM、Tools 总开关、Tool 详情子开关(`MCP/HTTP/Search`)、Dream Loop 全列清楚
- "DataTransparency" 三按钮("清聊天/清记忆/全部清")分级清楚,符合"知情同意"原则
- Health Connect 不可用时**自动 fallback 到本机传感器**(源码在 `HealthSyncManager`);UI 上明确写"已切换到本机传感器",用户不会以为是 bug

**可改**:
- 模型选择是下拉,没有"下载/更新"按钮 — 用户切到本地 LLM 后,看不到模型是否已下载,也没"重新下载"入口
- **DataTransparency 的"清聊天"是物理删除消息**,但聊天页 / 记忆 / Insight 还会引用这些消息 → 列表项会出现"消息已删除但卡片还在"的孤儿。需要先清引用再清消息,或干脆只"软删除"

### 2.3 MCP(`McpSettingsScreen`)

**好**:
- 空态有"添加 MCP"大按钮,门槛低
- "添加"表单有"高德地图"模板(选完自动填 URL),也有"自定义 URL"路径,覆盖主流用法
- 卡片显示状态徽章(`已启用 / 失败`)和"上次调用"时间

**可改**:
- **没有任何"测试连接"按钮** — 用户填完 URL 只能靠"和 Aura 聊 → 用 mcp 工具"间接验证,慢且不直观。建议添加前先 POST 一次 `/mcp/v1/tools/list` 验证,失败给具体错误
- **没有"删除前确认"** — 一旦删除配置,工具的命名空间/调用历史可能丢,误删代价大
- 自定义 URL 输入框没做"必须是 https:// 开头"的轻量校验

### 2.4 记忆(`MemoryRoomScreen`)

**好**:
- 顶部三个统计 pill("事实 / 时刻 / 习惯")+ 筛选 chip(全部 / FACT / EPISODE / PROCEDURAL)
- 卡片有"重要性"色点(绿/黄/灰)+ 标签 + 源(`对话` / `xxx`)
- 详情弹窗有时间戳、源、删除

**可改(高优)**:
- **核心问题**:卡片上**只有删除图标**,`onLongPress` 才是 pin / archive / 详情入口,但**没有任何视觉提示告诉用户"长按有更多"**。用户大概率会以为"记忆只能删",导致 pin/archive 功能空转
  - 建议:卡片右侧加一个 `⋮` 三点菜单图标(删除也在里面);或者卡片底部加一条半透明提示"长按查看更多",首次进入时显示 3 秒后渐隐
- **没有"搜索"框** — 2 条记忆看不出问题,假设用户积累到 50 条事实 + 100 条时刻,无法快速定位
- **没有批量选择** — 想清掉一堆低 importance 的事实,只能一张一张点
- **没有时间排序/分组** — 全混在一起,用户看不出"Aura 记住我"的时间线

### 2.5 聊天(`ChatScreen`)

**好**:
- 顶栏:角色 + 记忆(memory icon) + 工具状态(mcp 标签) + 设置,四要素一目了然
- 输入栏支持图片附件(Photo Picker)、@call to Reminder、Tool 状态徽章
- Tool 徽章可点 → 详情面板,展示完整 envelope(参数/结果/状态/耗时)
- 消息流 `reverseLayout` + IME 动画结束后 `scrollToItem(0)`,键盘弹起不挡最新消息

**可改**:
- **发送按钮在文本为空时**仍可见(只是禁用),真实用户偶尔会"点了没反应"误以为 bug。建议空文本时把发送按钮变成"图片/语音"快捷入口,而不是灰按钮
- **没有消息搜索**,历史对话完全埋没(`message_search_docs` 已经在 `MIGRATION_9_10` 落库 + `SearchSummariesTool` 已存在,但没 UI 入口)
- **没有"重新生成"按钮** — LLM 答错/答得不好,只能"再发一次"用近似 prompt 重新问
- **没有"停止生成"按钮** — 流式响应如果答偏了,只能等流完

### 2.6 Insight 长按彩蛋(看证据)

**好**:
- 弹窗有"证据"行(具体数据:关键词 4 次/近 7 天),不是空话
- 4 个动作:和 Aura 聊聊 / 本周不聊习惯 / 隐藏依据 / 知道了 — 既能继续也能主动退订
- "和 Aura 聊聊"会把相关 prompt 预填到 Chat 输入框,完成"Insight → 行动"闭环

**可改**:
- "本周不聊习惯"和"隐藏依据"**没有撤销入口**。用户点完之后突然又想看,在哪恢复?目前要去 Settings 翻配置
- 建议:在 Settings 加"已静音的 Insight"列表,显示 `mutedUntil` 倒计时,可手动取消

### 2.7 Onboarding(`OnboardingScreen`)

**好**:
- 5 步都有人话问题,不是"先填 10 个表单"式劝退
- 进度条 + 上一步/下一步,导航清晰
- 5/5 必填(只选一个 chip 才能"完成"),其余 4 步全可选
- "希望我怎么称呼你"默认值是"就叫我 Aura 吧,语气放松一点",给用户温和示范

**可改(高优)**:
- **行为与代码注释不一致**:`OnboardingScreen.kt:70` 写着 `// 5 问全部可选` + `val canAdvance = true`,但实测 step 1(空 q1)点"下一步"不响应,需要至少 1 个字符才能进。需要明确是:`canAdvance = q1.isNotBlank()` 还是真"5 问全可选"?如果按"产品调性"应该是后者,源码需要修
- **没有"重新走一遍"入口**:Onboarding 完成后,DataStore 写 `onboarding_completed_at`,之后无任何路径回到这 5 步。建议:Settings 加"重新了解 Aura",点击后清 `onboarding_completed_at` 并跳到 Onboarding
- **第 1 步的 placeholder 用了"例:换工作的面试"**,但"换工作"是中性事件,不是"挂心的事"。建议改成更贴近的:"例:周三的体检,月底的项目交付"
- **步骤间没有"你已答过 X 步"小提示**,用户不知道还剩几步
- **Q4 三个朋友输入框"label = 朋友 1"显示在输入框上方**而非内部,占空间大;建议用 `placeholder` 而非 `label`,留更多地方给用户写

---

## 3. 跨页体验细节

### 3.1 启动路径
冷启动 → MainActivity → 检查 `onboarding_completed_at` → 跳 Onboarding 或 Home
- **好**:检查逻辑在 NavHost,而不是 Activity intent,流程图清晰
- **改**:Onboarding 完成后直接进 Home,但**没有"欢迎动画"** — 从 5 步表单突然切到 Home,情绪断点。建议加 0.5s 渐显

### 3.2 跨页一致性
- 配色统一:暖色卡片 + 绿色主色 + 灰色辅助,全部页面协调
- 字体层级统一:title/titleMedium/bodyMedium/labelSmall 四档,无滥用
- 顶栏样式统一:`← 标题 + 右上图标`,无大杂烩

### 3.3 错误/空态
- **好**:记忆 / Insight / MCP 都有空态文案 + 引导按钮
- **改**:
  - Chat 流式响应失败时只在底部 Snackbar 提示"send 失败",**消息不留在输入栏**,用户只能凭记忆重写 → 建议失败时消息回填到输入框
  - 没有任何"网络断开"的全局提示,App 静默失败

---

## 4. 技术债务 / 已被我看到的问题(用户可见 vs 内部)

### 4.1 用户可见
| 现象 | 原因 | 修法 |
|---|---|---|
| Memory 卡片"长按有更多"不可见 | 只有删除图标,无 `⋮` 入口 | 加三点菜单或底部首次提示 |
| Onboarding 第 1 步空文本不让进 | 源码 `canAdvance = true` 但实操有判断,或 IME 误判 | 改源码或确认设计 |
| Insight"本周不聊习惯"无撤销 | `mutedUntil` 已存,但 Settings 无列表 | Settings 加"已静音"区域 |
| Chat 中文 IME 不稳 | ADB 限制(非真 bug),但缺少"输入中断"提示 | 加 `IME composing region` 指示 |

### 4.2 内部(可后续做)
- `message_search_docs` 已有但无 UI 入口
- `PulseWorker` 未实现,Reminder 仍走 `OneTimeWorkRequest`
- 远端 Agent Server / `RemoteAgentRuntime` 未实现
- Rive/Lottie 状态机资源仍缺,情绪用 Compose Canvas 临时画
- 相机(目前只有 Photo Picker)

---

## 5. 优先级建议(从用户视角)

| 优先级 | 项 | 影响 | 工时估 |
|---|---|---|---|
| P0 | Memory 卡片加 `⋮` 三点菜单 | 功能可见性 | 0.5d |
| P0 | Onboarding 第 1 步校验 / 修源码 | 一致性 | 0.5d |
| P0 | Settings 加"已静音的 Insight"列表 | Insight 闭环 | 1d |
| P1 | Chat 顶部加搜索入口(用 `message_search_docs`) | 长期可用性 | 1d |
| P1 | MCP 添加前"测试连接" | 反馈即时性 | 0.5d |
| P1 | Chat 发送失败回填到输入栏 | 错误恢复 | 0.5d |
| P2 | Settings 加"重新了解 Aura" | 提升重设能力 | 0.5d |
| P2 | Insight 卡片加未读标记 | 多 Insight 区分 | 0.5d |
| P2 | Home 加下拉刷新 | Insight 主动刷新 | 0.5d |
| P3 | Onboarding 完成后加欢迎动画 | 情绪断点 | 0.5d |
| P3 | Memory Room 加搜索 + 批量选择 | 大记忆量 | 1.5d |

---

## 6. 已知 BUG(在体验过程中遇到)

1. ✅ **已修**:Room Migration `NotImplementedError`(`DualMigration` 抽象 + 9 个迁移对象全改写)— 见 commit 前后
2. 🐞 **未修**:Onboarding 第 1 步 `canAdvance` 行为与注释不符(实测空文本不能进)
3. 🐞 **未修(功能缺失)**:Memory 卡片 pin/archive 入口对用户不可见
4. 🐞 **未修(测试条件)**:ADB `input text` 中文不进 Compose `OutlinedTextField`(adb 自身限制,非 app bug)
5. 🐞 **未修(感知)**:Chat 流式失败时消息不回填输入栏

---

## 7. 截图索引

| # | 文件 | 描述 |
|---|---|---|
| 01 | `tmp-adb-screens/01-home.png` | Home 主屏 |
| 02 | `tmp-adb-screens/02-settings.png` | Settings 顶部 |
| 03 | `tmp-adb-screens/03-settings-scroll1.png` | Settings 滚动 1 |
| 04 | `tmp-adb-screens/04-settings-scroll2.png` | Settings 滚动 2(Health + DataTransparency) |
| 05 | `tmp-adb-screens/05-settings-transparency.png` | DataTransparency 展开 |
| 06 | `tmp-adb-screens/06-mcp.png` | MCP 空态 |
| 07 | `tmp-adb-screens/07-mcp-add.png` | MCP 添加表单 |
| 08 | `tmp-adb-screens/08-memory.png` | 记忆列表 |
| 09 | `tmp-adb-screens/09-chat.png` | 聊天(空态后) |
| 10 | `tmp-adb-screens/10-chat-input.png` | 聊天输入框聚焦 |
| 11 | `tmp-adb-screens/11-chat-typed.png` | 输入内容(英文测试) |
| 12 | `tmp-adb-screens/12-chat-notif.png` | 聊天 Tool 状态 |
| 13 | `tmp-adb-screens/13-chat-mcp.png` | 聊天 + MCP 标签 |
| 14 | `tmp-adb-screens/14-back-home.png` | 返回主页 |
| 15 | `tmp-adb-screens/15-home.png` | 主页确认 |
| 16 | `tmp-adb-screens/16-insight-longpress.png` | Insight 长按菜单 |
| 17 | `tmp-adb-screens/17-evidence.png` | 证据详情(4 动作) |
| 19~30 | `tmp-adb-screens/19-onboarding-1.png` ... `30-onboarding-done.png` | Onboarding 5 步完整流程 |

> 注:18 因目录不存在保留为空,实际只到 17。
