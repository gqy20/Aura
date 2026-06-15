# Aura 项目网站方案

> Archived on 2026-06-15. Kept for historical design context; no longer a current planning entry.
>
> 产品叙事旗舰 + 极致动效 + Vercel 部署 / 调研与决策文档
> 起草：2026-06-15 / 待评审

---

## 0. TL;DR

为 Aura（Android AI 陪伴应用）建设**产品叙事旗舰站**，主舞台采用"深色 + Apple 滚动 + 3D 章节"组合，文档站采用 Anthropic 米色风，整体部署到 Vercel Free 层，仓库与 Android 项目同 repo（`web/` 子目录）。

**核心原则：能用现成轮子就不自己写。** 本文档每个功能点都对应一个或多个成熟库，确保工程量集中、风险可控、交付可预期。

---

## 1. 目标与定位

### 1.1 目标（按优先级）

| 优先级 | 目标 | 衡量 |
|--------|------|------|
| P0 | 让访客 30 秒内理解 Aura 是什么、为什么值得下载 | Hero 区 LCP、滚动完成率、CTA 点击率 |
| P0 | 配套 Android 应用的下载入口 | 落地页 → APK / GitHub Release 路径 |
| P1 | 服务开发者/贡献者 | 文档站可读、可搜索、有示例 |
| P2 | 长期 SEO + 社区建设 | Blog/Changelog 自动生成 |
| P3 | 在线体验（Web Demo） | 暂不在 P0-P5 范围 |

### 1.2 目标用户

- **主要**：对 AI 陪伴类产品感兴趣的潜在用户（最终下载 Android APK）
- **次要**：对 Aura 技术栈感兴趣的开发者（读文档、贡献代码）
- **第三**：媒体/合作方（看品牌叙事、截 Hero 图）

### 1.3 不在范围内

- Web 版完整聊天功能（涉及后端 + LLM 推理 + 数据持久化）
- 用户登录 / 账号体系
- 在线支付 / 订阅
- 服务端渲染复杂业务页面

> 如未来要做 Web Demo，单独成 `P6` 阶段评估。

---

## 2. 设计调研：参考站实测数据

> 用 Playwright 浏览器实测 + 设计令牌提取，2026-06-15 抓取。

### 2.1 7 个参考站一手数据

| 站点 | 主题 | 背景 | 主字 | H1 尺寸/字重 | canvas / video | 风格标签 |
|------|------|------|------|------|------|------|
| Linear.app | 深色 | `#08090A` | Inter Variable | 64px / 510 | 0 / 0 | 极简排版、慢节奏 |
| Vercel.com | 浅色 | `#FAFAFA` | Geist | 48px / 600 | 1 / 0 | 3D 元素、渐变 |
| Cursor.com | 米色 | `#F7F7F4` | CursorGothic | 26px / 400 | 0 / 1 | 视频叙事、自研字体 |
| Apple iPhone | 纯白 | `#FFFFFF` | SF Pro Display | 80px / 600 | 0 / 1 | 超大字号、滚动 |
| Replika.com | 纯白 | `#FFFFFF` | Pangea | 110px / 700 | 0 / 2 | 情感向、轮播文案 |
| Stripe.com | 浅色 | 透明 | sohne-var | 48px / 400 | 2 / 0 | 3D 卡片、排版 |
| Anthropic.com | 米色 | `#FAF9F5` | Anthropic Serif | 62.7px / 700 | 0 / 0 | 衬线、慢节奏 |
| Rauno.me | 浅灰 | `#EDEDED` | X 自研 | 32px / 500 | 0 / 0 | 极简、6000px 单页 |

### 2.2 6 种风格原型提炼

