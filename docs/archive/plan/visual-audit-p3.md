# Aura 视觉审计报告 · P3

> Archived on 2026-06-15. Kept for historical design context; no longer a current planning entry.
>
> 4 页 × 4 对标站 · 7 维度对比分析 · 改进方向
> 起草：2026-06-15 / 桌面 1440×900 / Playwright 实测截图

---

## 0. 调研方法

- **目标页面**：Aura `/`、`/presence`、`/memory`、`/agent`（部署在 aura.gqy20.top）
- **对标站**：Linear / Vercel / Stripe / Apple iPhone 17 Pro
- **视口**：1440×900 桌面
- **截图**：hero viewport + fullPage（用于检查滚动叙事）
- **截图位置**：`D:\C\Desktop\ai\android\*.png`

---

## 1. 对标站视觉语言提取

### 1.1 Linear（深色 + 排版旗舰）

| 维度 | 观察 |
|------|------|
| 背景 | `#08090a` 与 Aura 同色 |
| 标题 | 60-64px / 行高 1.1 / 限宽 60% / 留 40% 给产品截图 |
| 副标题 | 16-18px / 单行 / 短小 |
| CTA | 右上有 "New Coding Sessions →" 引导，hero 无居中按钮 |
| 主视觉 | **真实应用截图**（不是 3D 抽象），占 hero 60vh |
| 留白 | 巨大，hero padding 12vh top，section 间 200vh |

**关键借鉴**：用真实产品截图代替抽象 3D。"AI 陪伴" 截个聊天界面比 3D 手机有说服力。

### 1.2 Vercel（浅色 + 3D 几何 + 渐变）

| 维度 | 观察 |
|------|------|
| 背景 | 白 + 中心辐射渐变（橙/青/粉） |
| 标题 | 52px / **居中** / 黑字 |
| 主视觉 | 中央白色 3D 三角 + 渐变光晕铺底 |
| CTA | 一深一浅（Start Deploying 黑 / Get a Demo 白） |
| 顶部 | announcement bar（活动消息） |
| 节奏 | 视觉中心对称，眼睛被锁在中心 |

**关键借鉴**：浅色 + 居中对称的"祭坛式"布局让产品像圣物。Aura 深色版可以做"对称 + 强光源在 3D 上"。

### 1.3 Stripe（浅色 + 渐变文字 + 流体 banner）

| 维度 | 观察 |
|------|------|
| 背景 | 白底 |
| 标题 | 60px / **左对齐** / 关键短语彩色渐变 |
| 客户 logo | 横排信任带（NVIDIA / Ford / Coinbase / Google） |
| 右上 | 彩色 3D 流体 banner（紫橙粉） |
| 留白 | 大方，section 间 200vh |

**关键借鉴**：**渐变文字** 是 hero 亮点 — 关键短语（"陪伴体" / "Local LLM"）用 accent 渐变着色，立刻有品牌感。**信任带**（开源 GitHub stars / Hugging Face downloads / Reddit）适合放在 hero 底部。

### 1.4 Apple iPhone 17 Pro（全屏视频 + 极简）

| 维度 | 观察 |
|------|------|
| 背景 | 黑/产品图切换（滚动驱动） |
| 标题 | 100px+ 巨字 / 滚动到位才出现 |
| 主视觉 | **滚动驱动视频**（不是 3D，是预渲染视频） |
| CTA | 单 Buy 按钮 + 价格 |
| 滚动 | 多屏，每屏换背景 + 标题 |

**关键借鉴**：滚动驱动叙事 — 滚到位置才揭示新信息。Aura 可以用 GSAP ScrollTrigger 做"Presence 7 状态切换"。

---

## 2. Aura 4 页详细观察

### 2.1 `/`（首页）

**Aura home fullPage**：
- 紫色 Mesh Gradient 渐变作为 hero 背景 — **氛围到位** ✓
- 大标题"AI companion that lives with you" 排版有力 ✓
- 3D PhoneOrb **几乎不可见** — 只看到黑色手机外壳的左半边轮廓
- ScrollSection（Presence / Memory）**完全不可见** — `whileInView` 在 fullPage 截图下未触发
- 底部 Data Strip 也不可见
- 整体在滚动叙事前的内容**只有 hero 标题 + 两个 CTA**

**问题清单**：
1. **3D 主视觉缺失**（最严重）— PhoneOrb 没渲染出流体屏幕
2. **滚动叙事段不可见**（motion + IntersectionObserver 在 fullPage 截图下不触发）
3. **CTA 不吸引** — "Start" 缺少行动召唤；"View on GitHub" 退而求其次
4. **缺少信任带** — 41 tests / 7 modules 数据藏在底部，没起到信任作用
5. **缺少下载入口** — Android 应用的下载/安装链接缺失

