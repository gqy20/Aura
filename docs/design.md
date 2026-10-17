# Aura 前端设计规范（Android / Compose）

> 提取日期：2026-10-17。本文 token 与组件规范**从当前代码反向提取**为准（`ui/theme/` 与 `feature/chat/` 实查），不凭空发明；行为规范提炼自 `docs/chat-ux-benchmark.md` 已验证的修复批次（2026-08-16 真机验收通过）。
>
> 用途：指导新 UI（含结构化记录流）保持一致的视觉与交互语言。**修改 token 时先更新本文再动代码**；本文与代码不一致以代码为准并回填本文。

---

## 一、设计原则（按优先级）

1. **陪伴感优先于工具感**（聊天面）：AI 消息有身份锚点（mini 头像 / 时间戳 / 日期胶囊），异步过程有人格化的过程叙事（安抚轮播、"还在想"信号）；事务面（记录流）例外，见 §6。
2. **过程可见**：任何异步过程（流式出字 / 工具调用 / 图片提取）必须给出可区分的进行时信号（呼吸 caret / 时间线圆点 / loading 三点）。进行中、暂停、完成三种状态在视觉上必须可区分。
3. **估算数据诚实展示**：所有模型估算数值（热量 / 营养 / 价格）必须标注"估算"并带 confidence 展示策略，禁止以精确口吻呈现；用户确认/修改过的数值与模型估算值在数据上区分（`isEstimate`）。
4. **不硬跳**：滚动一律 `animateScrollToItem`；状态与内容切换用 `AnimatedVisibility` / `AnimatedContent` 过渡。禁止 `scrollToItem(0)` 硬跳与状态硬切。
5. **状态色按使用场景命名，不按色相命名**：见 `ChatStatusColors` 注释——SuccessDot/SuccessText 是两个不同用途的绿，语义优先。
6. **token 单点收敛**：新颜色 / 间距 / 时长进对应 token 文件（`ui/theme/` / `AuraMotion`），禁止在 Composable 里散落 inline `Color(0x…)` 与 magic number。现有代码未完全达标，新代码不允许新增散落。

---

## 二、Token 体系

### 2.1 主题色板（`ui/theme/Theme.kt`，仅 Light）

柔和蓝灰系，整体低饱和、暖白背景。**当前无 darkColorScheme**（已知缺口，见 §7）。

| Token | 色值 | 用途 |
|-------|------|------|
| `primary` | `#4B647C` | 主操作：发送按钮、链接文字、确认按钮、选中态 |
| `primaryContainer` | `#DDE7F2` | 选中背景、徽标底（如 POST_CHAT 蓝色徽标） |
| `secondary` | `#68707D` | 次级操作 |
| `tertiary` | `#6D6A85` | 第三层级强调（低频） |
| `background` | `#F4F5F8` | 页面背景 |
| `surface` | `#FFFFFF` | 卡片、气泡（AI） |
| `surfaceVariant` | `#E9EDF2` | 输入区、分隔、禁用底 |
| `onSurface` | `#171B20` | 主文字 |
| `onSurfaceVariant` | `#66707B` | 次文字、meta、时间戳 |
| `outline` | `#D7DCE3` | 描边、分隔线 |
| `outlineVariant` | `#E6EAF0` | 弱分隔 |

### 2.2 聊天域表面色（`ui/theme/ChatColors.kt`，独立于 colorScheme）

聊天域专用表面色不进 MaterialTheme.colorScheme，避免污染主题契约。

| Token | 色值 | 用途 |
|-------|------|------|
| `CardSurface` | `#FFFFFF` | 卡片统一底色（经 `ChatCardSurface` 使用） |
| `InputSurface` | `#F9FAFC` | 输入栏底色 |
| `BubbleAi` | `#FFFFFF` | AI 气泡 |
| `BubbleUser` | `#E8EEF7` | 用户气泡（唯一带彩表面） |

### 2.3 状态语义色（`ui/theme/ChatStatusColors.kt`）