| 标签 | 代表 | 核心手法 | 适用 |
|------|------|---------|------|
| 🅰 Linear | Linear | 深色 + 排版 + 183 SVG 矢量 | 主舞台、核心叙事 |
| 🅱 Apple | Apple | 纯白 + 80px 大标题 + 视频滚动 | 特性展示、产品发布 |
| 🅲 Vercel | Vercel/Stripe | 浅色 + 3D canvas + 渐变 | 特性页、可视化 |
| 🅳 Anthropic | Anthropic | 米色 + 衬线 + 零装饰 | 文档站、manifesto |
| 🅴 Replika | Replika | 白底 + 110px 情感文案 + video | Hero 标题区 |
| 🅵 Rauno | Rauno | 极简 + 6000px 单页 + X 自研字体 | Changelog、About |

### 2.3 最终配方

| 区域 | 风格原型 | 关键借鉴 |
|------|---------|---------|
| `/` Hero | 🅰 Linear + 🅴 Replika | 深色 + 110px 轮播文案 + 183 SVG 矢量动画 |
| `/` 滚动叙事 | 🅱 Apple | 大字号 + 滚动驱动 + 视频替代（Canvas/CSS 动画） |
| `/presence` | 🅲 Vercel | 3D 状态机 + 渐变背景 + WebGL |
| `/memory` | 🅲 Stripe | 3D 卡片 + 时间线 |
| `/local-llm` | 🅲 Vercel + 🅳 Anthropic | 3D 粒子流 + 米色排版 |
| `/docs` | 🅳 Anthropic | 米色 + 衬线 + 慢节奏 |
| `/changelog` | 🅵 Rauno | 单页 + 极简 + JetBrains Mono |
| `/manifesto` | 🅴 Replika + 🅵 Rauno | 大字体情感文案 + 慢节奏 |

---

## 3. 设计语言

### 3.1 主舞台（深色旗舰）

| 维度 | 规格 |
|------|------|
| 背景 | `#08090A`（深色，参考 Linear） |
| 主文字 | `#F7F8F8`（近白） |
| 次文字 | `rgba(247,248,248,0.6)` |
| 强调色 | （**待用户提供**） |
| 边框 | `rgba(255,255,255,0.08)` |
| 网格 | 12 列 / 80px gutter / 1440px max-width |
| 留白 | section 间距 ≥ 200vh |

### 3.2 文档站（米色 Anthropic 风）

| 维度 | 规格 |
|------|------|
| 背景 | `#FAF9F5`（米色，参考 Anthropic） |
| 主文字 | `#141413`（近黑） |
| 次文字 | `rgba(20,20,19,0.6)` |
| 边框 | `rgba(20,20,19,0.1)` |
| 主字 | Anthropic Sans / 衬线副字 |
| 代码 | JetBrains Mono |
| 行宽 | 720px（阅读舒适） |

### 3.3 字体策略

| 角色 | 主舞台字体 | 文档站字体 |
|------|----------|----------|
| 标题 | Geist Sans Variable / Inter Variable | Anthropic Sans |
| 正文 | Geist Sans / Inter Variable | Anthropic Sans |
| 强调 | Berkeley Mono / JetBrains Mono | JetBrains Mono |
| 中文 | 思源黑体 / HarmonyOS Sans | 思源宋体 / Noto Serif SC |

> **现成轮子**：`next/font` 内置自托管 + `fontsource` 离线包，避免 CDN 依赖。

### 3.4 动效节奏

| 阶段 | 时长 | 缓动 |
|------|------|------|
| Hover | 150ms | ease-out |
| 进场 | 400-600ms | cubic-bezier(0.22, 1, 0.36, 1) |
| Scroll 段落切换 | 800-1200ms | power3.out |
| 大背景过渡 | 1200-1800ms | expo.out |

> **现成轮子**：缓动曲线用 `motion` 内置 + `gsap` 的 `CustomEase`，不自己手写 cubic-bezier。

---

## 4. 技术栈：现成轮子清单

> 核心原则：**先在 npm/registry 上找成熟方案，没有再考虑自写**。每个功能点都有 1-3 个候选。

### 4.1 核心框架

| 用途 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 框架 | **Next.js 15 (App Router)** | 生态最广、SSR/SSG/ISR 全、Vercel 一等公民 | Astro（内容站更快但生态窄） |
| 语言 | **TypeScript 5.x** | 类型安全、与 Android 项目风格一致 | — |
| 样式 | **Tailwind v4** | 设计 token 一等公民、零运行时 | CSS Modules |
| 包管理 | **pnpm** | monorepo 友好、磁盘省 | npm |
| monorepo | **Turborepo** | Vercel 官方推荐、增量构建 | Nx |

