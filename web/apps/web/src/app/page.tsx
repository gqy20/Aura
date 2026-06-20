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

/** 场景 01 · 聊天流：模板回复（划掉）对比 Aura 的克制追问 */
function TiredChatPhone() {
  return (
    <PhoneMockup badge={{ label: '23:47 ·', value: '情绪 偏低', color: 'var(--aura-speaking)' }}>
      <div className="flex flex-1 flex-col justify-center gap-3">
        <p className="pl-1 font-mono text-[10px] text-white/40 line-through">辛苦了。</p>

        <div className="flex justify-end">
          <div className="max-w-[78%] rounded-2xl rounded-br-sm bg-accent/15 px-3.5 py-2.5 text-[13px] text-foreground">
            今天好累
          </div>
        </div>

        <div className="flex justify-start">
          <div className="max-w-[88%] rounded-2xl rounded-bl-sm bg-white/[0.06] px-3.5 py-2.5 text-[13px] leading-relaxed text-foreground">
            最近几天好像都比较辛苦？
            <span className="mt-1 block text-muted">要不要聊聊，还是就安静待会儿。</span>
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 02 · Insight 卡片：带类型、来源、置信度、用户控制（C 位） */
function DreamInsightPhone() {
  return (
    <PhoneMockup
      badge={{ label: '23:30 ·', value: 'Dream Loop 运行中', color: 'var(--aura-thinking)', pulse: true }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-thinking) 24%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center">
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

          <p className="mt-3 font-mono text-[10px] leading-relaxed text-muted">
            来源 · 7 条情绪记录 · 本地生成 · 0 联网
          </p>

          <div className="mt-4 flex gap-2">
            <span className="rounded-full bg-accent px-3 py-1 text-[11px] font-medium text-accent-foreground">
              和 Aura 聊聊
            </span>
            <span className="rounded-full border border-border px-3 py-1 text-[11px] text-muted">
              先收起
            </span>
          </div>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 03 · 路线 + 数据 chip + 终点卡：多源数据拼成一条可执行方案 */
function WalkRoutePhone() {
  return (
    <PhoneMockup badge={{ label: '18:20 ·', value: '今日步数 1,840', color: 'var(--aura-health)' }}>
      <div className="flex flex-1 flex-col gap-3.5">
        <div className="rounded-xl border border-border bg-subtle/30 p-3">
          <svg viewBox="0 0 220 90" className="w-full">
            <path
              d="M16 74 C 66 72, 78 42, 110 40 S 176 24, 202 14"
              fill="none"
              stroke="var(--aura-health)"
              strokeWidth="2"
              strokeDasharray="3 4"
              strokeLinecap="round"
            />
            <circle cx="16" cy="74" r="4.5" fill="var(--aura-health)" />
            <circle cx="110" cy="40" r="4.5" fill="var(--aura-health)" />
            <circle cx="202" cy="14" r="4.5" fill="var(--aura-health)" />
          </svg>
          <div className="mt-1 flex justify-between font-mono text-[9px] text-muted">
            <span>公司</span>
            <span>河边散步点</span>
            <span>小馆</span>
          </div>
        </div>

        <div className="flex flex-wrap gap-1.5">
          {['22° 微风', '偏好清淡', '树荫优先'].map((c) => (
            <span
              key={c}
              className="rounded-full border border-border bg-subtle/40 px-2.5 py-1 font-mono text-[10px] text-foreground"
            >
              {c}
            </span>
          ))}
        </div>

        <div className="rounded-xl border border-border bg-subtle/30 p-3">
          <p className="text-[13px] font-medium text-foreground">老王小馆</p>
          <p className="mt-0.5 font-mono text-[10px] text-muted">步行 18min · 一个人也好坐</p>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 04 · 夕阳照片 → Vision 理解 → Connection Insight 关联发现 */
function SunsetMemoryPhone() {
  return (
    <PhoneMockup
      badge={{ label: '18:32 ·', value: 'Vision 记忆', color: 'var(--aura-speaking)' }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-speaking) 18%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center gap-3.5">
        {/* 用户发送的夕阳照片 */}
        <div className="flex justify-end">
          <div className="max-w-[70%] overflow-hidden rounded-2xl rounded-br-sm">
            <div
              className="flex h-28 w-full items-end justify-center rounded-2xl"
              style={{
                background:
                  'linear-gradient(to top, #1a0a1e 0%, #4a1942 25%, #c44b3a 55%, #f0a030 78%, #ffd060 95%)',
              }}
            >
              <span className="mb-2 font-mono text-[9px] text-white/60">📷 2026-06-15 18:32</span>
            </div>
          </div>
        </div>

        {/* Aura 的 Vision 回应 */}
        <div className="flex justify-start">
          <div className="max-w-[88%] rounded-2xl rounded-bl-sm bg-white/[0.06] px-3.5 py-2.5 text-[13px] leading-relaxed text-foreground">
            好好看的夕阳，橙红色那层很厚——今天心情怎么样？
          </div>
        </div>

        {/* Connection Insight 卡片 */}
        <div
          className="rounded-2xl border p-3.5"
          style={{
            borderColor: 'color-mix(in srgb, var(--aura-speaking) 28%, transparent)',
            backgroundColor: 'color-mix(in srgb, var(--aura-speaking) 7%, transparent)',
          }}
        >
          <div className="flex items-center gap-2">
            <span className="label-mono text-accent">Connection</span>
            <span className="h-px flex-1 bg-border" />
            <span className="font-mono text-[10px] text-muted">conf 0.76</span>
          </div>
          <p className="mt-2.5 text-[13px] leading-relaxed text-foreground">
            你上次拍夕阳是{' '}
            <span className="rounded bg-accent/20 px-1">6 月 1 号</span>
            ，好像每次拍完第二天心情都不错？
          </p>
          <p className="mt-2 font-mono text-[10px] text-muted">
            来源 · 2 条视觉记忆 · 本地关联
          </p>
        </div>
      </div>
    </PhoneMockup>
  )
}

/** 场景 05 · Weekly Insight 推送卡片：主动关怀，不是群发模板 */
function WeeklyInsightPhone() {
  return (
    <PhoneMockup
      badge={{ label: '周日 21:00 ·', value: '本周洞察', color: 'var(--aura-thinking)', pulse: true }}
      screenGlow="radial-gradient(ellipse 80% 45% at 50% 115%, color-mix(in srgb, var(--aura-thinking) 22%, transparent), transparent)"
    >
      <div className="flex flex-1 flex-col justify-center">
        {/* 周报头部 */}
        <div className="flex items-center gap-3">
          <div className="flex gap-1.5">
            {['一', '二', '三', '四', '五', '六', '日'].map((d, i) => (
              <div
                key={d}
                className={`flex h-7 w-7 items-center justify-center rounded-full text-[10px] ${
                  i < 5 ? 'bg-accent/20 text-foreground' : 'bg-white/[0.04] text-muted'
                }`}
              >
                {d}
              </div>
            ))}
          </div>
          <span className="font-mono text-[10px] text-muted">06/16 – 06/22</span>
        </div>

        {/* Insight 卡片主体 */}
        <div
          className="mt-4 rounded-2xl border p-4"
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

          <p className="mt-3 text-[14px] leading-relaxed text-foreground">
            这周你提到{' '}
            <span className="rounded bg-accent/25 px-1">'deadline'</span>{' '}
            5 次，但周五之后就没再提了——是搞定了还是暂时放下了？
          </p>

          <div className="mt-4 flex gap-2">
            <span className="rounded-full bg-accent px-3 py-1 text-[11px] font-medium text-accent-foreground">
              和 Aura 聊聊
            </span>
            <span className="rounded-full border border-border px-3 py-1 text-[11px] text-muted">
              下周再说
            </span>
          </div>
        </div>

        {/* 底部元信息 */}
        <p className="mt-3 text-center font-mono text-[9px] text-muted">
          本地生成 · 0 联网 · 可在设置中调整频率
        </p>
      </div>
    </PhoneMockup>
  )
}

/** 场景 06 · 出行方案：12306 + 地图 + 天气 → 完整周末计划 */
function TravelPlanPhone() {
  return (
    <PhoneMockup badge={{ label: '周五 20:15 ·', value: '出行方案', color: 'var(--aura-health)' }}>
      <div className="flex flex-1 flex-col gap-3">
        {/* 高铁票卡片 */}
        <div className="rounded-xl border border-border bg-subtle/30 p-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[12px] font-medium text-foreground">G1234</p>
              <p className="font-mono text-[10px] text-muted">杭州东 → 上海虹桥</p>
            </div>
            <div className="text-right">
              <p className="text-[14px] font-semibold text-foreground">08:30</p>
              <p className="font-mono text-[10px] text-muted">→ 10:12 · 1h42m</p>
            </div>
          </div>
          <div className="mt-2 h-px bg-border" />
          <div className="mt-2 flex items-center justify-between font-mono text-[10px]">
            <span className="text-muted">二等座 · ¥73</span>
            <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-emerald-400">
              余票充足
            </span>
          </div>
        </div>

        {/* 目的地天气 + 数据 chips */}
        <div className="flex flex-wrap gap-1.5">
          {['上海 26° 晴', '武康路散步', '外滩夜景', '17:00 返程'].map((c) => (
            <span
              key={c}
              className="rounded-full border border-border bg-subtle/40 px-2.5 py-1 font-mono text-[10px] text-foreground"
            >
              {c}
            </span>
          ))}
        </div>

        {/* 完整行程时间线 */}
        <div className="rounded-xl border border-border bg-subtle/30 p-3">
          <p className="mb-2 text-[12px] font-medium text-foreground">完整行程</p>
          <div className="space-y-2 font-mono text-[10px]">
            {[
              { time: '07:50', label: '地铁 → 杭州东站', dot: true },
              { time: '08:30', label: '高铁 G1234 出发', dot: true },
              { time: '10:12', label: '地铁 → 武康路', dot: true },
              { time: '10:40', label: ' brunch + 漫步', dot: false },
              { time: '15:00', label: '外滩 / 城隍庙', dot: false },
              { time: '17:00', label: '返程高铁 G1567', dot: true },
            ].map((step) => (
              <div key={step.label} className="flex items-center gap-2.5">
                <span className="w-8 shrink-0 text-right text-muted">{step.time}</span>
                {step.dot && (
                  <span className="h-1 w-1 shrink-0 rounded-full bg-[var(--aura-health)]" />
                )}
                {!step.dot && <span className="w-1 shrink-0" />}
                <span className="text-foreground">{step.label}</span>
              </div>
            ))}
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
