'use client'

import dynamic from 'next/dynamic'
import Link from 'next/link'
import { motion } from 'motion/react'
import { SplitText } from '@/components/SplitText'
import { MagneticCursor } from '@/components/MagneticCursor'
import { Reveal } from '@/components/Reveal'
import { AuraLogo } from '@/components/AuraLogo'
import { SceneSection } from '@/components/scene/SceneSection'
import { PhoneMockup } from '@/components/scene/PhoneMockup'

const MeshGradient = dynamic(
  () => import('@/components/three/MeshGradient').then((m) => m.MeshGradient),
  { ssr: false },
)

export default function Home() {
  return (
    <>
      <MagneticCursor />
      <main className="relative min-h-screen overflow-x-clip">
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: '#08090a' }}
        />

        {/* 首屏（snap，整屏 100svh）：nav + hero */}
        <section className="relative flex h-[100svh] snap-start snap-always flex-col overflow-hidden">
          {/* 导航栏到内容区的渐变过渡 */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-x-0 top-0 z-10 h-32"
            style={{
              background: 'linear-gradient(to bottom, #08090a 0%, transparent 100%)',
            }}
          />
          <div className="relative z-20 px-6 sm:px-10 lg:px-16">
            <motion.nav
              initial={{ opacity: 0, y: -20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
              className="flex h-20 items-center justify-between"
            >
              <div className="flex items-center gap-6">
                <Link href="/" className="flex items-center gap-2" aria-label="Aura home">
                  <AuraLogo size={28} />
                  <span className="font-mono text-sm font-medium tracking-tight">
                    Aura<span className="text-accent">.</span>
                  </span>
                </Link>
                <span className="label-mono text-muted hidden sm:inline">
                  Android AI Companion
                </span>
              </div>
              <div className="flex items-center gap-6 text-sm sm:gap-8">
                <Link href="/presence" className="text-muted transition-colors hover:text-foreground">
                  Presence
                </Link>
                <Link href="/memory" className="text-muted transition-colors hover:text-foreground">
                  Memory
                </Link>
                <Link href="/agent" className="text-muted transition-colors hover:text-foreground">
                  Agent
                </Link>
                <Link href="/tech" className="text-muted transition-colors hover:text-foreground">
                  Tech
                </Link>
                <Link href="https://github.com/gqy20/Aura" className="text-muted transition-colors hover:text-foreground">
                  GitHub ↗
                </Link>
              </div>
            </motion.nav>
          </div>

          <div className="relative flex flex-1 items-center px-6 pt-6 pb-8 sm:px-10 sm:pt-8 lg:px-16">
            <div
              className="pointer-events-none absolute inset-0 hidden md:block"
              style={{
                WebkitMaskImage: 'linear-gradient(to bottom, transparent 0%, black 14%, black 70%, transparent 100%)',
                maskImage: 'linear-gradient(to bottom, transparent 0%, black 14%, black 70%, transparent 100%)',
              }}
            >
              <MeshGradient />
            </div>

            <div
              aria-hidden
              className="pointer-events-none absolute inset-0 md:hidden"
              style={{
                background:
                  'radial-gradient(ellipse 60% 40% at 15% 0%, rgba(124, 92, 255, 0.12), transparent), radial-gradient(ellipse 50% 35% at 85% 100%, rgba(124, 92, 255, 0.06), transparent)',
              }}
            />

            <div className="relative grid w-full grid-cols-1 items-center gap-12 md:grid-cols-[0.9fr_1.15fr]">
              <div className="flex max-w-[540px] flex-col">
                <h1 className="relative z-10 mt-6 max-w-[560px] text-balance text-4xl font-medium leading-[1.62] tracking-tight sm:mt-8 sm:text-5xl md:text-5xl lg:text-6xl">
                  <span className="block">
                    <SplitText text="长期认识你的" stagger={0.045} delay={0.4} />
                  </span>
                  <span className="block">
                    <SplitText text="AI 伙伴" stagger={0.045} delay={0.95} />
                  </span>
                </h1>

                <motion.p
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, delay: 1.6, ease: [0.22, 1, 0.36, 1] }}
                  className="mt-10 max-w-[560px] text-pretty text-base leading-relaxed text-muted sm:mt-12 sm:text-lg"
                >
                  Aura 不只是会聊天——它会记住你说过的话，在你锁屏后替你整理，下班递给你一条真正能走的路。
                </motion.p>

                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, delay: 1.8, ease: [0.22, 1, 0.36, 1] }}
                  className="mt-10 flex flex-wrap items-center gap-3"
                >
                  <Link
                    href="https://github.com/gqy20/Aura/releases"
                    className="group inline-flex h-11 items-center justify-center rounded-full bg-foreground px-6 text-sm font-medium text-background transition-all hover:bg-foreground/90"
                  >
                    下载 Aura
                    <span className="ml-1.5 transition-transform group-hover:translate-x-0.5">↗</span>
                  </Link>
                  <Link
                    href="https://github.com/gqy20/Aura"
                    target="_blank"
                    rel="noopener"
                    className="inline-flex h-11 items-center justify-center gap-2 rounded-full border border-border-strong px-5 text-sm font-medium text-foreground transition-all hover:bg-subtle"
                  >
                    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
                      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
                    </svg>
                    在 GitHub 上查看
                  </Link>
                  <Link
                    href="https://github.com/gqy20/Aura#readme"
                    target="_blank"
                    rel="noopener"
                    className="group ml-1 inline-flex h-11 items-center text-sm text-muted transition-colors hover:text-foreground"
                  >
                    阅读文档
                    <span className="ml-1 transition-transform group-hover:translate-x-0.5">→</span>
                  </Link>
                </motion.div>
              </div>

              <div className="relative flex items-center justify-center overflow-visible">
                <HeroPhone />
              </div>
            </div>
          </div>
        </section>

        {/* 场景 01 · 情绪感知：陪伴有温度 */}
        <SceneSection
          index="01"
          eyebrow="陪伴 · 情绪感知"
          title={'你说"今天好累"，它不回"辛苦了"。'}
          description="情绪状态机和关系亲密度让回应随你近几天的状态变化——越熟越懂分寸，但不越界。"
          abilityTags={['情绪状态机', '关系亲密度', '克制语调']}
          accent="var(--aura-speaking)"
          phone={<TiredChatPhone />}
        />

        {/* 场景 02 · 锁屏做梦 → Insight：长期认识你（C 位） */}
        <SceneSection
          index="02"
          eyebrow="洞察 · 长期认识你"
          title="你锁屏之后，它在为你工作。"
          description="Dream Loop 用本地 Qwen 整理今天，生成一条带来源、可校验的 Insight——全程不联网、不上云、不花钱。"
          abilityTags={['Dream Loop', '本地 Qwen 0.8B', 'Evidence 校验', '隐私不出手机']}
          accent="var(--aura-thinking)"
          phone={<DreamInsightPhone />}
        />

        {/* 场景 03 · 下班遛弯：真的能办事 */}
        <SceneSection
          index="03"
          eyebrow="行动 · 生活执行"
          title="下班它递给你一条路，不是一堆链接。"
          description="把步数、天气、口味和情绪拼成一条可走、能逛、能吃的路线，而不是零散回答「附近有什么」。"
          abilityTags={['Health Connect', '高德地图 MCP', '天气', '情绪综合']}
          accent="var(--aura-health)"
          phone={<WalkRoutePhone />}
        />

        {/* 场景 04 · 视觉记忆：拍过的照片它会记住 */}
        <SceneSection
          index="04"
          eyebrow="记忆 · 跨模态关联"
          title="你拍的每张照片，它都在默默记。"
          description="云端 Vision 理解画面内容，本地记忆系统存下时间戳和上下文——两周后自动关联：「每次拍完夕阳，你第二天心情都不错？」"
          abilityTags={['Qwen3-VL Vision', 'Memory Entity', 'Connection Insight', '证据可追溯']}
          accent="var(--aura-speaking)"
          phone={<SunsetMemoryPhone />}
        />

        {/* 场景 05 · 主动关怀：周日晚上它来找你 */}
        <SceneSection
          index="05"
          eyebrow="洞察 · 主动关怀"
          title="你不找它，它也会在合适的时候来找你。"
          description="Weekly Insight Worker 在周末汇总整周数据，生成一条有来源、可校验的卡片推送——不是群发模板，而是基于你这周真实说过的话。"
          abilityTags={['Weekly Insight', 'Evidence 校验', '用户可控', '深夜静默']}
          accent="var(--aura-thinking)"
          phone={<WeeklyInsightPhone />}
        />

        {/* 场景 06 · 周末出行：从一张票到一整套计划 */}
        <SceneSection
          index="06"
          eyebrow="办事 · 多工具编排"
          title="周五晚上说想出门，它给你一整套方案。"
          description="12306 查班次、地图算接驳、天气选目的地——MCP 工具链把出发时间、到站路线、散步点和回程建议串成一个完整周末计划。"
          abilityTags={['12306 MCP', '高德地图', '天气', '多步骤编排']}
          accent="var(--aura-health)"
          phone={<TravelPlanPhone />}
        />

      </main>
    </>
  )
}