### 2.2 `/presence`

**Aura presence fullPage**：
- 大标题 + "01 CAPABILITY" 编号 + 副标题 排版**顶级** ✓
- 3D PresenceOrb 渲染成功 — 紫色 Icosahedron + 6 卫星 Trail ✓
- 5 步骤 "How Aura decides what to feel" **信息密度好** ✓
- 24h timeline **不可见**（whileInView 触发问题）
- Reaction throttling 表 **不可见**
- Related 链接**可见** ✓

**问题清单**：
1. **3D 容器内 PresenceOrb 位置下沉** — 球体只占容器下半，上半空白
2. **状态指示器（6 个点）太小** — 6px × 24px，桌面端几乎看不见
3. **24h timeline 段缺失滚动揭示动效** — 没有"渐入 + 节点点亮"动效
4. **Reaction throttling 表** 没有视觉重点 — 一堆 rgba(0.2~0.8) 紫条对眼睛不友好
5. **节流表的 Priority 列** 用 opacity 表示强度，但眼睛看不出 50 vs 40 的差异

### 2.3 `/memory`

**Aura memory fullPage**：
- 标题 / 3D / 3 layers cards **可见且品质好** ✓
- 3D MemoryNetwork 节点彩色区分清晰（紫/绿/橙/青/粉） ✓
- Summary types 5 卡片**不可见**
- Storage breakdown 堆叠条**部分可见**（条本身没渲染出来）
- Every field 9 卡片**可见** ✓
- Related 链接**可见** ✓

**问题清单**：
1. **Summary types 5 卡片不可见**（whileInView 问题）
2. **Storage breakdown 堆叠条无动画反馈** — 应该 `width: 0 → 80%` 平滑展开（已写但未触发）
3. **3D 网络缺动画** — 节点应该 hover 高亮 + 卫星慢速 orbit
4. **9 字段卡片都是纯边框底色** — 缺视觉层级（importance / sourceMessageIds 应该有特殊 icon）
5. **缺少 "Try Aura" / "Build with memory" CTA** — 这一页是技术深度，底部应该有 GitHub / Docs 链接

### 2.4 `/agent`

**Aura agent fullPage**：
- 标题 / 3D / 工具分类**可见** ✓
- 3D AgentGraph 节点图清晰（9 工具 + 3 Provider） ✓
- Streaming pipeline 6 阶段**不可见**
- Dual-mode routing 3 卡片**不可见**
- Design principles 6 卡片**不可见**
- Related 链接**可见** ✓

**问题清单**：
1. **Streaming pipeline 段落** 完全隐藏 — fullPage 看不到
2. **3 LLM Provider 节点** 用了 wireframe octahedron 但太小（0.18 size）+ 透明度低，几乎看不见
3. **Pipeline 6 阶段连接线** 显示成右边的小段，看起来像 bug
4. **TTFT 性能条** 设计好，但被注释"3 stages"在 dark 上看不清
5. **Design principles 6 卡片** 没看到

---

## 3. 七维度对比分析

### 3.1 整体节奏（信息密度/留白/视线引导）

| 站 | 评分 | 观察 |
|----|------|------|
| **Linear** | ⭐⭐⭐⭐⭐ | 极简 + 留白 + 大字 + 真实截图 |
| **Vercel** | ⭐⭐⭐⭐⭐ | 居中对称 + 渐变 + 强引导 |
| **Stripe** | ⭐⭐⭐⭐ | 标题 + 流体 banner + logo 信任带 |
| **Apple** | ⭐⭐⭐⭐⭐ | 滚动叙事 + 巨字 + 全屏视觉 |
| **Aura 当前** | ⭐⭐⭐ | hero 排版好，但 3D 视觉未到 + 中段叙事空白 |

**Aura 改进方向**：
- 加 Vercel 风的**announcement bar** 顶部（"v0.4 刚发布 — 见 changelog →"）
- hero 底部加**信任带**（GitHub stars / Download count / License），像 Stripe
- 3 个特性页之间需要**视觉断点**（背景色/渐变切换），目前 4 页都是 `#08090a` 一样深

### 3.2 视觉层级（H1/H2/正文字号比例）

| 站 | H1 | H2 | Body | 比例 |
|----|----|----|----|------|
| **Linear** | 64px / 510 | 32px | 18px | 1 : 0.5 : 0.28 |
| **Vercel** | 52px / 600 | 28px | 16px | 1 : 0.54 : 0.31 |
| **Stripe** | 60px / 500 | 32px | 18px | 1 : 0.53 : 0.30 |
| **Aura 当前** | 80px / 500 | 32px | 18px | 1 : 0.4 : 0.225 |

