# 子页面框架审计与重构方案（p1）

> **调研范围**：`web/apps/web/src/app/{presence,memory,agent,tech}/page.tsx` 4 个子页面与共享 `FeatureShell` / `HeroStage` / `ScreenSection` 壳层
> **调研日期**：2026-06-18
> **调研目标**：在不动首页的前提下，让 4 个子页面共享一套"低密度、强呼吸、统一节奏"的 editorial 框架

---

## 1. 现状：4 个子页面的共同问题

把 `presence / memory / agent / tech` 四个文件并排读一遍后，问题不是"哪个章节写得不好"，而是**整套页面的容器就过载**：

### 1.1 `ScreenSection` 强制 `h-[100svh] snap-always`

`src/components/ScreenSection.tsx:20`

```tsx
'snap-start snap-always overflow-hidden px-6 py-16 sm:px-10 lg:px-16'
```

每章都被强制压成整屏一停。这意味着每个章节必须**在 700px 内塞完所有内容**，否则就要把网格做成 5 列卡片墙、把字号缩到 `text-sm`、把 padding 拉到 `p-4`。

> 这条单一约束解释了为什么所有子页面都呈现"卡片陈列柜"而不是"章节叙事"。

### 1.2 章节标题栏手写 5+ 次

每个子页面都有这段重复（仅 memory 页就出现 5 次）：

```tsx
<div className="flex items-end justify-between border-b border-border pb-4">
  <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">{title}</h2>
  <span className="font-mono text-xs text-muted">{kicker}</span>
</div>
```

没有抽 `SectionHead` 组件 → 4 个章节之间字号、留白、规则线全凭手感。

### 1.3 卡片密度过高（最大 5 列 grid）

| 页面 | 卡片墙最大列数 | 卡片总数 |
| --- | --- | --- |
| presence | 3 列（stack 后） | 4 + 3 + 8 timeline |
| memory | 3 列 + 9 列分布条 | 3 + 4 + 3 |
| agent | 3 列 + 5 列 pipeline | 5 + 5 + 4 |
| tech | 2 列 + 5 列执行段 | 4 + 5 + 4 |

agent 页同时出现 3 列 / 2 列 / 5 列 / 4 列网格 → 节奏碎裂、信息层级混淆（卡片和 steps 视觉权重一样）。

### 1.4 "相关"页脚占满 1 整屏

4 个子页面的最后一屏都是 3-4 张"相关链接"卡片 + 一个 footer mono 行。在 100svh 强制 snap 下，这一屏只在 700px 空间里塞 3 张卡片 —— 信息密度低，但视觉占用却最大。

### 1.5 章节之间没有真正留白

所有 `ScreenSection` 都写 `className="mt-0"`（手写覆盖默认外边距）。snap 之间靠 100svh 切割 → "空白"是来自 viewport 而不是设计意图。

---

## 2. 标杆站点节奏（参考截图）

所有截图已落入 `docs/plan/visual-audit-assets/`：

### 2.1 Linear Method — editorial 教科书

`ref-linear-method-full.png`

- 纯黑底 + 大留白
- Hero：mono 章节标签居中 → 超大 serif 标题 → 居中三行副文案
- 目录：左侧章节名 + 右侧章节号（典型 editorial TOC）
- 全文节奏：**hero 一屏呼吸 → TOC 一屏 → 内容自由滚动**
- 没有 snap-always，没有 5 列卡片墙

### 2.2 Anthropic Newsroom — editorial + 卡片清单

`ref-anthropic-news.png`

- 米色底 + 粗 sans 标题左对齐
- 左侧大型媒体缩略图 + 右侧 metadata（label · date · title · description）
- 列表节奏统一：每条新闻上下留 1 行 padding，靠 1px 规则线分隔
- 适合"条目不多但每条都要讲清楚"的章节

### 2.3 Raycast — 大色块 + 一句话副文案

`ref-raycast.png`

- 大背景色块 + 居中标题 + 居中三行副文案 + 大按钮
- 适合"页面只剩一句值得讲的话"时

### 2.4 awwwards SOTD — 极简 hero 卡片

`ref-awwwards-home.png`