/** 场景 01 · 情绪感知：模板 vs 克制追问 —— 核心是「越熟越懂分寸」 */
function TiredChatPhone() {
  return (
    <PhoneMockup badge={{ label: '23:47 ·', value: '情绪 偏低 · 亲密度 高', color: 'var(--aura-speaking)' }}>
      <div className="flex flex-1 flex-col justify-center gap-3">
        {/* 用户消息 + 情绪色条 */}
        <div className="flex justify-end">
          <div className="max-w-[78%]">
            <div className="rounded-2xl rounded-br-sm bg-accent/15 px-3.5 py-2.5 text-[13px] text-foreground">
              今天好累
            </div>
            <div className="mt-1 h-0.5 w-full rounded-full bg-gradient-to-r from-[var(--aura-speaking)] via-[var(--aura-speaking)]/40 to-transparent" />
          </div>
        </div>

        {/* 模板回复（划掉）—— 大多数 AI 的反应 */}
        <div className="flex justify-start opacity-30">
          <div className="max-w-[82%] rounded-2xl rounded-bl-sm border border-dashed border-white/10 px-3.5 py-2">
            <p className="text-[12.5px] text-muted line-through">辛苦了！早点休息吧 💪</p>
            <p className="mt-1 font-mono text-[9px] text-muted/50">← 模板回复</p>
          </div>
        </div>

        {/* Aura 回复 —— 基于情绪状态机 + 亲密度 */}
        <div className="flex justify-start">
          <div className="max-w-[88%] rounded-2xl rounded-bl-sm bg-white/[0.06] px-3.5 py-2.5">
            <p className="text-[13px] leading-relaxed text-foreground">
              连续第三天加班了？
            </p>
            <p className="mt-1.5 text-[12px] leading-relaxed text-foreground/70">
              要不要聊聊，还是就安静待会儿。
            </p>
            {/* 底部状态机标签 */}
            <div className="mt-2.5 flex items-center gap-1.5">
              <span className="rounded-md bg-[var(--aura-speaking)]/10 px-1.5 font-mono text-[8.5px]" style={{ color: 'var(--aura-speaking)' }}>state: low_energy</span>
              <span className="rounded-md bg-white/5 px-1.5 font-mono text-[8.5px] text-muted/60">intimacy: 0.84 → 收敛语调</span>
            </div>
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 02 · Dream Loop：锁屏后本地推理，证据链可追溯 —— 核心是「0 联网也能认识你」 */
function DreamInsightPhone() {
  return (
    <PhoneMockup
      badge={{ label: '23:30 ·', value: 'Dream Loop 运行中', color: 'var(--aura-thinking)', pulse: true }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-thinking) 24%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center gap-3.5">
        {/* 本地处理流水线 */}
        <div className="flex items-center gap-1.5 font-mono text-[8.5px]">
          {[
            { label: '收集数据', done: true },
            { label: 'Qwen 推理', done: true },
            { label: '校验 Evidence', done: true },
            { label: '生成 Insight', done: false },
          ].map((step, i) => (
            <div key={step.label} className="flex items-center gap-1.5">
              <span className={`flex h-4.5 w-4.5 items-center justify-center rounded-full ${step.done ? 'bg-[var(--aura-thinking)]/25 text-[var(--aura-thinking)]' : 'bg-white/5 text-muted'}`}>
                {step.done ? '✓' : `${i + 1}`}
              </span>
              <span className={step.done ? 'text-foreground/70' : 'text-muted/50'}>{step.label}</span>
              {i < 3 && <span className="mx-0.5 text-white/10">→</span>}
            </div>
          ))}
        </div>

        {/* Insight 卡片 */}
        <div
          className="rounded-2xl border p-4"
          style={{
            borderColor: 'color-mix(in srgb, var(--aura-thinking) 32%, transparent)',
            backgroundColor: 'color-mix(in srgb, var(--aura-thinking) 8%, transparent)',
          }}
        >
          <div className="flex items-center gap-2">
            <span className="label-mono text-accent">Pattern</span>
            <span className="h-px flex-1 bg-border" />
            <span className="font-mono text-[10px] text-muted">conf 0.82</span>
          </div>

          <p className="mt-3 text-[14px] leading-relaxed text-foreground">
            你最近 3 周都 <span className="rounded bg-accent/25 px-1">周日下午</span> 情绪偏低。
          </p>

          {/* Evidence 来源 chips */}
          <div className="mt-3 flex flex-wrap gap-1.5">
            {[
              { n: '6/15 日记', t: '情绪低' },
              { n: '6/8 对话', t: '疲惫' },
              { n: '6/1 记录', t: '不想说话' },
            ].map((e) => (
              <span key={e.n} className="rounded-md border border-white/[0.06] bg-white/[0.03] px-2 py-1 font-mono text-[9px]">
                <span className="text-foreground/70">{e.n}</span>
                <span className="ml-1 text-muted/50">·{e.t}</span>
              </span>
            ))}
          </div>

          <div className="mt-4 flex gap-2">
            <span className="rounded-full bg-accent px-3 py-1 text-[11px] font-medium text-accent-foreground">
              和 Aura 聊聊
            </span>
            <span className="rounded-full border border-border px-3 py-1 text-[11px] text-muted">
              先收起
            </span>
          </div>
        </div>

        {/* 底部隐私强调 */}
        <div className="flex items-center justify-center gap-1.5 font-mono text-[9px] text-muted/50">
          <span className="inline-block h-1 w-1 rounded-full bg-emerald-500/50" />
          本地 Qwen 0.8B · 数据零上传 · 锁屏后自动运行
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 03 · 多工具编排流程：不是给链接，而是把步数/天气/口味/地图融合成一条可走的路 */
function WalkRoutePhone() {
  return (
    <PhoneMockup badge={{ label: '18:21 ·', value: '工具编排中', color: 'var(--aura-health)' }}>
      <div className="flex flex-1 flex-col gap-3">
        {/* 用户请求 */}
        <div className="flex justify-end">
          <div className="max-w-[78%] rounded-2xl rounded-br-sm bg-accent/15 px-3 py-2 text-[12.5px] text-foreground">
            下班帮我规划条散步路线
          </div>
        </div>

        {/* 工具调用流 —— 核心差异化：多源数据同时拉取 */}
        <div className="space-y-2">
          {[
            { tool: 'Health Connect', status: 'done', result: '今日步数 1,840 / 偏低' },
            { tool: '高德地图', status: 'done', result: '周边 3 条步行路线' },
            { tool: '天气', status: 'done', result: '22°C 微风 · 无雨' },
            { tool: '记忆', status: 'done', result: '偏好清淡 · 常去小馆' },
          ].map((t) => (
            <div key={t.tool} className="flex items-center gap-2 rounded-lg bg-white/[0.02] px-2.5 py-1.5">
              <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${t.status === 'done' ? 'bg-emerald-400' : 'bg-[var(--aura-health)] animate-pulse'}`} />
              <span className="w-22 shrink-0 font-mono text-[9.5px] text-muted">{t.tool}</span>
              <span className="h-px flex-1 bg-white/[0.04]" />
              <span className={`font-mono text-[9.5px] ${t.status === 'done' ? 'text-foreground/60' : 'text-muted/50'}`}>{t.result}</span>
            </div>
          ))}
        </div>

        {/* 融合结果 —— 一条完整路线 */}
        <div className="rounded-xl border border-[var(--aura-health)]/20 bg-[var(--aura-health)]/[0.04] p-3">
          <div className="flex items-center gap-1.5 mb-2">
            <svg className="h-3 w-3 text-[var(--aura-health)]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" /></svg>
            <span className="font-mono text-[10px] font-medium text-[var(--aura-health)]">推荐路线 · 1.2km · 约14min</span>
          </div>
          <svg viewBox="0 0 220 60" className="w-full">
            <path d="M16 48 C 56 46, 70 28, 110 26 S 170 14, 202 6" fill="none" stroke="var(--aura-health)" strokeWidth="1.8" strokeDasharray="3 3" strokeLinecap="round" />
            <circle cx="16" cy="48" r="3" fill="var(--aura-health)" /><circle cx="110" cy="26" r="3" fill="var(--aura-health)" /><circle cx="202" cy="6" r="3" fill="var(--aura-health)" />
          </svg>
          <div className="mt-1.5 flex justify-between font-mono text-[8.5px] text-muted px-0.5">
            <span>公司<span className="text-foreground/30 ml-0.5">起点</span></span>
            <span>河边散步道<span className="text-foreground/30 ml-0.5">树荫</span></span>
            <span>老王小馆<span className="text-foreground/30 ml-0.5">终点</span></span>
          </div>
        </div>

        {/* Aura 补充建议 */}
        <div className="flex justify-start">
          <div className="max-w-[85%] rounded-2xl rounded-bl-sm bg-white/[0.04] px-3 py-2 text-[11.5px] leading-relaxed text-foreground/75">
            如果今天太累，也可以直接回家路上买杯咖啡——瑞幸文三路店顺路，步行 6min。
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 04 · 视觉记忆：拍照 → Vision 理解 → 跨时间关联发现 —— 核心是「它记得你拍过什么」 */
function SunsetMemoryPhone() {
  return (
    <PhoneMockup
      badge={{ label: '18:32 ·', value: 'Vision 记忆已存', color: 'var(--aura-speaking)' }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-speaking) 18%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center gap-3">
        {/* 用户发送的夕阳照片 —— 更有画面感 */}
        <div className="flex justify-end">
          <div className="max-w-[72%] overflow-hidden rounded-2xl rounded-br-sm">
            <div
              className="relative flex h-32 w-full items-end justify-center rounded-2xl"
              style={{
                background:
                  'linear-gradient(to top, #1a0a1e 0%, #4a1942 20%, #c44b3a 48%, #f0a030 75%, #ffd060 92%, #fff8e0 100%)',
              }}
            >
              {/* 太阳圆 */}
              <div className="absolute right-6 top-6 h-10 w-10 rounded-full bg-gradient-to-br from-yellow-200 via-orange-400 to-red-500 opacity-70 blur-[2px]" />
              {/* 地平线剪影 */}
              <div className="absolute bottom-0 left-0 right-0 h-5 bg-gradient-to-t from-black/60 to-transparent" />
              <span className="relative z-10 mb-2 font-mono text-[9px] text-white/70">📷 2026-06-15 18:32</span>
            </div>
          </div>
        </div>

        {/* Aura 的 Vision 回应 —— 说明它真的「看懂」了 */}
        <div className="flex justify-start">
          <div className="max-w-[88%] rounded-2xl rounded-bl-sm bg-white/[0.06] px-3 py-2.5">
            <p className="text-[12.5px] leading-relaxed text-foreground">
              好好看的夕阳，橙红色那层很厚——今天心情怎么样？
            </p>
            <div className="mt-2 flex items-center gap-1.5 font-mono text-[8.5px] text-muted/50">
              <span className="rounded bg-[var(--aura-speaking)]/10 px-1" style={{ color: 'var(--aura-speaking)' }}>Qwen3-VL</span>
              <span>识别：夕阳 · 橙红渐变 · 高饱和度</span>
            </div>
          </div>
        </div>

        {/* 记忆时间线 —— 展示跨时间关联能力 */}
        <div className="rounded-xl border border-white/[0.05] bg-white/[0.02] p-3">
          <p className="mb-2 font-mono text-[9.5px] text-muted">视觉记忆时间线</p>
          <div className="flex items-center gap-2">
            {[
              { date: '6/1', label: '夕阳', active: false },
              { date: '6/8', label: '晚霞', active: false },
              { date: '6/15', label: '夕阳 ★', active: true },
            ].map((m) => (
              <div key={m.date} className={`flex flex-col items-center ${m.active ? '' : 'opacity-40'}`}>
                <div className={`h-8 w-8 overflow-hidden rounded-lg ${m.active ? 'ring-1 ring-[var(--aura-speaking)]/40' : ''}`}
                  style={{
                    background: m.active
                      ? 'linear-gradient(135deg, #c44b3a, #f0a030)'
                      : 'linear-gradient(135deg, #4a1942, #c44b3a)',
                  }}
                />
                <span className={`mt-1 font-mono text-[8px] ${m.active ? 'text-foreground' : 'text-muted/50'}`}>{m.date}</span>
              </div>
            ))}
            <div className="ml-auto pl-2 border-l border-white/[0.08]">
              <p className="text-[11px] leading-snug text-foreground/85">每次拍完第二天<span className="text-[var(--aura-speaking)]">心情都不错</span></p>
              <p className="font-mono text-[8.5px] text-muted/50 mt-0.5">Connection Insight · conf 0.76</p>
            </div>
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 05 · Weekly Insight：基于真实对话数据的主动推送 —— 核心是「它来找你，且说的都是你说过的话」 */
function WeeklyInsightPhone() {
  return (
    <PhoneMockup
      badge={{ label: '周日 21:03 ·', value: 'Weekly Insight', color: 'var(--aura-thinking)', pulse: true }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-thinking) 22%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center gap-3.5">
        {/* 推送通知条 —— 营造「主动找你」的感觉 */}
        <div className="flex items-center gap-2 rounded-lg border border-dashed border-white/[0.08] bg-white/[0.015] px-3 py-2">
          <span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--aura-thinking)]/40" /><span className="relative inline-flex h-2 w-2 rounded-full bg-[var(--aura-thinking)]" /></span>
          <span className="font-mono text-[10px] text-muted">Aura 每周洞察 · 自动推送</span>
          <span className="ml-auto font-mono text-[9px] text-white/20">刚刚</span>
        </div>

        {/* 本周概览 —— 简洁的日历行 */}
        <div className="flex items-center gap-1.5">
          {['一', '二', '三', '四', '五', '六', '日'].map((d, i) => (
            <div key={d} className={`flex h-6 w-6 items-center justify-center rounded-full text-[9.5px] font-mono ${i < 5 ? 'bg-[var(--aura-thinking)]/15 text-foreground/80' : 'bg-white/[0.03] text-muted/50'}`}>
              {d}
            </div>
          ))}
          <span className="ml-auto font-mono text-[9px] text-muted/50">06/16 – 06/22</span>
        </div>

        {/* Insight 卡片 —— 引用用户原话作为证据 */}
        <div
          className="rounded-2xl border p-4"
          style={{
            borderColor: 'color-mix(in srgb, var(--aura-thinking) 32%, transparent)',
            backgroundColor: 'color-mix(in srgb, var(--aura-thinking) 8%, transparent)',
          }}
        >
          <div className="flex items-center gap-2">
            <span className="label-mono text-accent">Weekly</span>
            <span className="h-px flex-1 bg-border" />
            <span className="font-mono text-[10px] text-muted">5 条来源</span>
          </div>

          <p className="mt-3 text-[13.5px] leading-relaxed text-foreground">
            这周你提到 <span className="rounded bg-accent/20 px-1 font-medium">'deadline'</span> 5 次，
            但周五之后就没再提了——搞定了还是放下了？
          </p>

          {/* 用户原话引用 —— 让人感觉「它真的在听」 */}
          <div className="mt-3 space-y-1.5">
            {[
              { day: '周二', quote: '"这个 deadline 要命…"' },
              { day: '周四', quote: '"又加班到 11 点"' },
            ].map((q) => (
              <div key={q.day} className="flex items-start gap-2 rounded-md bg-white/[0.02] px-2 py-1.5">
                <span className="shrink-0 font-mono text-[8.5px] text-muted pt-0.5">{q.day}</span>
                <span className="text-[11px] italic text-foreground/60">{q.quote}</span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex gap-2">
            <span className="rounded-full bg-accent px-3 py-1 text-[11px] font-medium text-accent-foreground">
              和 Aura 聊聊
            </span>
            <span className="rounded-full border border-border px-3 py-1 text-[11px] text-muted">
              下周再说
            </span>
          </div>
        </div>

        <p className="text-center font-mono text-[8.5px] text-muted/40">
          本地生成 · 0 联网 · 设置中可调频率 / 关闭
        </p>
      </div>
    </PhoneMockup>
  )
}

/** 场景 06 · 出行方案：12306 + 地图 + 天气多工具编排 —— 核心是「一句话给出一整套计划」 */
function TravelPlanPhone() {
  return (
    <PhoneMockup badge={{ label: '周五 20:15 ·', value: '出行方案已生成', color: 'var(--aura-health)' }}>
      <div className="flex flex-1 flex-col gap-3">
        {/* 用户请求 */}
        <div className="flex justify-end">
          <div className="max-w-[78%] rounded-2xl rounded-br-sm bg-accent/15 px-3 py-2 text-[12.5px] text-foreground">
            周末想去上海逛逛，帮我规划一下
          </div>
        </div>

        {/* 高铁票卡片 —— 更有真实感 */}
        <div className="rounded-xl border border-[var(--aura-health)]/15 bg-subtle/30 p-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--aura-health)]/10">
                <span className="font-mono text-[11px] font-bold text-[var(--aura-health)]">G1234</span>
              </div>
              <div>
                <p className="text-[12px] font-medium text-foreground">杭州东 → 上海虹桥</p>
                <p className="font-mono text-[9.5px] text-muted">二等座 · ¥73 · 余票充足</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-[15px] font-semibold text-foreground tracking-tight">08:30</p>
              <p className="font-mono text-[9.5px] text-muted">→ 10:12</p>
              <p className="font-mono text-[8.5px] text-[var(--aura-health)]">1h42m</p>
            </div>
          </div>
        </div>

        {/* 完整行程时间线 —— 多步骤编排的核心展示 */}
        <div className="rounded-xl border border-white/[0.05] bg-white/[0.015] p-3">
          <p className="mb-2.5 flex items-center gap-1.5 font-mono text-[9.5px] text-muted">
            <svg className="h-3 w-3 text-[var(--aura-health)]" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" /></svg>
            完整行程
          </p>
          <div className="space-y-2">
            {[
              { time: '07:50', label: '地铁 → 杭州东站', icon: '🚇', tool: '' },
              { time: '08:30', label: '高铁 G1234 出发', icon: '🚄', tool: '12306' },
              { time: '10:12', label: '地铁 → 武康路', icon: '🚇', tool: '' },
              { time: '10:40', label: 'brunch + 漫步梧桐区', icon: '🥐', tool: '' },
              { time: '13:30', label: '外滩 / 城隍庙', icon: '📸', tool: '' },
              { time: '16:00', label: '返程 G1567 → 17:42 到', icon: '🚄', tool: '12306' },
            ].map((step, i) => (
              <div key={step.label} className="flex items-center gap-2">
                <span className="w-9 shrink-0 text-right font-mono text-[9.5px] text-muted">{step.time}</span>
                <span className="h-1 w-1 shrink-0 rounded-full bg-[var(--aura-health)]" />
                <span className="text-[11px] text-foreground/90">{step.icon} {step.label}</span>
                {step.tool && (
                  <span className="ml-auto rounded bg-[var(--aura-health)]/10 px-1.5 font-mono text-[8px]" style={{ color: 'var(--aura-health)' }}>{step.tool}</span>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* 目的地数据 chips */}
        <div className="flex flex-wrap gap-1.5">
          {[
            { label: '上海 26° 晴', icon: '☀' },
            { label: '武康路 人少', icon: '📍' },
            { label: '预算 ~¥300', icon: '💰' },
          ].map((c) => (
            <span key={c.label} className="inline-flex items-center gap-1 rounded-full border border-border bg-subtle/30 px-2 py-1 font-mono text-[9.5px] text-foreground/80">
              <span>{c.icon}</span>{c.label}
            </span>
          ))}
        </div>

        {/* Aura 补充 */}
        <div className="flex justify-start">
          <div className="max-w-[85%] rounded-2xl rounded-bl-sm bg-white/[0.04] px-3 py-2 text-[11px] leading-relaxed text-foreground/70">
            记得带伞——周日午后有短暂阵雨 🌦️
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/**
 * 首屏 Hero 手机——产品级多窗口浮动展示。
 *
 * 手机内容：场景 E（散步路线 + 小馆）——Aura 最独特的多工具编排能力。
 * 4 张浮动卡片紧贴手机左右两侧（不压在手机上），每张承载一个核心差异化信息。
 * 布局计算：容器 760px，手机 288px × 1.08 ≈ 311px 居中，
 *   手机左边缘 ≈ 225px，右边缘 ≈ 536px，卡片距边缘 16–20px。
 */
function HeroPhone() {
  const glassCard = (className?: string) =>
    `rounded-xl border backdrop-blur-xl shadow-2xl shadow-black/40 ${className ?? ''}`

  return (
    <div className="relative" style={{ width: 760 }}>
      {/* ═══ 右侧卡片 ① · 用户请求 ──────────────── */}
      <motion.div
        initial={{ opacity: 0, x: 20, y: -8, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 1.8, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ left: '556px', top: '0px' }}
      >
        <div className={glassCard()} style={{ borderColor: 'rgba(255,255,255,0.07)', background: 'rgba(21,21,29,0.88)' }}>
          <div className="px-4 py-2.5"><p className="text-[13px] font-medium text-foreground/95">下班帮我规划条散步路线</p></div>
        </div>
      </motion.div>

      {/* ═══ 左侧卡片 ① · 情绪状态机 ─────────────── */}
      <motion.div
        initial={{ opacity: 0, x: -20, y: -8, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 1.95, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ right: '552px', top: '0px' }}
      >
        <div className={glassCard('w-44')} style={{ borderColor: 'color-mix(in srgb, var(--aura-speaking) 20%, transparent)', background: 'color-mix(in srgb, var(--aura-speaking) 8%, rgba(21,21,29,0.88))' }}>
          <div className="p-3">
            <span className="label-mono text-[10px]" style={{ color: 'var(--aura-speaking)' }}>情绪状态机</span>
            <p className="mt-2 text-[11.5px] leading-snug text-foreground/85">越熟越懂分寸<br /><span className="text-muted/60">但不越界</span></p>
          </div>
        </div>
      </motion.div>

      {/* ═══ 右侧卡片 ② · Vision 能力 ───────────────── */}
      <motion.div
        initial={{ opacity: 0, x: 20, y: 10, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 2.15, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ left: '556px', top: '170px' }}
      >
        <div className={glassCard('w-48')} style={{ borderColor: 'color-mix(in srgb, var(--aura-speaking) 22%, transparent)', background: 'color-mix(in srgb, var(--aura-speaking) 8%, rgba(21,21,29,0.88))' }}>
          <div className="p-3">
            <div className="flex items-center gap-2">
              <span className="label-mono text-[10px]" style={{ color: 'var(--aura-speaking)' }}>Vision</span>
              <span className="h-px flex-1 bg-white/[0.08]" />
              <span className="font-mono text-[9px] text-muted">Qwen3-VL</span>
            </div>
            <p className="mt-2 text-[11.5px] leading-snug text-foreground/85">拍过的照片自动记下时间<br /><span className="text-muted/60">两周后关联发现规律</span></p>
          </div>
        </div>
      </motion.div>

      {/* ═══ 左侧卡片 ② · Pattern Insight ─────────────── */}
      <motion.div
        initial={{ opacity: 0, x: -20, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 2.3, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ right: '552px', top: '170px' }}
      >
        <div className={glassCard('w-52')} style={{ borderColor: 'color-mix(in srgb, var(--aura-thinking) 28%, transparent)', background: 'color-mix(in srgb, var(--aura-thinking) 10%, rgba(21,21,29,0.88))' }}>
          <div className="p-3.5">
            <div className="flex items-center gap-2"><span className="label-mono text-[10px]" style={{ color: 'var(--aura-thinking)' }}>Pattern</span><span className="h-px flex-1 bg-white/[0.08]" /><span className="font-mono text-[9px] text-muted">conf 0.82</span></div>
            <p className="mt-2.5 text-[12.5px] leading-snug text-foreground/90">你最近<span className="rounded bg-white/10 px-1 font-medium">3 周</span>都在周日下午情绪偏低。</p>
            <p className="mt-2 font-mono text-[9px] text-muted/60">来源 · 7 条记录 · 本地生成</p>
          </div>
        </div>
      </motion.div>

      {/* ═══ 右侧卡片 ③ · 瑞幸 MCP 示例 ───────────────── */}
      <motion.div
        initial={{ opacity: 0, x: 20, y: 12, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 2.55, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ left: '556px', top: '330px' }}
      >
        <div className={glassCard('w-48')} style={{ borderColor: 'color-mix(in srgb, #f97316 28%, transparent)', background: 'color-mix(in srgb, #f97316 8%, rgba(21,21,29,0.88))' }}>
          <div className="p-3">
            <div className="flex items-center gap-2">
              <span className="label-mono text-[10px]" style={{ color: '#f97316' }}>瑞幸咖啡</span>
              <span className="h-px flex-1 bg-white/[0.08]" />
              <span className="font-mono text-[9px] text-muted">MCP</span>
            </div>
            <p className="mt-2 text-[11.5px] leading-snug text-foreground/85">
              文三路店 · 步行 6min<br />
              <span className="text-muted/60">顺路经过 · 有优惠</span>
            </p>
          </div>
        </div>
      </motion.div>

      {/* ═══ 左侧卡片 ③ · Dream Loop ───────────────── */}
      <motion.div
        initial={{ opacity: 0, x: -20, y: 12, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 2.8, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ right: '552px', top: '340px' }}
      >
        <div className={glassCard('w-48')} style={{ borderColor: 'color-mix(in srgb, var(--aura-health) 26%, transparent)', background: 'color-mix(in srgb, var(--aura-health) 8%, rgba(21,21,29,0.88))' }}>
          <div className="p-3">
            <div className="flex items-center gap-2">
              <span className="relative flex h-2 w-2"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400/50" /><span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400" /></span>
              <span className="label-mono text-[10px]" style={{ color: 'var(--aura-health)' }}>Dream Loop</span>
            </div>
            <p className="mt-2 text-[11.5px] leading-snug text-foreground/85">锁屏后本地整理中<br /><span className="text-muted/60">Qwen 0.8B · 0 联网</span></p>
          </div>
        </div>
      </motion.div>

      {/* ═══ 右侧卡片 ④ · 隐私安全 ─────────────────── */}
      <motion.div
        initial={{ opacity: 0, x: 20, y: 14, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 3.05, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ left: '556px', bottom: '56px' }}
      >
        <div className={glassCard('w-44')} style={{ borderColor: 'rgba(255,255,255,0.06)', background: 'rgba(21,21,29,0.84)' }}>
          <div className="flex items-center gap-2.5 p-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-emerald-500/12">
              <svg className="h-4 w-4 text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>
            </div>
            <div><p className="text-[11px] font-medium text-foreground/90">隐私不出手机</p><p className="font-mono text-[9px] text-muted/60">本地推理 · 数据零上传</p></div>
          </div>
        </div>
      </motion.div>

      {/* ═══ 左侧卡片 ④ · 周末出行预览 ─────────────────── */}
      <motion.div
        initial={{ opacity: 0, x: -20, y: 14, scale: 0.94 }}
        animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
        transition={{ duration: 0.9, delay: 3.3, ease: [0.22, 1, 0.36, 1] }}
        className="absolute z-40 hidden lg:block"
        style={{ right: '548px', bottom: '52px' }}
      >
        <div className={glassCard('w-46')} style={{ borderColor: 'color-mix(in srgb, var(--aura-health) 20%, transparent)', background: 'color-mix(in srgb, var(--aura-health) 8%, rgba(21,21,29,0.88))' }}>
          <div className="p-3">
            <div className="flex items-center gap-2">
              <span className="label-mono text-[10px]" style={{ color: 'var(--aura-health)' }}>12306</span>
              <span className="h-px flex-1 bg-white/[0.08]" />
              <span className="font-mono text-[9px] text-muted">G1234</span>
            </div>
            <p className="mt-2 text-[11.5px] leading-snug text-foreground/85">
              杭州东 → 上海虹桥<br />
              <span className="text-muted/60">08:30 出发 · 1h42m</span>
            </p>
          </div>
        </div>
      </motion.div>

      {/* ═══════════════ 主手机框 —— 场景 E：散步路线 + 小馆 + 瑞幸 ══════════════ */}
      <PhoneMockup
        badge={{ label: '18:20 ·', value: '今日步数 1,840', color: 'var(--aura-health)' }}
        screenGlow="radial-gradient(ellipse 80% 55% at 50% 100%, color-mix(in srgb, var(--aura-health) 18%), transparent)"
        className="relative z-10 mx-auto scale-[1.08]"
      >
        <div className="flex flex-1 flex-col gap-2.5">
          {/* 用户请求 */}
          <div className="flex justify-end">
            <div className="max-w-[80%] rounded-2xl rounded-br-sm bg-accent/15 px-3 py-2 text-[12.5px] text-foreground">下班帮我规划条散步路线</div>
          </div>

          {/* 工具调用中 */}
          <div className="flex justify-start">
            <div className="inline-flex items-center gap-2 rounded-2xl rounded-bl-sm bg-white/[0.03] px-3 py-1.5">
              <span className="relative flex h-1.5 w-1.5"><span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[var(--aura-health)]/50" /><span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-[var(--aura-health)]" /></span>
              <span className="font-mono text-[10px] text-muted">正在调用工具…</span>
            </div>
          </div>

          {/* 路线图 —— 带距离和时间标注 */}
          <div className="rounded-xl border border-border bg-subtle/30 p-3 pt-2">
            <svg viewBox="0 0 220 80" className="w-full">
              <path d="M16 64 C 54 62, 68 38, 110 35 S 170 19, 202 9" fill="none" stroke="var(--aura-health)" strokeWidth="2" strokeDasharray="3 4" strokeLinecap="round" />
              <circle cx="16" cy="64" r="3.5" fill="var(--aura-health)" />
              <circle cx="110" cy="35" r="3.5" fill="var(--aura-health)" />
              <circle cx="202" cy="9" r="3.5" fill="var(--aura-health)" />
              {/* 距离标注线 */}
              <path d="M 16 58 L 16 72 M 110 29 L 110 42 M 202 3 L 202 16" stroke="rgba(255,255,255,0.08)" strokeWidth="1" strokeDasharray="2 3" />
            </svg>
            <div className="mt-0.5 flex justify-between font-mono text-[8.5px] text-muted px-0.5">
              <span>公司<br/><span className="text-foreground/40">起点</span></span>
              <span>河边散步<br/><span className="text-foreground/40">1.2km / 14min</span></span>
              <span>老王小馆<br/><span className="text-foreground/40">终点</span></span>
            </div>
          </div>

          {/* 多源数据 chips */}
          <div className="flex flex-wrap gap-1.5">
            {[
              { label: '22° 微风', src: '天气' },
              { label: '偏好清淡', src: '记忆' },
              { label: '树荫优先', src: '高德地图' },
              { label: '步数偏低', src: 'Health' },
            ].map((chip) => (
              <span key={chip.label} className="inline-flex items-center gap-1 rounded-full border border-border bg-subtle/40 px-2 py-1">
                <span className="font-mono text-[9.5px] text-foreground">{chip.label}</span>
                <span className="h-1 w-1 rounded-full bg-white/[0.08]" />
                <span className="font-mono text-[7.5px] text-muted/60">{chip.src}</span>
              </span>
            ))}
          </div>

          {/* 终点推荐卡 */}
          <div className="rounded-xl border border-border bg-subtle/30 p-3">
            <div className="flex items-start justify-between gap-2">
              <div>
                <p className="text-[13px] font-medium text-foreground">老王小馆</p>
                <p className="mt-0.5 font-mono text-[9.5px] text-muted">步行 18min · 一个人也好坐</p>
              </div>
              <span className="shrink-0 rounded-lg bg-emerald-500/10 px-2 py-1 font-mono text-[9px] text-emerald-400">符合口味</span>
            </div>
          </div>

          {/* Aura 回复：瑞幸 fallback 方案 */}
          <div className="flex justify-start">
            <div className="max-w-[88%] space-y-1.5 rounded-2xl rounded-bl-sm bg-white/[0.04] px-3 py-2">
              <p className="text-[12px] leading-relaxed text-foreground/85">
                如果今天太累或下雨，也可以改成直接回家路上买杯咖啡：
              </p>
              <div className="inline-flex items-center gap-1.5 rounded-md bg-[#f97316]/10 px-2 py-0.5">
                <span className="font-mono text-[9px] font-medium text-[#f97316]">☕ 瑞幸·文三路店</span>
                <span className="font-mono text-[8.5px] text-[#f97316]/80">步行 6min · 有优惠</span>
              </div>
            </div>
          </div>

          {/* 底部工具汇总 */}
          <div className="mt-auto flex items-center gap-2 rounded-lg border border-dashed border-white/[0.05] bg-white/[0.015] px-3 py-2">
            <span className="font-mono text-[8.5px] text-muted/40">已调用</span>
            {['高德地图', '天气', 'Health', '记忆'].map((t) => (
              <span key={t} className="rounded-md bg-white/[0.04] px-1.5 py-0.5 font-mono text-[8.5px] text-foreground/60">{t}</span>
            ))}
          </div>
        </div>
      </PhoneMockup>
    </div>
  )
}