### 4.2 动效

| 用途 | 选择 | 理由 | 备选 |
|------|------|------|------|
| React 动效 | **motion** (formerly Framer Motion) | React 一等公民、声明式 API、bundle 友好 | React Spring |
| 时间线/滚动 | **GSAP + ScrollTrigger** | 行业标准、性能强、SplitText 文字动效 | motion 自带 useScroll |
| 平滑滚动 | **Lenis** | 业界主流、active 维护、Vercel/Linear 都用 | smoothscroll-polyfill |
| 文字逐字 | **GSAP SplitText**（付费）/ **Splitting.js** | Splitting.js 免费开源 | 自写字符 span |
| 磁性光标 | **@react-three/cursor** 或自写 ~30 行 | 简单、依赖小 | cursor-effects |
| 视差 | GSAP ScrollTrigger 内置 | 不需要额外库 | — |

**实测确认**：
- motion.dev: "Motion (prev Framer Motion) is a fast, production-grade animation library for React, JavaScript and Vue. Build smooth UI animations at a tiny footprint."
- gsap.com: "Animate Anything - A wildly robust JavaScript animation library built for professionals."
- lenis.dev: "Lenis is a lightweight, performant smooth scroll library for the web."

### 4.3 3D

| 用途 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 3D 引擎 | **Three.js** | 事实标准 | Babylon.js |
| React 绑定 | **@react-three/fiber** | 声明式、React 友好 | react-three-raw |
| 工具集 | **@react-three/drei** | 大量现成 helper（OrbitControls、Environment、MeshDistortMaterial） | 自写 |
| 后处理 | **@react-three/postprocessing** | 辉光、景深、色彩 | 自写 shader |
| Mesh Gradient | **`@paper-design/shaders`** 或 **`mesh-gradient`** | 渐变背景现成 | 自写 GLSL |

### 4.4 UI 组件

| 用途 | 选择 | 理由 | 备选 |
|------|------|------|------|
| 组件库 | **shadcn/ui** | 复制源码不绑定、可定制、设计 token 化 | Radix UI 裸用 |
| 基础原语 | **Radix UI** | 无样式、可访问性强 | Headless UI |
| 图标 | **Lucide** | 2000+ 图标、tree-shake 友好 | Phosphor |
| 命令面板 | **cmdk** | shadcn 默认、键盘友好 | kbar |
| 表单 | **react-hook-form + zod** | 业界标准 | Formik |
| 状态管理 | **Zustand** | 轻量、无 boilerplate | Jotai |

**实测确认**：
- ui.shadcn.com: "A set of beautifully designed components that you can customize, extend, and build on. Start here then make it your own. Open Source. Open Code."

### 4.5 内容 / MDX

| 用途 | 选择 | 理由 | 备选 |
|------|------|------|------|
| MDX 渲染 | **`fumadocs-mdx`** 或 **`next-mdx-remote`** | fumadocs 文档站最完整；next-mdx-remote 更灵活 | contentlayer（已停维护） |
| 文档框架 | **fumadocs** | Next.js 文档站最成熟方案，内置搜索/侧边栏 | Nextra、Docusaurus（独立框架） |
| Markdown 增强 | **remark-gfm** + **rehype-pretty-code** | GFM 表格/任务列表 + 代码高亮 | shiki |

### 4.6 字体 / 排版

| 用途 | 选择 | 理由 |
|------|------|------|
| Web 字体 | **next/font/google** + **next/font/local** | 零 CLS、按需子集 |
| 离线包 | **@fontsource-variable/inter** 等 | 避免 Google Fonts CDN |
| 中文 | **fontsource.noto-sans-sc** | 同上 |

### 4.7 搜索

