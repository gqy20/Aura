# Aura 演示视频脚本 + 口播稿

> **视频定位**：初赛必交项 05「原型演示视频」
> **评委硬性要求**：必须实拍「① 模型本地加载过程 → ② 推理输入输出 → ③ 核心交互流程」，**禁止纯 PPT 录屏或无代码逻辑的动效演示**。
> **核心信息（一句话）**：真正的陪伴，不是陪你把难过说完，是帮你把让你难过的那件事，办掉。
> **建议时长**：4–5 分钟
> **拍摄方式**：真机 ADB 录屏为主 + Android Studio Logcat 实时日志穿插 + 少量代码/架构图字幕

---

## 一、拍摄前准备清单（⚠️ 必须先做，否则拍不出来）

### 1. 本地推理 SO 真机编译【最关键前置，标红】

评委要求展示「模型本地推理」，而 `libaura_mnn_llm.so` 默认编译为 **stub 版本**（未配置 `AURA_MNN_HOME` 时，所有 JNI 调用会抛 `IllegalStateException`）。**拍摄前必须先让本地推理在真机跑通**：

- 设置 `AURA_MNN_HOME` 环境变量指向本地 MNN 源码树，重新 `./gradlew.bat assembleDebug`，让 CMake 链接真正的 `libMNN.so`（见 `aura_mnn_llm_jni.cpp:17` 的 `AURA_MNN_LINKED` 宏）。
- 真机安装后，在 App 内下载 `Qwen3.5-0.8B-MNN`（约 0.5–1GB，9 个文件），确认能流式输出。
- **验证标准**：本地模型输入一句话，能流式逐字输出，全程断网（开飞行模式也能跑）。
- ⚠️ 如果这一步没跑通，整个视频的「推理输入输出」硬性要求就达不到——**这是优先级最高的前置任务**。

### 2. 预置数据（让"记得你""注意到你"有东西可演）

- 完成 Onboarding 5 问（留几条偏好，如"偏爱清淡""最近在赶 deadline"），后续演示"记得你"。
- 制造 10–20 条对话历史（让 Dream Loop / 记忆搜索有素材）。
- 触发一次 Dream Loop（设置页点"立即运行"），确保至少生成 **1 条真实 Pattern Insight** 带来源线索，供 Insight 卡片演示。
- Health Connect 授权 + 导入若干天步数/心率/睡眠数据（让 `query_health_data` 有真实返回）。

### 3. MCP 配置

- 设置页 → MCP，添加**高德地图** preset，填入有效的高德 API Key（`lbs.amap.com` 申请）。
- 验证工具发现成功（能看到 maps 相关工具），否则"查附近面馆"那段演不出来。

### 4. 录屏工具

```bash
# ADB 录屏（最长 180s/段，长视频分段录后剪辑）
D:\tools\ADB_Cli\adb.exe shell screenrecord --bit-rate 8000000 /sdcard/aura_01.mp4
# 结束：Ctrl+C，然后拉回
D:\tools\ADB_Cli\adb.exe pull /sdcard/aura_01.mp4 ./docs/plan/visual-audit-assets/

# Logcat 同步录（展示模型加载/推理/工具调用日志，证明真实性）
D:\tools\ADB_Cli\adb.exe logcat -v time | tee aura_logcat.txt
```

> 录屏素材放 `docs/plan/visual-audit-assets/`（已 gitignore，不入库）。

---

## 二、分镜脚本