**Aura 改进方向**：
- H1 (80px) 相对 H2 (32px) 太大，**中间需要 H1.5 (56px)**
- 副标题/正文偏小（18px vs Linear/Vercel 的 20-22px）
- **建议加 scrolled-section 小标题用 14px mono uppercase**（已有，但样式不够鲜明）

### 3.3 动效密度与品质

| 站 | 特点 | Aura 现状 |
|----|------|----------|
| **Linear** | 极简 hover / scroll fade-in | 类似 |
| **Vercel** | 居中几何体自旋 + 渐变流动 | 类似（Mesh Gradient） |
| **Stripe** | **3D 卡片 hover 倾斜** + parallax 鼠标 | 未做（建议加） |
| **Apple** | **滚动驱动视频** + 巨字渐入 | 未做（建议用 GSAP） |

**Aura 改进方向**：
- **3D 卡片 hover tilt**（Stripe 风）— 各页 "Related" 链接卡片加 3D 倾斜
- **滚动驱动标题**（Apple 风）— 滚到 H2 时字符渐入
- **GSAP ScrollTrigger 替代 motion** — motion 的 `whileInView` 在 fullPage 截图下不触发；GSAP ScrollTrigger 在生产中更可靠

### 3.4 品牌独特性

| 站 | 品牌符号 | Aura 缺什么 |
|----|----------|------------|
| **Linear** | 极简黑 + Inter + 183 SVG 矢量动画 | 缺一个**标志性 logo 动画**（aura. 后面那个点） |
| **Vercel** | 黑白 V 字 + 渐变光晕 | 缺一个**标志性 3D 形态**（一个统一的 "Aura 球体"） |
| **Stripe** | 渐变紫 + 流体 + 衬线 logo | 缺**品牌色系统**（目前只有 #7c5cff 一个 accent） |
| **Apple** | SF Pro + 产品 + 黑色全屏 | 缺**情感文案**（Aura 是"陪伴"，应该更"人"） |

**Aura 改进方向**：
- 选一个**核心 accent 色**（紫 #7c5cff OK）+ 2 个**辅助色**（粉 #ff7c9c / 青 #5cefff）作为 Type 分类
- 加一个**hero 标志性动画**：当鼠标静止 3 秒，aura. 那个点变形成一颗跳动的小心脏 💜
- 情感文案：从"The AI companion that lives with you"改为更**具体场景**的轮播："a friend who remembers your dog / a coach for your morning / a quiet presence at 3am"

### 3.5 CTA 转化设计

| 站 | 顶部 CTA | Hero CTA | 底部 CTA |
|----|---------|----------|----------|
| **Linear** | Sign up | 无 | 强 — 截图下方 Sign up 表格 |
| **Vercel** | Sign Up | Start Deploying | 客户 logo |
| **Stripe** | 登录 | 立即开始 | 联系销售 |
| **Aura 当前** | GitHub | **Start**（无目标链接！）| 无 |

**Aura 改进方向**：
- **"Start" 按钮目前是 `<Link href="#">` 死链** — 必须改成 `/download` 或 `https://github.com/.../releases`
- 加**两个次级 CTA**（"View Demo" / "Read Docs"），分散转化路径
- 底部加**客户/使用场景信任带**：⭐ GitHub stars / 📥 1.2k downloads / 🌐 MIT License / 👥 Discord

### 3.6 移动端降级

Aura 在 mobile (max-width: 768px) 已经：
- 关闭 Lenis
- 关闭磁性光标
- 关闭 3D（用 CSS 渐变替代）

**问题**：
- 手机端 hero 的 **Mesh Gradient 渐变太弱**（CSS radial-gradient 简单两层）
- 移动端 3D 完全消失（虽然计划里有"静态截图 + CSS 渐变"），但目前 3 页特性页的 3D 都消失了，**移动端用户看不到任何主视觉**

**改进方向**：
- 移动端 3D 占位用 **静态 SVG / canvas 渲染静态图**（像 Apple 那样）
- 或者**保留 R3F 但降低质量**（dpr=1、不开 bloom）

### 3.7 性能 / 首屏

Aura 用 Vercel Free 部署，hkg1 区域：
- LCP 应该 < 1.5s（Vercel CDN）
- 但 3D PhoneOrb 加载需要 1-2s（three.js + R3F + Drei bundle 大）

**未测数据**（建议补）：
- Lighthouse 95+ 目标
- 三页 first contentful paint
- 3D 资源是否在 viewport 内才加载（lazy）

---

## 4. 关键问题清单（按严重度排序）