| 用途 | 选择 | 理由 |
|------|------|------|
| 静态搜索 | **Pagefind** | 构建后索引、零运行时、免费、fumadocs 集成 |
| 客户端搜索 | **FlexSearch** | 体积小、快 |

### 4.8 工具链 / 部署

| 用途 | 选择 | 理由 |
|------|------|------|
| 部署 | **Vercel** | 项目要求 |
| 域名 | 暂用 `*.vercel.app` | 后续可绑 |
| CI | Vercel 内置 | 不需要自建 |
| 图像优化 | `next/image` | 内置 |
| 分析 | **Vercel Analytics** | 轻量、隐私友好 |
| 错误监控 | Sentry（可选） | 暂不需要 |
| TypeScript | tsx / tsc | 标配 |
| Lint | ESLint + Prettier | 标配 |
| 提交规范 | **commitlint + Husky** | Android 端已有则对齐 |
| 包更新 | **Renovate** | 自动 PR |

### 4.9 显式不做自写的清单

| 功能 | 不自写的原因 |
|------|------|
| 滚动条样式 | 用 `lenis` 内置 |
| 文字动效 | 用 Splitting.js 或 GSAP SplitText |
| 渐变背景 | 用 `@paper-design/shaders` |
| 粒子效果 | 用 `@react-three/drei` 的 `Sparkles` |
| 鼠标视差 | 用 GSAP ScrollTrigger 的 `parallax` |
| 视口检测 | 用 `motion` 的 `useInView` |
| 响应式断点 | 用 Tailwind 默认值 |
| 焦点环 | 用 Tailwind `focus-visible:` |
| 暗色模式 | 用 `next-themes` |
| SEO meta | 用 `next/metadata` + `next-seo`（可选） |
| Sitemap | 用 `next-sitemap` |

> 自写的部分只剩：业务组件（AuraCard、AuraHero）、布局 shell、设计 token 配置文件。

---

## 5. 页面架构

### 5.1 信息架构

```
/                       首页：Hero + 滚动叙事 + 特性概览 + CTA
/presence               Presence Layer 深度页
/memory                 Memory 系统时间线
/local-llm              Local LLM 技术剖析
/docs                   文档首页（自动从 docs/ 生成侧边栏）
  /docs/getting-started
  /docs/architecture
  /docs/koog
  /docs/roadmap
  /docs/api
/changelog              git log 自动生成
/manifesto              品牌叙事
/about                  关于
/download               APK / GitHub Release
```

### 5.2 路由

Next.js App Router：

```
web/src/app/
  layout.tsx            全局 layout（含字体、theme、analytics）
  page.tsx              / 首页
  presence/page.tsx
  memory/page.tsx
  local-llm/page.tsx
  docs/
    layout.tsx          文档站 layout（米色主题 + 侧边栏）
    page.tsx            文档首页
    [...slug]/
      page.tsx          MDX 动态路由
  changelog/page.tsx
  manifesto/page.tsx
  about/page.tsx
  download/page.tsx
  globals.css
```

### 5.3 关键页面文字版 wireframe

#### 首页 Hero 区

```
┌─────────────────────────────────────────────────┐
│ [logo]    presence  memory  docs      [GitHub]  │  ← nav 60px
├─────────────────────────────────────────────────┤
│                                                 │
│           The AI companion                      │  ← 110px Replika 风
│           that lives with you.                  │     轮播文案
│                                                 │
│           [Start →]  [Watch demo]               │  ← 磁吸按钮
│                                                 │
│                                                 │
│              ╭─────────╮                        │
│              │ 3D Aura │  ← 旋转手机 + 角色      │  ← 3D canvas
│              │  model  │     （R3F + Drei）     │
│              ╰─────────╯                        │
│                                                 │
│   ↓ scroll                                      │  ← 提示
├─────────────────────────────────────────────────┤
│   (滚动驱动段落 1) Presence 实时状态            │  ← 80px Apple 风
│   (滚动驱动段落 2) Memory 时间线                │
│   (滚动驱动段落 3) Local LLM 推理可视化          │
│   (滚动驱动段落 4) 数据：41 测试 / 7 模块        │
│   CTA: Download Aura (Android)                  │
└─────────────────────────────────────────────────┘
```