| # | 时间 | 画面（手机操作） | 口播稿 | 拍摄备注 |
|---|---|---|---|---|
| 1 | 0:00–0:25 | 黑底白字字幕：「今天好累」→ 快速闪过 3 个现有 AI 的「辛苦了，记得休息」截图 | 你现在打开任何一个 AI 说"今天好累"，它会回你"辛苦了，记得休息"。但**累的真正原因**——加了多少天班、走了几步、deadline 还有多久——它一个都不知道。它给你的是**情感止疼药**。 | 痛点引入。可提前截图几个竞品回复，快速剪辑。节奏要快。 |
| 2 | 0:25–0:40 | 字幕打出灵魂句 | 今天介绍 **Aura**。它的理念只有一句：**真正的陪伴，不是陪你把难过说完，是帮你把让你难过的那件事，办掉。** | 灵魂句，停顿、加重。这是全片钉子。 |
| 3 | 0:40–1:10 | 双轨架构图（字幕/简单动画）→ 切 App 主屏 | Aura 能做到，是因为它有**两个大脑**：云端大脑帮你办事，**本地大脑真正懂你**——而且这个本地大脑，就跑在你的手机里。 | 过渡，铺垫本地模型。 |
| 4 | 1:10–1:50 | **模型本地加载**：App 内模型下载页（进度条）→ 切 Logcat 显示 MNN 加载日志 → 显示模型文件大小/加载耗时 | 看这是 Aura 正在下载本地的 Qwen3.5 模型，来自魔搭 ModelScope，完全下载到你手机本地。加载日志可以看到，它用的是阿里的 **MNN 推理引擎**，跑在 Arm CPU 上。 | ✅ **硬性①模型本地加载**。展示进度条 + 加载日志 + 文件大小。 |
| 5 | 1:50–2:30 | **推理输入输出**：开飞行模式（断网图标可见）→ 本地模型推理 demo，输入框打字 → 流式逐字输出 → 屏幕角显示首 token 延迟 / tokens/s | 重点来了——我现在**断开网络**，纯离线。输入一句话，模型在**你手机本地**流式输出，全程不联网、不花钱、不上云。 | ✅ **硬性②推理输入输出**。一定要先开飞行模式证明离线！展示延迟和速度数字。 |
| 6 | 2:30–3:40 | **核心交互**：聊天页，输入"今天好累" → Aura 先调用工具（屏幕显示 search_memory / query_health_data / get_weather / 高德 MCP 的调用过程）→ 流式给出方案 | 现在，我对 Aura 说"今天好累"。注意看——它没有回"辛苦了"。它**先去查了我的记忆、今天的健康数据、天气、附近的面馆**。然后给了我一整套今晚的安排：几点睡、下班沿河边走走、还帮我查到那家我喜欢的面馆现在不用排队。**它没止疼，它顺着"累"，把制造"累"的那段生活，接住了。** | ✅ **硬性③核心交互**。本片高潮。要让工具调用过程**可见**（气泡/状态条），突出"懂你→帮你办"。⚠️ 只演到"查到信息+给方案"，**别演"自动下单/订位"**（那是路线图，会穿帮）。 |
| 7 | 3:40–4:15 | 切到 MemoryRoom 记忆房间，展示 onboarding 时的偏好被记住 / 记忆可搜索 | 而且 Aura **真的记得你**。这是我第一次用它时告诉它的偏好，到现在还能被检索到。它不是每次从零开始的陌生人。这些记忆全部存在**你手机本地**，不上云。 | 突出"长期记忆 + 本地"。可演示 FTS5 搜索一条。 |
| 8 | 4:15–4:50 | 设置页 Dream Loop 开关 → 点"立即运行" → 等其跑完 → 主页出现 Insight 卡片 → 点开看**来源线索** | 更特别的是，**你锁屏之后，Aura 还在"做梦"**。它的 Dream Loop 用本地模型整理你最近的生活，生成洞察——比如"你最近几周周日下午情绪都偏低"。**每一条都带着真实来源**，可以点进去核验，不是 AI 瞎编的。 | 差异化杀招：Dream Loop + 可验证 Insight。重点点"来源线索"证明不幻觉。 |
| 9 | 4:50–5:10 | 收尾字幕 + Aura Logo + 作品名 | 所以 Aura 不是又一个会聊天的 App。它是第一个把**情感陪伴**和**解决真实生活**连起来的 AI——懂你的留在本地，帮你的连到世界。别人止于"我懂你"，**Aura 止于"我帮你办好了"**。 | 收尾钉灵魂句。口号感。 |

---

## 三、口播稿（纯文本，提词用）

> 以下是去掉表格、可直接念的连续版。建议用提词器，语速适中，灵魂句放慢加重。