| # | 问题 | 严重度 | 影响 |
|---|------|-------|------|
| 1 | 首页 3D PhoneOrb **几乎不可见**（屏幕/流体没渲染） | 🔴 致命 | 失去主视觉 |
| 2 | "Start" CTA 是死链 `href="#"` | 🔴 致命 | 转化断流 |
| 3 | 4 页所有 `whileInView` 内容在 fullPage 截图下不可见 | 🟠 严重 | SEO 抓取、社交分享图丢失 |
| 4 | 3 个特性页之间没有视觉断点（背景都一样） | 🟠 严重 | 失去节奏感 |
| 5 | 移动端没有 3D 替代品 | 🟠 严重 | 60% 流量没主视觉 |
| 6 | Reaction throttling 表视觉层级弱 | 🟡 中 | 难读 |
| 7 | 3 个 LLM Provider 节点太小看不清 | 🟡 中 | agent 页主视觉缺一块 |
| 8 | 缺信任带（GitHub stars / downloads） | 🟡 中 | 转化率低 |
| 9 | 缺品牌符号（标志性 3D 形态 / logo 动画） | 🟡 中 | 品牌识别弱 |
| 10 | 缺次级 CTA（View Demo / Read Docs） | 🟡 中 | 转化路径单一 |

---

## 5. 改进方向（按 ROI 排序）

### Tier 1 · 立竿见影（1-2 天）

1. **修复 3D PhoneOrb** — 检查 alpha 通道、调整 camera、放大屏幕区域
2. **"Start" CTA 改链接** → `/download` 或 GitHub release
3. **加 announcement bar** 顶部（"v0.4 · 41 tests pass → changelog"）
4. **加信任带** hero 底部（GitHub stars / Downloads / License）
5. **加 3 个次级 CTA**（"View Demo" / "Read Docs" / "Open in Android"）

### Tier 2 · 节奏感（2-3 天）

6. **3 特性页换背景渐变**：
   - `/presence` — 紫蓝
   - `/memory` — 青绿
   - `/agent` — 紫粉
7. **加 GSAP ScrollTrigger** 替代 motion `whileInView`（生产可靠性 + fullPage 截图兼容）
8. **24h timeline 节点点亮** 动效
9. **Reaction throttling 表** 改用条形强度 + 颜色阶梯

### Tier 3 · 品牌独特性（3-5 天）

10. **统一 "Aura 球体" 形态** — 4 页都有的标志性 3D 元素
11. **aura. 那个点的 logo 动画** — 鼠标静止 3s 变小心脏
12. **3D 卡片 hover tilt**（Stripe 风）— Related 卡片
13. **情感文案轮播** — hero 标题 4 句轮播

### Tier 4 · 性能 / 移动端（2-3 天）

14. **移动端 3D 静态替代** — SVG / canvas 渲染关键帧
15. **3D lazy load** — IntersectionObserver 触发再加载
16. **Lighthouse 95+ 跑分**
17. **Bundle 分析** — 拆 three.js 按需

### Tier 5 · 长期品牌建设（5+ 天）

18. **Open Graph 卡片定制** — 每页一张品牌图
19. **品牌色规范** — 1 主色 + 2 辅色 + 5 中性色
20. **字体自研 / 升级** — 考虑 Berkeley Mono 作为强调

---

## 6. 推荐执行路径

```
Week 1: Tier 1（5 项 + 修复致命 bug）
Week 2: Tier 2（4 项，节奏感）+ 移动端 3D 替代
Week 3: Tier 3（4 项，品牌独特性）
Week 4: Tier 4（性能 / Lighthouse）
```

**用户优先级确认**：
- **视觉冲击**（3D 修复 + 品牌）vs **节奏感**（3 页断点 + 动效）vs **转化**（CTA + 信任带）？
- 推荐**先 Tier 1 + Tier 2**（5 天内完成 9 项），立刻拉升整体品质
- Tier 3-4 看用户反馈

---

## 7. 附件

| 文件 | 描述 |
|------|------|
| `aura-home-hero.png` / `aura-home-full.png` | Aura 首页截图 |
| `aura-presence-hero.png` / `aura-presence-full.png` | /presence 截图 |
| `aura-memory-hero.png` / `aura-memory-full.png` | /memory 截图 |
| `aura-agent-hero.png` / `aura-agent-full.png` | /agent 截图 |
| `ref-linear-hero.png` | Linear 主页 |
| `ref-vercel-hero.png` | Vercel 主页 |
| `ref-stripe-hero.png` | Stripe 主页 |
| `ref-apple-hero.png` | Apple iPhone 17 Pro 主页 |

---

**报告完。** 等待用户确认改进优先级，进入 P3.4+ 实施阶段。
