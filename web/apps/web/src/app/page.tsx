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
 * - 沉浸式布局：nav / Hero / Data Strip / 滚动叙事 / footer 全部铺满视口
 *   （删除原 max-w-[1400px] 容器，文字区单独加水平内边距）
 */
export default function Home() {
  return (
    <SmoothScroll>
      <MagneticCursor />
      <main className="relative min-h-screen snap-y snap-mandatory overflow-x-clip overflow-y-auto">
        {/* ─── 全局深色底（叙事段/footer 用） ─── */}
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: '#08090a' }}
        />

        {/* 沉浸式首页：nav / Hero / Data Strip 全部铺满视口
            - 文字区保留 px-6 sm:px-10 lg:px-16 让阅读舒适
            - Hero 内的 Mesh Gradient 背景 / PhoneOrb 仍 absolute 撑满 viewport
        */}
        <div className="flex min-h-[100svh] snap-start snap-always flex-col">
          <AnnouncementBar />
          {/* ─── Nav ─── */}
          <motion.nav
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
            className="flex h-20 items-center justify-between px-6 sm:px-10 lg:px-16"
          >
            <Link href="/" className="flex items-center gap-2" aria-label="Aura home">
              <AuraLogo size={28} />
              <span className="font-mono text-sm font-medium tracking-tight">
                Aura<span className="text-accent">.</span>
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
                href="/tech"
                className="text-muted transition-colors hover:text-foreground"
              >
                Tech
              </Link>
              <Link
                href="https://github.com/gqy20/Aura"
                className="text-muted transition-colors hover:text-foreground"
              >
                GitHub ↗
              </Link>
            </div>
          </motion.nav>

          {/* ─── Hero ─── */}
          <section className="relative min-h-[90vh] overflow-hidden">
            {/* Hero 内的 Mesh Gradient 背景（仅桌面，撑满整个 section） */}
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

            <div className="relative grid h-full min-h-[90vh] grid-cols-1 items-center gap-12 px-6 pb-16 pt-12 sm:px-10 md:grid-cols-2 lg:px-16">
              {/* 左：文字 */}
              <div className="flex flex-col">
                <motion.p
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.6, delay: 0.2, ease: [0.22, 1, 0.36, 1] }}
                  className="mb-12 font-mono text-xs uppercase tracking-[0.2em] text-muted"
                >
                  Android AI Companion
                </motion.p>

                <h1 className="relative z-10 max-w-xl text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-6xl lg:text-7xl">
                  <span className="block">
                    <SplitText text="长期认识你的" stagger={0.045} delay={0.4} />
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
                  Aura 不把所有能力都塞给一个模型，而是把对外办事、对内理解和长期陪伴拆成不同层次，让记忆、洞察与生活建议在手机里持续发生。
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
            <dl className="grid grid-cols-2 gap-x-12 gap-y-8 px-6 sm:grid-cols-3 sm:px-10 lg:px-16">
              {[
                { label: '双心智智能体', value: 'Cloud + Local' },
                { label: '陪伴运行时', value: 'Dream Loop' },
                { label: '生活能力', value: 'MCP' },
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
            title="它不只在你发消息时出现。"
            description="Presence、Reaction 与 Dream Loop 共同构成 Aura 的陪伴运行时。它会等待、倾听、整理，再在合适的时候回到你身边。"
          />
          <div className="border-t border-border" />
          <ScrollSection
            number="02"
            title="它不是记住一句话，而是在理解你。"
            description="记忆、洞察、来源线索与用户控制共同构成 Aura 的可信个人模型，而不是黑盒人格画像。"
          />
        </div>

        <section className="border-t border-border px-6 py-20 sm:px-10 lg:px-16 snap-start snap-always min-h-[100svh]">
          <div className="flex items-end justify-between border-b border-border pb-4">
            <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
              继续往下看
            </h2>
            <span className="font-mono text-xs text-muted">
              分层浏览
            </span>
          </div>
          <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
            如果你想继续了解 Aura，可以顺着四个方向往下看：它如何在场、如何理解你、如何连接真实生活，以及这套体验到底怎么被做出来。
          </p>

          <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            {[
              {
                href: '/presence',
                label: 'Presence',
                title: '它如何持续在场',
                desc: '从状态、反应和节制开始，看它为什么像一个会陪着你的存在。',
              },
              {
                href: '/memory',
                label: 'Memory',
                title: '它如何逐渐理解你',
                desc: '从记忆、摘要和用户控制开始，看它如何慢慢形成对你的理解。',
              },
              {
                href: '/agent',
                label: 'Agent',
                title: '它如何从聊天走向行动',
                desc: '从工具层、MCP 和生活能力开始，看它怎样把对话接到现实世界。',
              },
              {
                href: '/tech',
                label: 'Tech',
                title: '这套体验如何被做出来',
                desc: '从架构、执行路径和系统边界开始，看这套体验背后的真实技术方案。',
              },
            ].map((item, index) => (
              <Reveal
                key={item.href}
                delay={index * 70}
                className="rounded-xl border border-border p-6"
              >
                <Link href={item.href} className="group block">
                  <p className="font-mono text-xs uppercase tracking-[0.18em] text-accent">
                    {item.label}
                  </p>
                  <h3 className="mt-3 text-lg font-medium text-foreground transition-colors group-hover:text-accent">
                    {item.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted">
                    {item.desc}
                  </p>
                </Link>
              </Reveal>
            ))}
          </div>
        </section>

        {/* ─── Footer ─── */}
        <footer className="flex items-center justify-between border-t border-border px-6 py-8 font-mono text-xs text-muted sm:px-10 lg:px-16">
          <span>© 2026 Aura · 开源</span>
          <span>Home · Presence · Memory · Agent · Tech</span>
        </footer>
      </main>
    </SmoothScroll>
  )
}