你现在打开任何一个 AI 说"今天好累"，它会回你"辛苦了，记得休息"。但累的真正原因——加了多少天班、走了几步、deadline 还有多久——它一个都不知道。它给你的，是情感止疼药。

今天介绍 Aura。它的理念只有一句：**真正的陪伴，不是陪你把难过说完，是帮你把让你难过的那件事，办掉。**

Aura 能做到，是因为它有两个大脑：云端大脑帮你办事，本地大脑真正懂你——而且这个本地大脑，就跑在你的手机里。

看，这是 Aura 正在下载本地的 Qwen3.5 模型，来自魔搭 ModelScope，完全下载到你手机本地。加载日志可以看到，它用的是阿里的 MNN 推理引擎，跑在 Arm CPU 上。

重点来了——我现在断开网络，纯离线。输入一句话，模型在你手机本地流式输出，全程不联网、不花钱、不上云。

现在，我对 Aura 说"今天好累"。注意看——它没有回"辛苦了"。它先去查了我的记忆、今天的健康数据、天气、还有附近的面馆。然后给了我一整套今晚的安排：几点睡、下班沿河边走走、还帮我查到那家我喜欢的面馆现在不用排队。它没有止疼，它顺着"累"，把制造"累"的那段生活，接住了。

而且 Aura 真的记得你。这是我第一次用它时告诉它的偏好，到现在还能被检索到。它不是每次从零开始的陌生人。这些记忆，全部存在你手机本地，不上云。

更特别的是，你锁屏之后，Aura 还在"做梦"。它的 Dream Loop 用本地模型整理你最近的生活，生成洞察——比如"你最近几周周日下午情绪都偏低"。每一条都带着真实来源，可以点进去核验，不是 AI 瞎编的。

所以 Aura 不是又一个会聊天的 App。它是第一个把情感陪伴和解决真实生活连起来的 AI——懂你的留在本地，帮你的连到世界。别人止于"我懂你"，Aura 止于"我帮你办好了"。

---

## 四、演示避坑（拍摄前务必读）

1. **只拍已跑通的功能**。以下都是路线图，**绝对别入镜**，否则评委追问会穿帮：
   - ❌ 健身训练优化、智能家居控制、自动订咖啡/备礼等"主动代办"
   - ❌ Voice 语音输入输出
   - ❌ "我帮你订好了/下单了"这类执行级承诺——演示只到"查到信息 + 给方案"
2. **本地推理一定要先断网再演**。开着网演本地推理没说服力，开飞行模式是铁证。
3. **工具调用过程要可见**。第 6 段的高潮全靠"它在调用工具"被看见——确保 UI 有工具调用状态展示（气泡/状态条），别让它静默回复。
4. **预置数据要真实**。记忆、健康数据、Insight 都要提前埋好真实内容，别现场等 Dream Loop 跑（可能失败/超时）。Insight 演示用**已生成好的**那一条，现场只点开看。
5. **Logcat 穿插要有选择**。只展示关键日志（模型加载、推理开始、工具调用），别把整个日志流贴上去，喧宾夺主。
6. **崩溃预案**。现场演示易翻车（网络/模型加载/工具调用失败），每段都**预先录好成功版本**，现场演 + 剪辑备用素材结合，别全程 live。

---

## 五、小红书笔记（完整可发布版）

> **发布平台**：小红书
> **赛道标签**：#手机上的创意AI （TONGYI LAB × Arm）
> **配图**：6–8 张（见下方「配图规划 + AI 提示词」）
> **发布时机**：视频提交前后 1–2 天，错峰发布

---

### 5.1 笔记正文（直接复制可用）

**标题选项（三选一，A/B 测选最戳的）**：

- **A**：做了一个 AI 陪伴 App，评委看完问我：你这是要替代男朋友吗 😂
- **B**：所有 AI 都在给你止疼药，只有这个想帮你把病根拔了
- **C**：我让 AI 跑在手机本地，断网也能陪你聊天｜附实测数据

---

**正文**：

你现在打开任何一个 AI 说"今天好累"

它回你："辛苦了，记得休息 💊"

——但让你累的那个原因，它一个都不知道。