#### `/presence` 深度页

```
┌─────────────────────────────────────────────────┐
│  Presence Layer                                 │  ← 标题 80px
│  The soul of Aura                               │
├─────────────────────────────────────────────────┤
│  ┌──── 3D 状态机 (R3F) ────┐   文字描述：       │
│  │                         │   - 状态推导逻辑    │
│  │   ⊙ Idle  Calm  Focus   │   - 反应策略        │
│  │     (颜色/形态变化)     │   - 离线衰减         │
│  │                         │                     │
│  └─────────────────────────┘   [查看源码 →]     │
├─────────────────────────────────────────────────┤
│  状态时间线（24h）                              │
│  ──●──────●──────●─────●─────                   │
│  07:00   12:00   18:00   22:00                  │
├─────────────────────────────────────────────────┤
│  相关：Memory / Local LLM / Reminder            │
└─────────────────────────────────────────────────┘
```

#### 文档站 `/docs`

```
┌─────────────────────────────────────────────────┐
│ [Aura Docs]                       [搜索 ⌘K]    │  ← 米色 top bar
├──────────┬──────────────────────────────────────┤
│ Sidebar  │  Getting Started                     │  ← 衬线标题
│          │                                      │
│ Overview │  Aura 是一个...                       │  ← 正文
│ Getting  │                                      │
│  Started │  ## 快速开始                          │
│ Arch.    │  ```bash                             │
│ Koog     │  ...                                 │
│ Roadmap  │  ```                                 │
│ API      │                                      │
│          │  ## 下一步                            │
│          │  ...                                 │
└──────────┴──────────────────────────────────────┘
```

---

## 6. 动效与 3D 清单

### 6.1 动效清单

| 效果 | 实现 | 库 | 备注 |
|------|------|----|------|
| Hero 文字逐字符入场 | SplitText + 时间线 | GSAP SplitText（付费）/ Splitting.js | 0.5s/字 |
| 滚动驱动叙事 | ScrollTrigger | GSAP | 8 段落 |
| 段落背景渐变切换 | 视口进入/离开 | motion | ease 1200ms |
| 磁性光标 | 距离阈值 + transform | 自写（~30 行） | 移动端禁用 |
| 数字滚动 | useInView + animate | motion | 0 → 41 |
| View Transitions | 内置 | Next.js | 路由切换 |
| Lenis 平滑滚动 | 全局 | Lenis | 与 GSAP 集成 |
| Mesh Gradient 背景 | shader | @paper-design/shaders | Hero 区域 |
| 进场段落 | useInView | motion | stagger 80ms |

### 6.2 3D 元素清单

| 元素 | 几何 | 着色器 | 用途 |
|------|------|--------|------|
| 手机 360° 展示 | RoundedBox + Screen Quad | 自定（屏幕 UI） | Hero |
| Presence 状态机 | 球体/Icosahedron | MeshDistortMaterial | /presence |
| Memory 时间线 | 节点球 + LineSegments | Standard + Glow | /memory |
| LLM 推理粒子流 | 粒子 + 流向场 | 自定 | /local-llm |
| Hero 背景 | 球 + ShaderMaterial | Mesh gradient | 首屏 |

> 全部几何体 + 着色器，**不依赖任何外部 .glb / Rive 资源**。

### 6.3 移动端降级

| 桌面端 | 移动端 |
|--------|--------|
| 3D 完整渲染 | 静态截图 + CSS 渐变 |
| GSAP 复杂时间线 | motion 基础 fade/slide |
| 磁性光标 | 默认光标 |
| Lenis | 原生滚动 |

> 用 `next/dynamic` + `isMobile` 标志位切换。

---

## 7. 实施计划

### 7.1 阶段总览