hard semantic，不进 colorScheme。具体语义由调用方按上下文赋予，色值统一。

| Token | 色值 | 用途 |
|-------|------|------|
| `SuccessDot` | `#3FA86B` | 成功状态点：8dp Canvas 圆点、ToolStatusPill SUCCEEDED、loading 三点主色 |
| `SuccessText` | `#2E7D32` | 成功文字/标签（比 Dot 深，满足 WCAG AA 文字对比度） |
| `Warning` | `#E5A100` | 琥珀：警告 / 进行中 / 未就绪 / 重要 |
| `Unknown` | `#B7B0A4` | 中性灰：未知 / 禁用 / 空状态（"信息不足，中性等待"） |

规则：**需要文字对比度的场景一律用 Text 变体**；Dot 变体只用于 Canvas 绘制与图形元素。新增状态色先问"是不是这四个语义的新用法"，是则复用，不是才加 token。

### 2.4 间距（`ui/theme/Spacing.kt`，`LocalCompanionSpacing` CompositionLocal）

| Token | 值 | 建议映射 |
|-------|-----|---------|
| `xs` | 4dp | 图标与文字内隙、chips 组内 |
| `sm` | 8dp | 强相关元素组内（圆点+文案）、时间线行内 |
| `md` | 12dp | 卡片内边距、列表行内边距 |
| `lg` | 16dp | 卡片间、气泡间、页面水平边距 |
| `xl` | 20dp | 区块内分组间 |
| `section` | 24dp | 区块间、页面级分组 |

规则：新布局用这六档，禁止 5dp/10dp/18dp 类自由值；取档位就近不内插。

### 2.5 形状（`ui/theme/Shapes.kt`）

| Token | 值 | 用途 |
|-------|-----|------|
| `small` | 10dp | pill、chips、小徽标、时间线节点 |
| `medium` | 16dp | 卡片、气泡、Sheet（`ChatCardSurface` 默认） |
| `large` | 22dp | 全屏级容器、大面板、日期胶囊外的浮层 |

### 2.6 字体（`ui/theme/Typography.kt`）

MiSans（Xiaomi HyperOS 默认字体，免费可商用），GB2312 子集化打包三字重（Normal/Medium/SemiBold），生僻字自动回退系统 SansSerif。全部 letterSpacing 0。

| Style | Size/LineHeight | Weight | 典型用途 |
|-------|-----------------|--------|---------|
| `displayMedium` | 32/38 | SemiBold | 主屏大数字（今日热量等汇总） |
| `headlineSmall` | 24/30 | Medium | 页面标题 |
| `titleLarge` | 20/26 | SemiBold | 区块标题 |
| `titleMedium` | 18/24 | SemiBold | 卡片标题、Sheet 标题 |
| `titleSmall` | 16/22 | Medium | 行标题、设置分组 |
| `bodyLarge` | 17/24 | Normal | 聊天正文（气泡主文字） |
| `bodyMedium` | 16/23 | Normal | 一般正文、表格单元格 |
| `bodySmall` | 14/20 | Normal | 辅助说明、meta 文字 |
| `labelLarge` | 15/20 | Medium | 按钮文字 |
| `labelMedium` | 13/18 | Medium | pill、状态行、时间戳 |
| `labelSmall` | 12/16 | Medium | 徽标、注释、单位（kcal/g） |

### 2.7 动效时长与曲线（`feature/chat/AuraMotion.kt`）

| Token | 值 | 用途 |
|-------|-----|------|
| `ShortMs` | 180ms | 小元素进出（chips、圆点、pill 切换） |
| `MediumMs` | 260ms | 常规过渡（`emphasizedTween`：按钮 morph、卡片淡入） |
| `LongMs` | 420ms | 大面积过渡（`gentleTween`：Sheet、错误卡）、loading 呼吸周期 |