你加了几天班、走了几步路、deadline 还有多远、明天天气怎么样……它全看不见。它只能陪你说说话，**给一颗情感止疼药，然后你的生活还在原地烂着。**

这不是我编的。OpenAI 自己联合 MIT 做了研究，分析了近 4000 万条真实交互，结论是：**用得越深的人越孤独**。把 AI 当朋友的人，受损最重。

所以我和团队做了 **Aura · 奥拉** ☺️

它的理念只有一句：

📌 **真正的陪伴，不是陪你把难过说完，是帮你把让你难过的那件事，办掉。**

---

**Aura 和其他 AI 陪伴 App 到底哪里不一样？**

**① 它有两个大脑 🧠**

别的 App 只有一个云端大脑，离了网就废。Aura 多了一个**跑在你手机里的本地大脑**——

用的魔搭 Qwen3.5-0.8B 模型 + 阿里 MNN 推理引擎，纯 Arm CPU 跑的。**开飞行模式都能聊**，数据永远不出你的手机。

实测数据我贴了 👇（不是 P 的，是真机 benchmark 跑出来的）

**② 它真的懂你，而且记得 📔**

第一次用它的时候它会问你几个问题（爱吃什么、最近在忙什么……），这些偏好会存进本地长期记忆库。过两周你再跟它说"今天好累"，它能接着上次的话往下说——

"这周第四次加班了。你上次说项目月底交，还撑得住吗？"

不是客套的"辛苦了"。是从记忆里翻出来的。

**③ 你锁屏了，它还在"做梦" ✨**

这可能是最不一样的地方——

Aura 有个叫 **Dream Loop** 的后台机制。你锁屏之后，它用本地模型默默整理你最近 7 天的数据：步数、睡眠、聊天记录、情绪变化……然后生成洞察卡片。

比如某天弹一张：*"你最近三周周日情绪都偏低，步数也在跌，要不要出门走走？"*

**每一条洞察都带着真实来源**，你可以点进去看证据。不是 AI 瞎编的，是数据推导出来的。

---

**技术宅可以看的部分 🔧**

我们跑了完整的端侧 benchmark（真机 Realme Dimensity 8200，中端机）：

📊 本地模型 Qwen3.5-0.8B-MNN 实测性能：
• 模型加载：366ms（冷启动不到半秒）
• 流式输出：**40.72 tokens/s**（聊天级流畅）
• 模型大小：438MB（端侧友好）
• 推理后端：Arm CPU，经 OpenCL 对测 CPU 更优

优化前 decode 只有 16 t/s，调了一轮参数干到了 **40.72 t/s（+149%）**。这不是换个模型就有的速度，是针对 Arm 中端机一行行调出来的。

单元测试 497 项全绿 ✅

---

**最后说句真心话**

做 Aura 的初衷很简单：市面上的 AI 陪伴产品，都在拼命让你**上瘾**——最大化你的情感卷入，让你离不开。但它们从不碰你那段真正消耗你的生活。

我们想走另一条路：**越用越被懂，而不是越用越依赖。**

陪伴的落点，从"话说完"变成"事办了"☀️

---

📱 项目已开源，参加的是「手机上的创意 AI 挑战赛」（TONGYI LAB × Arm）

有问题评论区问，看到就回 👇

---

### 5.2 标签池（选 8–12 个）

```
#手机上的创意AI #AI陪伴 #本地大模型 #端侧推理 #MNN #Qwen
#Arm #魔搭ModelScope #开源项目 #独立开发 #Android #AI应用
#情感计算 #DreamLoop #人机交互 #大学生竞赛 #科技创新
```

---

### 5.3 配图规划（共 8 张）

