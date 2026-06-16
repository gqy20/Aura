# Aura iOS 原生气质 UI 改造方案

## 0. 目标

把 Aura 的 Android UI 从“暖色陪伴产品”收敛成“更接近 iOS 原生应用”的视觉与交互气质。

目标不是做 iPhone 复刻，而是得到这些观感：

- 字体更统一，层级更干净。
- 颜色更克制，少品牌染色。
- 间距更稳定，页面更像系统 App。
- 组件更轻，少装饰性卡片。
- 聊天、设置、记忆等页面共享同一套视觉秩序。

## 1. 当前问题

### 1.1 字体没有真正统一

当前主题只定义了颜色，没有自定义 Typography；页面大多直接吃 Material3 默认排版。
首页还在局部使用 `FontFamily.Serif` 做品牌名和主状态文案，这会把气质推向 editorial，而不是 iOS 原生。

### 1.2 颜色偏暖，品牌感过重

当前主色系是绿 + 米白 + 浅卡其，聊天域还单独有暖卡片和浅绿气泡。
这套配色很适合“陪伴感”，但不太像 iOS 的系统气质。

### 1.3 组件形态不够克制

首页顶部操作按钮、一些状态卡、输入区按钮都偏“装饰化”，像独立设计元素，不像系统控件。

### 1.4 页面节奏不够统一

设置、记忆、Onboarding、聊天页的 spacing / 圆角 / 标题层级各自成立，但整体还没形成一套稳定的系统规则。

## 2. 设计原则

1. **无衬线优先**
   全站统一为系统无衬线字体，避免 serif 作为主视觉。

2. **低饱和中性底色**
   大面积背景、surface、列表容器都保持浅灰白系，强调内容而不是背景。

3. **少装饰，多秩序**
   优先用对齐、留白、层级、透明度来表达质感，不靠强烈渐变、光晕、浮夸阴影。

4. **统一页面语法**
   标题、正文、注释、按钮、分组、分隔线都按固定 token 输出。

5. **聊天是主体验，其他页服从它**
   主页、设置、记忆、Onboarding 都应围绕聊天页的秩序重新收束。

## 3. 设计基线

### 3.1 Typography

建议补一套明确的字体层级：

- Large Title：主页主标题、关键场景标题
- Title 1/2/3：页面标题、卡片标题
- Body：正文、输入、对话主体
- Subheadline / Footnote：说明、时间、状态
- Button：操作按钮、导航按钮

规则：

- 主标题不用 Serif。
- 中文和英文统一无衬线。
- 正文行高略放松，标题更紧。
- 少用大写、字距、夸张字重。

### 3.2 Color

建议从“暖陪伴”迁移到“系统中性”：

- `background`：浅灰白
- `surface`：纯白或轻灰白
- `primary`：单一强调色，少量使用
- `secondary/tertiary`：弱化，只保留辅助提示
- 气泡区分靠明度和层级，不靠两套明显品牌色

### 3.3 Shape

- 页面级容器：更接近 iOS grouped card，圆角适中
- 按钮：更轻、更少边框
- 输入框：更平直，少“胶囊化”
- 图标按钮：尽量直接，不额外包厚底

### 3.4 Spacing

建议固定节奏：

- 页面左右 inset：20dp
- section 间距：24dp
- 卡片内边距：14-16dp
- 标题到正文：4-6dp
- 列表行高：固定、稳定、可扫描

## 4. 现有包与复用策略

当前 UI 栈已经足够原生，核心是：

- `Material3`
- `Compose`
- `Navigation Compose`
- `Activity Compose`
- `Lifecycle Compose`
- `Coil`
- `Lottie`（谨慎使用）
- `CameraX`

结论：

1. **基础控件优先用内置**
   `Scaffold`、`TopAppBar`、`Surface`、`TextField`、`Button`、`Switch`、`Chip`、`Dialog`、`Snackbar`、`ListItem` 这类都直接用。

2. **少引入新 UI 包**
   不再额外找 iOS 风格组件库或新的设计系统包。

3. **自定义只留 Aura 特色部分**
   重点保留 Presence、Avatar、聊天气泡细节、少量状态动画。