| 阶段 | 周期 | 关键交付 | 验证 |
|------|------|---------|------|
| **P0 基础设施** | 3-4 天 | Next.js 脚手架 + 部署占位 | `aura-xxx.vercel.app` 看到空壳首页 |
| **P1 Hero + 滚动叙事** | 7-10 天 | Hero + 4 段滚动叙事 + 3D 主视觉 | 首页可看、移动端降级 |
| **P2 特性深度页** | 7-10 天 | /presence /memory /local-llm | 三页可用 |
| **P3 文档站** | 3-4 天 | fumadocs + MDX + 搜索 | 文档可读、可搜 |
| **P4 Changelog + Manifesto + About** | 2-3 天 | 自动生成 + 长读 | 内容完整 |
| **P5 性能 / SEO / a11y** | 3-4 天 | LCP < 1.5s / sitemap / 键盘可达 | Lighthouse 95+ |
| **总计** | **5-6 周** | | |

### 7.2 P0 基础设施详细

- 创建 `web/` 子目录，pnpm workspace
- 初始化 Next.js 15 + TypeScript + Tailwind v4
- 接入 Geist Sans + JetBrains Mono（next/font）
- 接入 shadcn/ui（基础组件）
- 配置 ESLint + Prettier
- 创建 Vercel 项目，链接到 `web/` 子目录
- 部署占位首页（"Aura / coming soon"）
- 设计 token 文档（colors.css / typography.css）

### 7.3 P1 Hero 详细

- `/` 路由基础 layout
- Hero 区域：110px 标题 + Splitting 字符动效
- R3F 3D 场景：手机 360° + Mesh Gradient 背景
- GSAP ScrollTrigger 绑定 4 段叙事
- Lenis 全局平滑滚动
- 磁性光标（桌面端）
- View Transitions 路由切换
- 移动端降级逻辑

### 7.4 P2 特性页详细

每页：
- 80px 标题
- 1 个 3D canvas 主体
- 文字说明（与 docs/ 同步内容）
- 相关链接

**现成轮子复用**：
- 3D 场景：Drei 工具 + 复用 P1 的几何体
- 文字动效：复用 Splitting 组件
- 滚动驱动：复用 ScrollTrigger 模式
- 布局：复用 shadcn Card / Container

### 7.5 P3 文档站详细

- 接入 fumadocs（`fumadocs-mdx` + `fumadocs-ui`）
- 软链 `docs/architecture.md` 等到 `web/content/docs/`
- 配置侧边栏（自动从目录结构）
- 接入 Pagefind 搜索
- 米色主题（独立 layout）

### 7.6 P4 Changelog 详细

- 用 `changelogen` 或自写脚本读 git tag → MDX
- 同步到 `web/content/changelog/`
- 列表页 + 详情页

### 7.7 P5 性能 / SEO 详细

- Lighthouse 跑分目标 95+
- 图像全部走 `next/image` + AVIF
- 字体子集化
- Bundle 分析（`@next/bundle-analyzer`）
- Sitemap + robots.txt
- 语义化 HTML / ARIA
- 键盘可达测试
- 减少动效选项（prefers-reduced-motion）

---

## 8. 仓库结构

### 8.1 monorepo 布局

```
android/                              ← 现有 Android 项目
docs/                                 ← 现有 docs（被 web/content 软链）
  plan/
  architecture.md
  roadmap.md
  ...
web/                                  ← 新增
  apps/
    web/                              ← Next.js 主站
      src/
        app/
        components/
        lib/
        styles/
      public/
      next.config.ts
      package.json
  packages/
    ui/                               ← 共享 UI（AuraCard、AuraHero）
    tokens/                           ← 设计 token（colors、typography）
    content/                          ← 共享内容（git log 脚本等）
  package.json                        ← workspace root
  pnpm-workspace.yaml
  turbo.json
vercel.json                           ← 根级 Vercel 配置
```

### 8.2 Vercel 配置

- Framework Preset: Next.js
- Root Directory: `web/apps/web`
- Build Command: `pnpm build`（Turborepo 加速）
- Output: 默认 `.next/`
- 域名：先 `aura-xxx.vercel.app`

---

## 9. 待用户提供资产

> 优先级排序，P0 启动前**至少需要**产品名 + 配色。