| 序号 | 类型 | 内容描述 | 来源 |
|---|---|---|---|
| **封面** | AI 生成 | 概念主视觉——温暖光晕中的手机，屏幕透出柔和光芒，象征"懂你的存在" | AI 生成（提示词见 ↓） |
| **2** | AI 生成 | "止疼药 vs 办事"概念对比图——分左右两半 | AI 生成 |
| **3** | 自制截图 | App 主界面截图（聊天页 + 底部导航） | 手机截屏 |
| **4** | 自制截图 | 本地模型下载/加载过程 + Logcat 日志 | 手机截屏 |
| **5** | 自制截图 | 工具调用过程（search_memory / health / weather / MCP） | 手机截屏 |
| **6** | 自制截图 | Insight 卡片 + 点开后来源线索 | 手机截屏 |
| **7** | AI 生成 或 自制 | Benchmark 数据可视化（40.72 t/s / +149% / 366ms） | 推荐 AI 生成为信息图风格 |
| **8** | AI 生成 | 尾图——Aura Logo / 口号 / 二维码占位 | AI 生成 |

---

### 5.4 AI 配图提示词（逐张）

#### 图 1：封面 —— 温暖陪伴感主视觉

**用途**：笔记封面，决定点击率。需要在信息流中一眼抓住注意力。

**推荐工具**：通义万相 / Midjourney / DALL·E 3 / 即梦

**中文提示词（适合通义万相 / 即梦）**：

```
一部智能手机悬浮在温暖的琥珀色光晕中心，屏幕发出柔和的淡金白色光芒，
光芒向外扩散形成一圈圈细腻的光波纹，像心跳一样温柔地律动。
光波中隐约浮现出抽象的记忆碎片符号——一本书、一双鞋、一杯咖啡、一片树叶，
这些碎片围绕着手机缓缓旋转，仿佛被某种力量温柔地吸引。
背景是深邃的午夜蓝渐变到暖橙色的夜空，远处有极淡的城市剪影。
整体氛围：安静、温暖、被理解、科技与情感的融合。
风格：微距摄影质感 + 电影级布光，8K超高清，竖版 3:4 比例，
不要出现任何文字和人物面部。
```

**英文提示词（适合 Midjourney / DALL·E 3）**：

```
A smartphone floating at the center of a warm amber glow, screen emitting soft pale golden light,
gentle ripples of light waves emanating outward like a heartbeat.
Abstract memory fragments floating in the light waves — a book, a pair of sneakers, a coffee cup, a leaf —
gently orbiting the phone as if drawn by an invisible warm force.
Background: deep midnight blue gradient to warm orange night sky with faint distant city silhouette.
Mood: quiet, warm, feeling understood, fusion of technology and emotion.
Style: macro photography texture, cinematic lighting, 8K ultra HD, vertical 3:4 aspect ratio,
no text, no human face, no letters --ar 3:4 --v 6.0 --style raw
```

**备选方案（更偏科技感）**：

```
A sleek smartphone resting on a wooden desk in a cozy dimly lit room,
screen showing a gentle chat interface with warm message bubbles.
A soft glow from the screen illuminates a cup of tea and an open notebook beside it.
Tiny luminous particles float between the phone and the notebook,
symbolizing digital-to-physical connection.
Warm color palette: amber, cream, deep teal.
Cinematic still life photography, shallow depth of field, 8K, vertical 3:4 --ar 3:4
```

---

#### 图 2：「止疼药 vs 办事」概念对比图

**用途**：视觉化呈现核心差异点，让读者 3 秒内get到 Aura 的不同。

**中文提示词**：

```
一张左右分割的概念对比插画，竖版 3:4 比例。

左半边（冷色调）：一个孤独的人影坐在黑暗中，周围漂浮着许多
彩色的药丸形状，每个药丸上写着"辛苦了""加油""别太难过了"
之类的安慰话语。药丸表面光滑但给人一种冰冷机械的感觉。
整体色调偏灰蓝，光线暗淡，人物蜷缩。

右半边（暖色调）：同样的人物站起来了，面前有一座由光组成的桥，
桥的另一端连接着真实的生活场景——有阳光的街道、冒着热气的面馆、
绿树成荫的河边小路、日历上圈出的日期。一只发光的手（代表AI）
正在扶着这个人走上这座桥。色调温暖金黄，充满希望。

中间有一条细微的分界线，但右边的光晕微微渗透到左边一点点，
象征"从止疼到解决"的转变。

风格：现代扁平插画风 + 微妙的光效渲染，类似 Apple Keynote 风格，
干净、高级、有设计感。无文字（文字后期叠加）。8K，竖版 3:4。
```