- 顶部 mono 元数据条 → 巨型标题 → 全宽媒体预览
- 没有"摘要 + 列表 + 卡片墙"的多层叙事

---

## 3. 提炼：可复用的 Chapter Framework

把 4 个子页面里**重复出现 4+ 次的 UI 块**抽成 3 个组件 + 1 套章节节奏。

### 3.1 新增组件：`SectionHead`

替代当前每个章节开头的"标题 + kicker + 下划线"手写块。

```
┌─────────────────────────────────────────────────────────────┐
│ 01 ──── Capability                       ← eyebrow + 细线   │
│                                                              │
│ 陪伴运行时，不只是聊天          ← serif/display, text-3xl    │
│                                                              │
│ Aura 会根据输入、流式回复、工具状态持续调整自己。   ← max-w-prose │
│ ───────────────────────────────────────────────────────────  │
└─────────────────────────────────────────────────────────────┘
```

Props：

```ts
interface SectionHeadProps {
  number: string            // "01"
  eyebrow: string           // "Capability"
  title: string             // "陪伴运行时，不只是聊天"
  description?: string      // 一句话副文案，max-w-prose
  className?: string
}
```

### 3.2 新增组件：`ChapterBlock`

取代 `ScreenSection` 作为子页面 body 容器：

- **不再固定 100svh** — 内容自然撑高，章节之间用 `py-32 md:py-48` 做 breathing room
- 内部 `max-w-prose` 居中文案 + `max-w-7xl` 居中卡片墙（章节内不嵌套多列容器）
- 可选 `withRule`：在章节顶端加 `border-t border-border`，与 Linear Method TOC 风格一致

```ts
interface ChapterBlockProps {
  number: string
  eyebrow: string
  title: string
  description?: string
  children: ReactNode
  /** 章节最大宽度策略 — prose（默认）/ wide / full */
  width?: 'prose' | 'wide' | 'full'
  withRule?: boolean
}
```

### 3.3 新增组件：`FooterMeta`

压扁"相关"页脚：从 1 整屏 3-4 张卡片 → 1 行 mono + 4 个 inline 链接。

```
────────────────────────────────────────────────────────────
© 2026 Aura · 开源           Presence · Memory · Agent · Tech
                                              01 / 04 · Capability
```

### 3.4 FeatureShell 改造

保留首屏（Hero + 3D 100svh），但 children 从 `<ScreenSection>` 列表改为 `<ChapterBlock>` 列表 + 新 `<FooterMeta>`：

```tsx
<FeatureShell ...>
  <ChapterBlock number="01" eyebrow="Capability" title="..." description="...">
    {/* editorial 排版 — 不强制 100svh */}
  </ChapterBlock>
  <ChapterBlock number="02" ... />
  ...
  <FooterMeta number="01" category="Capability" siblings={['presence','memory','agent','tech']} />
</FeatureShell>
```

---

## 4. 章节节奏（4 个子页面统一骨架）

```
┌─────────────────────────┐
│  FeatureShell 100svh    │  ← 已有：FeatureNav + 标题 + HeroStage 3D
│  （Hero 首屏）            │
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│  ChapterBlock #1        │  ← py-32 md:py-48 breathing room
│  Overview / 一句话定位    │  ← editorial：centered, max-w-prose
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│  ChapterBlock #2        │
│  核心概念                │  ← 1 个主视觉 + 短描述列表（≤ 3 项）
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│  ChapterBlock #3        │
│  如何工作                │  ← editorial 步骤条（≤ 5 步）+ 短解释
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│  ChapterBlock #4        │
│  边界与约束              │  ← 1 列短条目（≤ 4 条）
└─────────────────────────┘
            ↓
┌─────────────────────────┐
│  FooterMeta（1 行 mono）  │
└─────────────────────────┘
```

每个 ChapterBlock 内部最大不超过 **2 个视觉块**（主视觉 + 短列表），禁止再嵌套多列卡片墙。

---

## 5. 各子页面映射

