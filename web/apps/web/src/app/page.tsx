'use client'

import dynamic from 'next/dynamic'
import Link from 'next/link'
import { motion } from 'motion/react'
import { SplitText } from '@/components/SplitText'
import { ScrollSection } from '@/components/ScrollSection'
import { MagneticCursor } from '@/components/MagneticCursor'
import { Reveal } from '@/components/Reveal'
import { AuraLogo } from '@/components/AuraLogo'
import { ScreenSection } from '@/components/ScreenSection'

const MeshGradient = dynamic(
  () => import('@/components/three/MeshGradient').then((m) => m.MeshGradient),
  { ssr: false },
)
const PhoneOrb = dynamic(
  () => import('@/components/three/PhoneOrb').then((m) => m.PhoneOrb),
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
          <div className="px-6 sm:px-10 lg:px-16">
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
                WebkitMaskImage: 'linear-gradient(to bottom, black 0%, black 70%, transparent 100%)',
                maskImage: 'linear-gradient(to bottom, black 0%, black 70%, transparent 100%)',
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

            <div className="relative grid w-full grid-cols-1 items-start gap-12 md:grid-cols-2">
              <div className="flex max-w-[620px] flex-col">
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
                  Aura 不把所有能力都塞给一个模型，而是把对外办事、对内理解和长期陪伴拆成不同层次，让记忆、洞察与生活建议在手机里持续发生。
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

              <div className="pointer-events-none absolute left-[58%] top-1/2 hidden h-[460px] w-[52vw] max-w-[920px] -translate-x-1/2 -translate-y-1/2 md:block">
                <PhoneOrb />
              </div>
            </div>
          </div>
        </section>

        {/* 第二、三屏：滚动叙事 */}
        <div className="border-t border-border">
          <ScrollSection
            number="01"
            title="它不只在你发消息时出现。"
            description="Presence、Reaction 与 Dream Loop 共同构成 Aura 的陪伴运行时。它会等待、倾听、整理，再在合适的时候回到你身边。"
          />
        </div>
        <div className="border-t border-border">
          <ScrollSection
            number="02"
            title="它不是记住一句话，而是在理解你。"
            description="记忆、洞察、来源线索与用户控制共同构成 Aura 的可信个人模型，而不是黑盒人格画像。"
          />
        </div>

        {/* 第四屏：四个方向导航 + 底部 footer */}
        <ScreenSection className="border-t border-border" innerClassName="max-w-7xl">
          <div className="grid grid-cols-1 gap-10 md:grid-cols-12 md:gap-16">
            <div className="md:col-span-4">
              <span className="label-mono text-muted">next</span>
              <h2 className="mt-4 max-w-sm text-balance text-3xl font-medium tracking-tight sm:text-4xl">
                从四个方向继续认识 Aura
              </h2>
              <p className="mt-4 max-w-sm text-pretty text-sm leading-relaxed text-muted sm:text-base">
                在场、理解、行动、系统。四页，刚好讲完这套产品最核心的骨架。
              </p>
            </div>

            <div className="md:col-span-8">
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {[
                  { href: '/presence', label: 'Presence', title: '它如何持续在场', desc: '状态、反应和节制' },
                  { href: '/memory', label: 'Memory', title: '它如何逐渐理解你', desc: '记忆、摘要和用户控制' },
                  { href: '/agent', label: 'Agent', title: '它如何从聊天走向行动', desc: '工具层、MCP 和生活能力' },
                  { href: '/tech', label: 'Tech', title: '这套体验如何被做出来', desc: '架构、执行路径和边界' },
                ].map((item, index) => (
                  <Reveal
                    key={item.href}
                    delay={index * 60}
                    className="rounded-xl border border-border p-6 transition-colors hover:border-border-strong"
                  >
                    <Link href={item.href} className="group block">
                      <p className="label-mono text-accent">{item.label}</p>
                      <h3 className="mt-3 text-lg font-medium text-foreground transition-colors group-hover:text-accent">
                        {item.title}
                      </h3>
                      <p className="mt-2 text-sm leading-relaxed text-muted">{item.desc}</p>
                    </Link>
                  </Reveal>
                ))}
              </div>
            </div>
          </div>

          <div className="mt-auto flex items-center justify-between border-t border-border pt-6 font-mono text-xs text-muted">
            <span>© 2026 Aura · 开源</span>
            <span>Home · Presence · Memory · Agent · Tech</span>
          </div>
        </ScreenSection>
      </main>
    </>
  )
}