**英文提示词**：

```
A split-concept illustration, vertical 3:4 aspect ratio.

LEFT SIDE (cold tones): A silhouetted figure sitting alone in darkness,
surrounded by floating colorful pill-shaped objects. Each pill glows faintly
with hollow comforting words. Cold gray-blue palette, dim lighting, figure hunched.

RIGHT SIDE (warm tones): The same figure standing upright, facing a bridge made of golden light.
The bridge connects to real life scenes — a sunlit street, a steaming noodle shop,
a tree-lined riverside path, a circled date on a calendar.
A glowing hand (representing AI) gently guiding the person onto the bridge.
Warm golden palette, hopeful and bright.

Subtle dividing line in center, but right side's warm light slightly bleeds into left side,
symbolizing transition from "numbing" to "solving".

Style: modern flat illustration + subtle glow effects, clean Apple Keynote aesthetic,
premium feel, no text, 8K, vertical 3:4 --ar 3:4 --v 6.0
```

---

#### 图 7：Benchmark 数据可视化

**用途**：展示硬核实测数据，建立技术可信度。建议做成简洁的信息图风格。

**方案 A：AI 生成信息图（推荐）**

**中文提示词**：

```
一个简洁现代的技术数据仪表板 / 信息图，深色背景（深蓝灰色 #1a1d29），
展示 AI 模型性能测试数据。中央是一个大的圆形进度条风格的图表，
显示 "40.72 tokens/s" 这个核心数字，用亮青色 (#00d4aa) 高亮。

周围分布着 3-4 个较小的数据卡片，每个卡片包含：
- "模型加载 366ms" 带一个闪电图标 ⚡
- "Prefill 191 TPS" 带一个上升箭头 📈
- "优化提升 +149%" 带一个星星图标 ⭐
- "438 MB 模型体积" 带一个芯片图标 🔷

底部有一行小字标注测试环境："Realme Dimensity 8200 | Arm CPU | 真机实测"

整体风格：类似 GitHub Profile README 统计卡片 + Vercel Analytics 面板的混合，
霓虹风但克制，不用太花哨。科技感、专业、可读性强。
竖版 3:4，8K。数字要清晰可读（示意即可，精确数字后期PS叠加）。
```

**英文提示词**：

```
A clean modern tech performance dashboard infographic, dark background (#1a1d29),
showing AI model benchmark data. Center: large circular gauge displaying
"40.72 tokens/s" highlighted in bright cyan (#00d4aa).

Surrounded by 3-4 smaller data cards:
- "Load 366ms" with lightning bolt icon
- "Prefill 191 TPS" with upward arrow
- "Optimized +149%" with star icon
- "438 MB model size" with chip icon

Footer text: "Realme Dimensity 8200 | Arm CPU | Real Device Test"

Style: mix of GitHub profile stats card + Vercel analytics dashboard,
neon accent but restrained, professional and readable.
Vertical 3:4, 8K. Numbers should be legible (approximate OK, exact values overlaid later).
--ar 3:4 --v 6.0
```

**方案 B：自己用代码 / Figma 做（更精确）**

如果 AI 生成的数字不够清晰，推荐用以下任一方式自制定量准确的信息图：
- **Python matplotlib / plotly** → 渲染为高清 PNG
- **Figma** → 免费模板搜索 "dashboard" 或 "stats card"
- **Canva** → 搜索 "tech dashboard" 模板，填入数据即可导出

关键数据点（必须准确）：

| 展示项 | 数值 | 视觉建议 |
|--------|------|---------|
| 流式生成速度 | **40.72 tokens/s** | 最大字号，核心卖点 |
| Prefill 吞吐 | **191 TPS** | 次级 |
| 模型加载 | **366 ms** | 带 ⚡ 图标 |
| 优化提升 | **+149%** | 用对比箭头强调 |
| 模型体积 | **438 MB** | 小字，补充说明 |
| 测试设备 | Realme Dimensity 8200 | 底部署名 |