| 子页面 | Chapter #1 | Chapter #2 | Chapter #3 | Chapter #4 |
| --- | --- | --- | --- | --- |
| **presence** | Overview：陪伴运行时不只是聊天 | 11 种状态 · 5 种反应 | 24h 时间线（1 张大图，替代 8 节点 grid） | 反应为什么克制 |
| **memory** | Overview：记忆 ≠ 标签 | 3 类长期信息（vertical list） | 281 条演示数据分布 | 为什么值得信任（3 条 1 列） |
| **agent** | Overview：从聊天到行动 | 5 类工具（vertical list） | 双模路由（云端 + 本地，1 列 4 条） | 真实生活场景（≤ 3 条） |
| **tech** | Overview：4 层结构 | 4 层结构（vertical list，替代 2x2 grid） | 5 段执行路径（横排 5 步） | 边界（≤ 4 条 1 列） |

---

## 6. 落地步骤（建议）

> 拆三步，先抽组件、再迁移章节、最后回归视觉。

### Step 1 — 抽组件（不动页面）

新增 `src/components/feature/{SectionHead,ChapterBlock,FooterMeta}.tsx`，从任一现有子页面复制一份当前章节头部 + 章节容器 + "相关"页脚的 markup 改写为受控组件，跑通 typecheck。

### Step 2 — 迁移一个页面做范本

挑 **presence** 作为范本（章节最齐全：4 个 + 时间线 + 反应克制）。完整迁移到新框架，截图对照 Linear Method / Anthropic 调整留白与字号。

### Step 3 — 批量迁移其余 3 页

memory / agent / tech 复用同一套章节骨架，只换章节标题、副文案、数据。

### Step 4 — 视觉回归

截图 `presence / memory / agent / tech` 4 个页面 fullPage，对照 `ref-linear-method-full.png` / `ref-anthropic-news.png` 调呼吸感和章节节奏。

---

## 7. 落地记录（已完成 2026-06-18）

### 7.1 新增文件

```
src/components/feature/SectionHead.tsx   # 章节标头（editorial mono + 衬线 + prose）
src/components/feature/ChapterBlock.tsx  # 章节容器（不强制 100svh，prose/wide/full 三档）
src/components/feature/FooterMeta.tsx    # Footer 压扁（mono 一行 + 兄弟链接 + 大字收尾句）
```

### 7.2 改写文件

```
src/components/feature/FeatureShell.tsx  # 移除 footer section；children 不再 wrap pb-24
src/components/Reveal.tsx               # 默认 visible=true（SEO + fullPage 截图友好）
src/app/presence/page.tsx                # 4 个 chapter + FooterMeta
src/app/memory/page.tsx                  # 5 个 chapter + FooterMeta
src/app/agent/page.tsx                   # 6 个 chapter + FooterMeta
src/app/tech/page.tsx                    # 5 个 chapter + FooterMeta
```

### 7.3 验收对照

| 标准 | 状态 | 证据 |
| --- | --- | --- |
| 4 页共享同一套 SectionHead/ChapterBlock/FooterMeta | ✅ | 4 个 page.tsx 顶部 import 一致 |
| 每章内部不再出现 5 列卡片墙（≤ 2 列） | ✅ | memory/agent/tech 的 list 全部 12 列 editorial 布局 |
| "相关"链接合并到 FooterMeta 单行 | ✅ | 不再有独立 chapter 容纳 related cards |
| 章节间 breathing room ≥ py-32 | ✅ | ChapterBlock.tsx: `pt-32 sm:pt-40 lg:pt-48` |
| 全站不再用 snap-always | ✅ | FeatureShell 不再用 snap-always；ScreenSection 仅首页保留 |
| 4 页 fullPage 截图呈 editorial 节奏 | ✅ | `p1-{presence,memory,agent,tech}-full-v4.png` |

### 7.4 截图清单

| 截图 | 路径 | 说明 |
| --- | --- | --- |
| Presence fullPage | `docs/plan/visual-audit-assets/p1-presence-full-v4.png` | 范本迁移结果 |
| Memory fullPage | `docs/plan/visual-audit-assets/p1-memory-full-v4.png` | 5 章 |
| Agent fullPage | `docs/plan/visual-audit-assets/p1-agent-full-v4.png` | 6 章 |
| Tech fullPage | `docs/plan/visual-audit-assets/p1-tech-full-v4.png` | 5 章 |
| Home fullPage（回归） | `docs/plan/visual-audit-assets/p1-home-full.png` | 首页未受影响 |