- 曲线统一 `FastOutSlowInEasing`（tween 场景）；颜色过渡统一 `colorSpring`（StiffnessLow + NoBouncy，无弹跳缓变）。
- **流式节奏**（`SendMessageUseCase` 出字队列，勿改）：33ms ticker、首包 16 字符立即上屏、积压 ≥96 字符时每 tick 排空 1/4。
- **呼吸动画**（无限循环类）：caret 呼吸竖条、时间线运行中圆点、loading 三点一律用 `LongMs` 周期 + Restart 模式，不用 Bounce。
- PerformancePill（tok/s）仅 debug 构建可见，release 禁止。

---

## 三、Presence 配色语义（`feature/chat/HomePresencePalette.kt`）

主屏角色氛围按 `PresenceMode` 映射 4 色（backgroundTint / glow / ring / spark），全部经 `colorSpring` 动画过渡：

| Mode | 主 glow | 场景 |
|------|---------|------|
| HAPPY | `#FFD884` 暖金 | 高兴 |
| THINKING / SEARCHING | `#9BEAE5` 青绿 | 思考、查工具 |
| REMEMBERING | `#CDB4F6` 紫 | 保存记忆 |
| SAD / TIRED / SLEEPING | `#AFC8E8` 灰蓝 | 低落、休眠 |
| ERROR | `#E9A39D` 珊红 | 错误（含恢复反应） |
| LISTENING / SPEAKING / IDLE | `#A8E7DE` 青绿+暖金 spark | 常态 |

反应强度（`haloBoost`）：ERROR_RECOVER 0.9 > MEMORY_SPARK 0.7 > SEARCH_SWEEP 0.55 > RETURN_BLINK 0.38 > TOUCH_NUZZLE 0.28。新反应类型按"用户可感知重要性"插队到这个梯度里，不另起体系。

---

## 四、组件规范（已验证模式，新 UI 复用）

- **`ChatCardSurface`**（`ui/theme/`）：统一卡片容器（medium 形状 + `ChatColors.CardSurface`）。默认选择；变体（自定义 shape / clickable / 非 fillMaxWidth）才裸用 `Surface`。
- **消息气泡**：AI = `BubbleAi` + 左侧 28dp mini 头像（流式 SPEAKING 帧 / 完成 IDLE 帧）+ "⋮"操作行旁 HH:mm；用户 = `BubbleUser`。跨天插"今天/昨天/M月d日"胶囊分隔。
- **流式 caret**：`StreamingMessageText` 尾部独立 Composable 呼吸竖条，不污染 markdown 解析——新"进行中"文本场景照抄这个模式。
- **工具时间线**：气泡下方有序步骤列表（运行中呼吸圆点 → ✓/✗ + 动词化文案 + ≥1s 耗时），点步骤开详情 Sheet。
- **意图段**：`intentText` 弱化样式渲染在正文上方（浅色、小于正文），多轮工具调用按轮累积。
- **安抚轮播（`ThinkingHintCarousel`）**：首字到达前 2/5/12/25/50s 六档文案升级——**仅聊天面**，事务面禁用（见 §6.4）。
- **loading 三点（`AuraLoadingIndicator`）**：确定性等待的通用指示，18dp 默认，带 contentDescription。
- **空态**：建议 prompt chips（点击只预填输入框，不直接发送）。
- **错误卡**：持久、可关闭、可重试，`AnimatedVisibility` 进出；不用一次性 Snackbar 承载可重试错误。
- **Markdown 链接**：`[label](url)` → `LinkAnnotation.Url`（primary 色 + 下划线，http 前缀校验）。

---

## 五、聊天面（陪伴面）行为规范

从 `chat-ux-benchmark.md` 三批修复提炼，真机验收通过，**不回退**：

