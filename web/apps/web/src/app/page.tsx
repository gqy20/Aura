'use client'

import dynamic from 'next/dynamic'
import Link from 'next/link'
import { motion } from 'motion/react'
import { SplitText } from '@/components/SplitText'
import { SmoothScroll } from '@/components/SmoothScroll'
import { ScrollSection } from '@/components/ScrollSection'
import { MagneticCursor } from '@/components/MagneticCursor'
import { AnnouncementBar } from '@/components/AnnouncementBar'
import { Reveal } from '@/components/Reveal'
import { AuraLogo } from '@/components/AuraLogo'

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

        <div className="mx-auto flex min-h-screen max-w-[1400px] flex-col px-6 sm:px-10 lg:px-16">
          <AnnouncementBar />
          {/* ─── Nav ─── */}
          <motion.nav
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
            className="flex h-20 items-center justify-between"
          >
            <Link href="/" className="flex items-center gap-2" aria-label="Aura home">
              <AuraLogo size={28} />
              <span className="font-mono text-sm font-medium tracking-tight">
                aura<span className="text-accent">.</span>
              </span>
            </Link>
            <div className="flex items-center gap-6 text-sm sm:gap-8">
              <Link
                href="/presence"
                className="text-muted transition-colors hover:text-foreground"
              >
                Presence
              </Link>
              <Link
                href="/memory"
                className="text-muted transition-colors hover:text-foreground"
              >
                Memory
              </Link>
              <Link
                href="/agent"
                className="text-muted transition-colors hover:text-foreground"
              >
                Agent
              </Link>
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
                  v0 · 即将上线
                </motion.p>

                <h1 className="relative z-10 max-w-xl text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-6xl lg:text-7xl">
                  <span className="block">
                    <SplitText text="与你同行的" stagger={0.045} delay={0.4} />
                  </span>
                  <span className="block">
                    <SplitText
                      text="AI 陪伴。"
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
                  Aura 是一款 AI 陪伴应用，把存在感、记忆与本地大模型装进你的口袋。
                </motion.p>

                <motion.div
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, delay: 1.8, ease: [0.22, 1, 0.36, 1] }}
                  className="mt-12 flex flex-wrap items-center gap-3"
                >
                  {/* 主 CTA：下载 Aura */}
                  <Link
                    href="https://github.com/gqy20/Aura/releases"
                    className="group inline-flex h-12 items-center justify-center rounded-full bg-foreground px-7 text-sm font-medium text-background transition-all hover:bg-foreground/90"
                  >
                    下载 Aura
                    <span className="ml-1.5 transition-transform group-hover:translate-x-0.5">
                      ↓
                    </span>
                  </Link>
                  {/* 次 CTA：源码 */}
                  <Link
                    href="https://github.com/gqy20/Aura"
                    target="_blank"
                    rel="noopener"
                    className="inline-flex h-12 items-center justify-center gap-2 rounded-full border border-border-strong px-6 text-sm font-medium text-foreground transition-all hover:bg-subtle"
                  >
                    <svg
                      className="h-4 w-4"
                      viewBox="0 0 24 24"
                      fill="currentColor"
                      aria-hidden
                    >
                      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
                    </svg>
                    在 GitHub 上查看
                  </Link>
                  {/* 文字链：阅读文档 */}
                  <Link
                    href="https://github.com/gqy20/Aura#readme"
                    target="_blank"
                    rel="noopener"
                    className="group ml-2 inline-flex h-12 items-center text-sm text-muted transition-colors hover:text-foreground"
                  >
                    阅读文档
                    <span className="ml-1 transition-transform group-hover:translate-x-0.5">
                      →
                    </span>
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
            <dl className="grid grid-cols-2 gap-x-12 gap-y-8 sm:grid-cols-3">
              {[
                { label: '模块数', value: '7' },
                { label: 'Kotlin 代码行', value: '12k+' },
                { label: '许可证', value: 'MIT' },
              ].map((stat, i) => (
                <Reveal
                  key={stat.label}
                  delay={i * 100}
                  className="flex flex-col gap-2"
                >
                  <dt className="text-3xl font-medium tracking-tight sm:text-4xl">
                    {stat.value}
                  </dt>
                  <dd className="font-mono text-xs uppercase tracking-wider text-muted">
                    {stat.label}
                  </dd>
                </Reveal>
              ))}
            </dl>
          </section>
        </div>

        {/* ─── 滚动叙事 ─── */}
        <div className="border-t border-border">
          <ScrollSection
            number="01"
            title="永远在那，从不打扰。"
            description="Aura 不只是聊天工具，而是一个有「存在感」的陪伴体。它能感知你的设备状态、情绪、时间，适时地响应或沉默。"
          />
          <div className="border-t border-border" />
          <ScrollSection
            number="02"
            title="它替你记住一切。"
            description="每一次对话都被结构化地保存，不是简单日志，而是可被大模型调用的记忆图谱。从早安到晚安，从今天到明年。"
          />
        </div>

        {/* ─── Footer ─── */}
        <div className="mx-auto max-w-6xl px-8 sm:px-12">
          <footer className="flex items-center justify-between border-t border-border py-8 font-mono text-xs text-muted">
            <span>© 2026 Aura · 开源</span>
            <span>P2 · 三个特性深度页</span>
          </footer>
        </div>
      </main>
    </SmoothScroll>
  )
}