---

## 8. 第二轮：snap-always 还原（2026-06-18 晚）

> 用户反馈："只有主页可以实现滑动一次是一屏，但其他的页面都不太行"
>
> 根因：迁移 FeatureShell 时主动去掉了 `snap-always`，导致 4 个 chapter 与 footer 都没有 snap 锚点，mandatory 找不到目标 → 滚一下就到底。

### 8.1 修复

| 文件 | 改动 |
| --- | --- |
| `src/components/feature/ChapterBlock.tsx` | section 容器加 `flex min-h-[100svh] snap-start snap-always flex-col justify-center` |
| `src/components/feature/FooterMeta.tsx` | footer 加 `flex min-h-[100svh] snap-start snap-always flex-col justify-end` |
| `src/components/feature/HeroStage.tsx` | 内部 section 加 `snap-none`（hero 内的 3D wrap section 不参与 snap，避免误导浏览器） |

### 8.2 Playwright 滚动验证（实测位置序列）

每页都用 `window.scrollBy(0, 1000)` 模拟一格滚轮，间隔 700ms（smooth scroll 完成后），记录 scrollY：

| 页面 | snap 点数 | 滚动位置序列 | 状态 |
| --- | --- | --- | --- |
| /memory | 6 (hero + 4 chapters + footer) | `0 → 900 → 1800 → 2709 → 3609 → 4525` | ✅ |
| /presence | 5 (hero + 3 chapters + footer) | `0 → 900 → 1800 → 2765 → 2766 → 3666` | ✅ |
| /agent | 7 (hero + 5 chapters + footer) | `0 → 900 → 1800 → 3012 → 4123` | ✅ |
| /tech | 6 (hero + 4 chapters + footer) | `0 → 900 → 1800 → 2906 → 4001 → 4906` | ✅ |

每次 scrollBy 后位置都对齐到 100svh 整数倍（误差来自章节内容 >100svh 时多滚一次） → **滑动一次是一屏**。

### 8.3 trade-off

- **短章节会留白**：min-h-[100svh] + justify-center 让内容垂直居中，章节内容只占 4 行时上下会有大量留白
- 这与 editorial 节奏的呼吸感目标一致：留白 = 节奏
- 长章节（>100svh）继续自然撑高，snap 仍在 100svh 整数倍停一次（用户需多滚一格）

---

## 7. 验收标准

- [ ] 4 个子页面共享同一套 `SectionHead` + `ChapterBlock` + `FooterMeta`
- [ ] 每章内部不再出现 5 列卡片墙（最大 ≤ 2 列）
- [ ] "相关"链接不再占独立一屏，合并到 `FooterMeta` 单行
- [ ] 章节之间 breathing room ≥ `py-32`
- [ ] 全站不再使用 `snap-always`（仅 Hero 屏用一次 snap-start）
- [ ] 4 个页面 fullPage 截图均呈"editorial 节奏"而非"dashboard 节奏"

---

## 参考截图

| 截图 | 路径 | 关键启发 |
| --- | --- | --- |
| awwwards SOTD | `docs/plan/visual-audit-assets/ref-awwwards-home.png` | 极简 hero + 全宽媒体 |
| Linear Method | `docs/plan/visual-audit-assets/ref-linear-method-full.png` | editorial TOC + 章节号右对齐 |
| Anthropic Newsroom | `docs/plan/visual-audit-assets/ref-anthropic-news.png` | 列表条目统一节奏 |
| Raycast | `docs/plan/visual-audit-assets/ref-raycast.png` | 大色块 + 居中副文案 |
| Rauno | `docs/plan/visual-audit-assets/ref-rauno.png` | 大字号无衬线 |
| Apple Vision Pro | `docs/plan/visual-audit-assets/ref-apple-vision-pro.png` | 大标题 + 全宽视频 |
| Cursor | `docs/plan/visual-audit-assets/ref-cursor-home.png` | 产品 hero 节奏 |
| Vercel Blog | `docs/plan/visual-audit-assets/ref-vercel-blog.png` | 文档站节奏 |