---

#### 图 8：尾图 —— Logo + 口号 + CTA

**用途**：最后一图强化品牌记忆，引导互动。

**中文提示词**：

```
一个简约大气的品牌收尾图，竖版 3:4。
正中央是 "Aura · 奥拉" 的品牌名称，使用优雅的衬线字体风格
（不需要真的渲染文字，只需留出中央区域作为文字安全区）。

背景：从顶部的深靛蓝色平滑渐变到底部的暖琥珀色，
象征"从科技到温度"。渐变中有极其细微的噪点纹理，
增加质感和高级感。

画面两侧有非常淡的装饰线条——左侧是一条向上延伸的细线
（象征成长），右侧是一条温柔的曲线（象征陪伴）。
两条线在顶部附近几乎相交但不接触，形成一个抽象的"A"字母暗示。

整体感觉：像一本精装书的封底，或者一张高端剧院的节目单背面。
克制、有呼吸感、值得收藏。8K，竖版 3:4。
中央区域留白供后期叠加文字："Aura · 奥拉" + "别人止于我懂你 Aura 止于我帮你办好了"。
```

**英文提示词**：

```
A minimalist elegant brand closing image, vertical 3:4.
Center area reserved for text (keep blank/clean): "Aura" brand name space.

Background: smooth gradient from deep indigo top to warm amber bottom,
symbolizing "from technology to warmth". Very subtle noise texture for premium feel.

Decorative elements: extremely faint line on left side curving upward (growth),
gentle curve on right side (companionship). Lines nearly meet at top,
forming an abstract hint of letter "A".

Vibe: like the back cover of a hardcover book or a premium theater program.
Restrained, breathable, collectible. 8K, vertical 3:4.
Center area kept clean for text overlay: "Aura · 奥拉" + tagline. --ar 3:4 --v 6.0
```

---

### 5.5 截图拍摄清单（需自己拍的 4 张）

| 图号 | 对应脚本段落 | 拍摄内容 | 要求 |
|---|---|---|---|
| **图 3** | 分镜 #3 / #9 | App 主界面 | 聊天页可见，底部导航栏完整，状态栏时间清晰 |
| **图 4** | 分镜 #4 | 模型加载 | 模型下载页进度条 **或** 已下载完成的模型列表 + 并排 Logcat 加载日志截图 |
| **图 5** | 分镜 #6（高潮段） | 工具调用过程 | 必须能看到 search_memory / query_health_data / get_weather / MCP 至少 2 个工具的调用气泡或状态条 |
| **图 6** | 分镜 #8 | Insight 卡片 | Dream Loop 生成的洞察卡片全貌 + 一张点开后带"来源线索"的详情截图（可拼为一张长图） |

> **截图 Tips**：
> - 用 ADB 截图：`adb shell screencap -p /sdcard/aura_shot.png && adb pull /sdcard/aura_shot.png`
> - 所有截图统一用**真机**，不要模拟器
> - 截图前清掉通知栏敏感信息（微信消息等）
> - 建议截图后用系统自带编辑工具**涂掉**状态栏里不必要的图标，保持干净
> - 如果工具调用过程闪得太快不好截，可以用 `adb screenrecord` 录视频后逐帧抽取

---

### 5.6 发布 Checklist

- [ ] 4 张 App 截图已拍好并简单修图（去敏感信息、统一尺寸）
- [ ] 4 张 AI 生成图已生成并审核质量
- [ ] 全部 8 张图按顺序编号保存
- [ ] 正文已复制到小红书编辑器，排版检查（分段、emoji 显示正常）
- [ ] 标签已从标签池选取 8–12 个添加
- [ ] 话题 **#手机上的创意AI** 已添加（赛事官方要求）
- [ ] 发布时间选在工作日晚 19:00–21:00 或周末 10:00–12:00（流量高峰）
- [ ] 发布后 30 分钟内回复第一条评论（触发算法推荐）

---

*基于 Aura 真实已跑通功能撰写。路线图功能（健身/家居/主动代办）均未纳入演示，避免过度承诺。*
*最后更新：2026-06-22*