1. **滚动 pin 策略**：只有真实拖动（`UserInput` source）解除置底；离底 32px 自动回 pin；IME 动画结束补滚；发送/回到底部用 `animateScrollToItem`，流式跟随保持即时滚动。
2. **三态按钮 morph**：发送/停止/图片处理用 `AnimatedContent` 缩放淡入切换，不硬切。
3. **输入区**：回车换行（`ImeAction.Default`），发送只走按钮；发送失败自动回填输入文本与图片（用户已开始打下一条时不覆盖）。
4. **身份锚点**：AI 消息常驻 mini 头像 + 时间戳 + 日期胶囊（§4）。
5. **瞬态 UI 不持久化**：时间线 / 地图卡片 / perf pill 等 UI 态重启后不恢复，DB 只存正文；持久化 key 稳定（transient id → `persistedId`，不闪断）。

---

## 六、记录流（事务面）规范 —— 2026-10-17 新增

结构化生活记录（饮食 / 采购）的专用交互面。**定位：记录是动词不是模式**——快记流是 NavHost 新路由，不是全局模式切换；聊天兜底（P3 agent 工具）与快记流共用 Extractor 与数据层，确认组件两种容器复用（全屏页 / 聊天卡片）。

### 6.1 效率指标

**30 秒闭环**：打开入口 → 选图 → 表格确认（份量 chips 一击）→ 存 → 返回原处。任何超出该流程的交互（寒暄、确认弹窗、多余步骤）都是设计缺陷。

### 6.2 确认表格组件（`RecordConfirmTable`，待实现）

结构自上而下：图片缩略（可替换/移除）→ 域切换（餐食/采购，提取器自动分类，可手动纠正）→ 行卡片列表 → 汇总行 → 保存按钮（primary）。

**饮食域行**：菜名（可编辑）｜份量 chips（小/中/大，一击切换并按乘数联动重估营养）｜热量 kcal｜蛋白/碳水/脂肪 g｜删除行。汇总行：总热量 + 三大营养素总和 + "估算"徽标。

**采购域行**：品名｜品牌｜数量+单位（个/板/盒/斤/袋）｜单价｜删除行。汇总行：总件数 + 总金额。

交互规则：

- 行默认**只读展示**，点击行进入编辑态——不做"一上来全是输入框"。
- 所有估算数值右上角"估算"角标；用户改过的字段角标消失并置 `isEstimate=false`。
- 数值钳制越界（单项热量 >3000kcal 等）行内警示，不静默修正。
- 单位用 `labelSmall`（kcal / g / 元），数值用 `bodyMedium`，菜名/品名用 `titleSmall`。

### 6.3 状态设计

| 状态 | 呈现 |
|------|------|
| 提取中 | 图片 + "Aura 正在看图…" + `AuraLoadingIndicator`（可带轻 presence 气息，但文案确定性） |
| 提取失败 | 降级为空白表格手填 + 重试按钮；**不能让提取失败挡住记录** |
| 确认入库 | 保存后返回入口处，主屏卡片数字即时更新 |

### 6.4 与聊天面的差异约束（刻意的）

- **无寒暄、无人格文案**：事务面文案全部确定性（"提取中/已保存/15 个鸡蛋"），安抚轮播、意图段等聊天面机制禁用。
- 进度反馈用 loading 三点，不用安抚文案轮播。
- 入口：主屏"记一笔"卡片（`ChatCardSurface` 规格）+ Android App Shortcut 长按图标深链。聊天输入栏相机按钮**不改动**（P3 工具化后再考虑分流）。
- 离线（云端不可达）：入口置灰 + 一行状态提示，或降级手填——不做假提取。

---

## 七、已知缺口与演进

- **无 darkColorScheme**：当前仅 Light；引入暗色前需先补全 `ChatStatusColors` 在暗色下的对比度验证。
- **token 覆盖度不全**：聊天域大量颜色仍散落（Presence 相关集中在 `HomePresencePalette` 尚可，气泡/卡片已收敛）；新代码遵守 §一.6，存量渐进收敛。
- **MiSans 子集构建**：`Typography.kt` 注释指向 `tmp-fonts/build_gb2312.py`（工作区已删，历史提交保留）；补生僻字场景需从 git 历史恢复脚本重跑子集。
- **web 站点**（`web/apps/web`）不在本文范围，需要时另立章节。