| 优先级 | 资产 | 说明 |
|--------|------|------|
| P0 | 产品名 | 中英文 |
| P0 | Slogan | ≤ 20 字 |
| P1 | 主色 HEX | 至少 1 个 |
| P1 | 强调色 HEX | 至少 1 个 |
| P2 | logo | SVG 优先 |
| P2 | 字体偏好 | 衬线/无衬线/特定字体 |
| P3 | 应用截图 | 3-5 张手机截图 |
| P3 | 核心特性文案 | Presence/Memory/Local LLM 各 1-2 句 |
| P3 | 数据点 | commit/feature/test/用户数 |
| P4 | 路线图 | M0-M6 描述 |
| P5 | 文档脱敏清单 | 哪些 doc 可对外 |

> **当前状态**：用户已表示**配色暂无，先不急**。P0 启动前请至少提供产品名 + Slogan。

---

## 10. 风险与权衡

### 10.1 技术风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 3D bundle 过大 | 首屏 LCP 升高 | 动态 import + 移动端降级 |
| GSAP SplitText 付费 | 增加成本 | 用 Splitting.js 免费替代 |
| R3F 学习曲线 | 开发周期 | 大量复用 Drei 现成组件 |
| 文档站与主站主题冲突 | 维护复杂 | 独立 layout + 主题隔离 |
| Vercel Free 限额 | 100GB 带宽 | 启用图像优化 + ISR |

### 10.2 内容风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 缺少品牌资产 | 视觉降级 | 用占位 token 启动，后续替换 |
| 缺少应用截图 | 落地页空 | 用 mockup 框架 + 文字 |
| 文档脱敏成本 | 文档站延期 | 渐进式公开 |

### 10.3 工程权衡

| 选择 | 放弃 |
|------|------|
| A+B+C 全套 | 5-6 周工期 |
| 极致动效 | 移动端性能（必须降级） |
| monorepo | 仓库复杂度（但与 Android 共享工具链） |
| 文档站用 fumadocs | 与主站视觉完全一致（保持独立品牌） |

### 10.4 显式不做

- Web Demo（涉及后端）
- 用户登录 / 账号
- 服务端数据库
- 国际化（首版仅中文，后续评估）
- 移动端原生 App
- 实时聊天（WebSocket）

---

## 11. 评审与下一步

### 11.1 待评审项

请确认以下决策：

1. **A+B+C 组合 + 米色文档站** ✅（已确认）
2. **monorepo + Turborepo** ✅（已确认）
3. **Vercel Free 部署** ✅（已确认）
4. **3D 用几何体 + 着色器** ✅（已确认）
5. **产品名 / Slogan** ⏳（待提供）
6. **logo / 配色** ⏳（用户表示先不急）
7. **文档公开范围** ⏳（P3 阶段前确认）

### 11.2 启动条件

P0 启动前需获得：
- [ ] 产品名 + Slogan
- [ ] 至少一个主色 HEX（或授权我用占位）

### 11.3 下一步动作

收到 P0 启动条件后：
1. 创建 `web/` 子目录
2. 初始化 pnpm workspace + Turborepo
3. 脚手架 Next.js 15
4. 部署占位首页
5. 邀请你 review 链路

预计 P0 完成时间：**3-4 天**。

---

## 12. 参考资料

- [motion.dev](https://motion.dev) — React 动效库
- [gsap.com](https://gsap.com) — 专业动效库
- [lenis.dev](https://lenis.dev) — 平滑滚动
- [ui.shadcn.com](https://ui.shadcn.com) — 组件库
- [threejs.org](https://threejs.org) — 3D 引擎
- [drei.docs.pmnd.rs](https://drei.docs.pmnd.rs) — R3F 工具集
- [paper.design](https://paper.design) — mesh gradient shaders
- [fumadocs.vercel.app](https://fumadocs.vercel.app) — 文档站框架
- [pagefind.app](https://pagefind.app) — 静态搜索
- [tailwindcss.com](https://tailwindcss.com) — Tailwind v4
- [nextjs.org](https://nextjs.org) — Next.js 15

---

**文档结束** / 待评审后启动 P0
