'use client'

import dynamic from 'next/dynamic'
import Link from 'next/link'
import { motion } from 'motion/react'
import { SplitText } from '@/components/SplitText'
import { SmoothScroll } from '@/components/SmoothScroll'
import { ScrollSection } from '@/components/ScrollSection'
import { MagneticCursor } from '@/components/MagneticCursor'

// 3D 组件仅客户端渲染，禁用 SSR
const MeshGradient = dynamic(
  () => import('@/components/three/MeshGradient').then((m) => m.MeshGradient),
  { ssr: false },
)
const PhoneOrb = dynamic(
  () => import('@/components/three/PhoneOrb').then((m) => m.PhoneOrb),
  { ssr: false },
)

/**
 * 首页 — P1 阶段
 *
 * - Lenis 平滑滚动（桌面端）
 * - SplitText 逐字符进场
 * - Mesh Gradient 背景 + 3D 手机主视觉
 * - 2 段滚动叙事（Presence / Memory）
 * - 移动端降级
 */
export default function Home() {
  return (
    <SmoothScroll>
      <MagneticCursor />
      <main className="relative min-h-screen overflow-hidden">
        {/* ─── 全局深色底（叙事段/footer 用） ─── */}
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: '#08090a' }}
        />

        <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-8 sm:px-12">
          {/* ─── Nav ─── */}
          <motion.nav
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
            className="flex h-20 items-center justify-between"
          >
            <Link href="/" className="font-mono text-sm font-medium tracking-tight">
              aura<span className="text-accent">.</span>
            </Link>
            <div className="flex items-center gap-8 text-sm">
              <Link
                href="https://github.com"
                className="text-muted transition-colors hover:text-foreground"
              >
                GitHub ↗
              </Link>
            </div>
          </motion.nav>

          {/* ─── Hero ─── */}
          <section className="relative min-h-[90vh] overflow-hidden">
            {/* Hero 内的 Mesh Gradient 背景（仅桌面） */}
            <div
              className="pointer-events-none absolute inset-0 hidden md:block"
              style={{
                WebkitMaskImage:
                  'linear-gradient(to bottom, black 0%, black 70%, transparent 100%)',
                maskImage:
                  'linear-gradient(to bottom, black 0%, black 70%, transparent 100%)',
              }}
            >
              <MeshGradient />
            </div>

            {/* 移动端 Hero 降级 */}
            <div
              aria-hidden
              className="pointer-events-none absolute inset-0 md:hidden"
              style={{
                background:
                  'radial-gradient(ellipse 60% 40% at 15% 0%, rgba(124, 92, 255, 0.12), transparent), radial-gradient(ellipse 50% 35% at 85% 100%, rgba(124, 92, 255, 0.06), transparent)',
              }}
            />

            <div className="relative grid h-full min-h-[90vh] grid-cols-1 items-center gap-12 pb-16 pt-12 md:grid-cols-2">
              {/* 左：文字 */}
              <div className="flex flex-col">
                <motion.p
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.6, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
                  className="mb-12 font-mono text-xs uppercase tracking-[0.2em] text-muted"
                >
                  v0 · coming soon
                </motion.p>

                <h1 className="relative z-10 max-w-xl text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-6xl lg:text-7xl">
                  <span className="block">
                    <SplitText text="The AI companion" stagger={0.045} delay={0.4} />
                  </span>
                  <span className="block">
                    <SplitText
                      text="that lives with you."
                      stagger={0.045}
                      delay={0.95}
                    />
                  </span>
                </h1>

                <motion.p
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, delay: 1.6, ease: [0.22, 1, 0.36, 1] }}
                  className="mt-10 max-w-xl text-pretty text-lg leading-relaxed text-muted sm:text-xl"
                >
                  Aura 是一个开源 AI 陪伴应用，把 Presence（存在感）、Memory（记忆）和 Local LLM
                  装进你的口袋。
                </motion.p>

                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, delay: 1.8, ease: [0.22, 1, 0.36, 1] }}
                  className="mt-12 flex flex-wrap items-center gap-4"
                >
                  <Link
                    href="#"
                    className="group inline-flex h-12 items-center justify-center rounded-full bg-foreground px-7 text-sm font-medium text-background transition-all hover:bg-foreground/90"
                  >
                    Start
                    <span className="ml-1 transition-transform group-hover:translate-x-0.5">
                      →
                    </span>
                  </Link>
                  <Link
                    href="https://github.com"
                    className="inline-flex h-12 items-center justify-center rounded-full border border-border-strong px-7 text-sm font-medium text-foreground transition-all hover:bg-subtle"
                  >
                    View on GitHub
                  </Link>
                </motion.div>
              </div>

              {/* 右：3D 主视觉（仅桌面，浮于背景层） */}
              <div className="pointer-events-none absolute right-0 top-1/2 hidden h-[420px] w-[55%] -translate-y-1/2 md:block">
                <PhoneOrb />
              </div>
            </div>
          </section>

          {/* ─── Data Strip ─── */}
          <section className="border-t border-border py-12">
            <dl className="grid grid-cols-2 gap-x-12 gap-y-8 sm:grid-cols-4">
              {[
                { label: 'Tests', value: '41' },
                { label: 'Modules', value: '7' },
                { label: 'Lines of Kotlin', value: '12k+' },
                { label: 'License', value: 'MIT' },
              ].map((stat, i) => (
                <motion.div
                  key={stat.label}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, margin: '-10%' }}
                  transition={{ duration: 0.6, delay: i * 0.1, ease: [0.22, 1, 0.36, 1] }}
                  className="flex flex-col gap-2"
                >
                  <dt className="text-3xl font-medium tracking-tight sm:text-4xl">
                    {stat.value}
                  </dt>
                  <dd className="font-mono text-xs uppercase tracking-wider text-muted">
                    {stat.label}
                  </dd>
                </motion.div>
              ))}
            </dl>
          </section>
        </div>

        {/* ─── 滚动叙事 ─── */}
        <div className="border-t border-border">
          <ScrollSection
            number="01"
            title="Presence that lives with you."
            description="Aura 不只是 chat 工具，而是一个有“存在感”的陪伴体。它能感知你的设备状态、情绪、时间，适时地响应或沉默。"
          />
          <div className="border-t border-border" />
          <ScrollSection
            number="02"
            title="Memory that grows over time."
            description="每一次对话都被结构化地保存，不是简单日志，而是可被 LLM 调用的记忆图谱。从早安到晚安，从今天到明年。"
          />
        </div>

        {/* ─── Footer ─── */}
        <div className="mx-auto max-w-6xl px-8 sm:px-12">
          <footer className="flex items-center justify-between border-t border-border py-8 font-mono text-xs text-muted">
            <span>© 2026 Aura · Open Source</span>
            <span>P1 · Lenis + 滚动叙事</span>
          </footer>
        </div>
      </main>
    </SmoothScroll>
  )
}