4. **把可变样式上收为 token**
   字体、颜色、圆角、间距统一收敛到 theme 层，避免散落在页面里。

## 5. 页面级改造建议

### 4.1 Home

- 去掉 Serif 标题。
- 顶部按钮改成更轻的系统 icon 风格。
- Presence 舞台保留，但弱化装饰性光效。
- 状态文案改成更系统化的次级标题。

### 4.2 Chat

- 消息正文统一 body 层级。
- 气泡颜色降饱和，减少品牌色块感。
- 输入栏更像系统底部工具条。
- tool status、时间、附件说明统一做成弱信息层。

### 4.3 Settings / Memory / Onboarding

- 统一成 grouped list 语法。
- 少做漂浮卡片，多做 section。
- 行项对齐方式、分隔线、辅助说明统一。
- Onboarding 更像系统表单，不像营销问卷。

## 6. 推荐实施顺序

### Phase 1：设计 token

- 新增 Typography。
- 整理颜色 token。
- 统一 shape 与 spacing 常量。

验收：

- 全站不再直接依赖 serif 作为主标题。
- 主页面视觉语言开始统一。

### Phase 2：基础组件收束

- 统一 TopAppBar。
- 统一列表 section。
- 统一 chat bubble。
- 统一 input bar。

验收：

- 页面之间不再像不同产品拼起来的。

### Phase 3：关键页面重做

- 优先改 Home。
- 再改 Chat。
- 再改 Settings / Memory / Onboarding。

验收：

- 主页和聊天页已经明显更像 iOS 原生应用。

## 7. 代码量预估

以下为粗略区间，按“尽量复用现有 Material3 组件、少引入新包”的前提估算。

### Phase 1：设计 token

- `Theme.kt` / `Typography.kt` / `Shapes.kt` / `Spacing.kt`
- 预估新增或改动：**150-300 行**

说明：

- 如果只补 typography + shape + spacing，代码量偏低。
- 如果顺手把颜色 token 也系统化拆开，代码量会接近上限。

### Phase 2：基础组件收束

- 统一 `TopAppBar`
- 统一 section/grouped list 容器
- 统一 settings row / info row
- 统一 input bar / bubble 的公共视觉 token
- 预估新增或改动：**300-700 行**

说明：

- 这里看起来是“抽组件”，但实际会减少页面里的重复样式代码。
- 净代码量未必暴涨，更多是结构重排。

### Phase 3：关键页面重做

- `AuraHomeScreen`
- `ChatScreen` / `ChatHeader` / `ChatInputBar` / `MessageBubble`
- `SettingsScreen`
- `MemoryRoomScreen`
- `OnboardingScreen`
- 预估新增或改动：**600-1400 行**

说明：

- 如果只是换 token、调间距和组件样式，偏下限。
- 如果连页面结构一起收成 grouped list / system layout，偏上限。

### 总量判断

整体属于：

- **轻量视觉收敛版**：**800-1200 行改动**
- **完整原生化收束版**：**1200-2400 行改动**

这里的“改动”包含：

- 新增 theme / design token 文件
- 抽公共组件
- 页面内部删旧样式、替换调用点

不包含：

- 新增大规模动画资源
- 引入全新 UI 框架
- 重写业务状态流或导航结构

### 净结果判断

虽然改动行数不小，但如果方向正确，最终会得到：

- 页面文件更短
- 样式更集中
- 后续改单个视觉规则时影响面更可控

也就是说，这波更像“前期多挪一遍，后面少返工很多次”。

## 8. 风险

| 风险 | 表现 | 对策 |
| --- | --- | --- |
| 过度 iOS 化 | 看起来像仿冒 | 保留 Android 的 Material 交互骨架，只调气质 |
| 字体层级不一致 | 页面仍然乱 | 先做 token，再做页面 |
| 颜色太灰 | 失去 Aura 情绪感 | 只保留少量品牌强调色 |
| 组件改太散 | 视觉还是碎 | 优先收基础组件，不逐页补丁 |

## 9. 结论

Aura 现在最需要的不是“再加一点设计”，而是“建立一套更像系统 App 的秩序”。
先收 typography、color、shape、spacing，再改首页和聊天页，效果会最明显。

**Status: Draft（2026-06-16）**
