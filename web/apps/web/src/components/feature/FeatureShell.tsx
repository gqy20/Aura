'use client'

import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { SmoothScroll } from '@/components/SmoothScroll'
import { MagneticCursor } from '@/components/MagneticCursor'
import { AnnouncementBar } from '@/components/AnnouncementBar'
import { FeatureNav } from './FeatureNav'
import { cn } from '@/lib/utils'

interface FeatureShellProps {
  number: string
  category: string
  title: string
  subtitle: string
  active: 'presence' | 'memory' | 'agent' | 'tech'
  /** 首屏的 3D 主视觉 — 与标题共享第一屏（snap 屏） */
  heroStage: ReactNode
  /** 后续 ScreenSection 列表 */
  children: ReactNode
  className?: string
  bgGradient?: string
  hideMeta?: boolean
  hideAnnouncement?: boolean
}

/**
 * 特性深度页共享 layout
 *
 * - 顶部 FeatureNav
 * - 第一屏（snap）：大标题区 + HeroStage 3D 共享 100svh
 * - children：ScreenSection 列表（每个自带 data-snap）
 * - 最后一屏（snap）：footer
 */
export function FeatureShell({
  number,
  category,
  title,
  subtitle,
  active,
  heroStage,
  children,
  className,
  bgGradient,
  hideMeta = false,
  hideAnnouncement = false,
}: FeatureShellProps) {
  return (
    <SmoothScroll>
      <MagneticCursor />
      <main
        className="relative min-h-screen overflow-x-clip"
        style={{ ['--shell-top-h' as string]: hideAnnouncement ? '5rem' : '7.25rem' }}
      >
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: bgGradient ?? '#08090a' }}
        />

        {!hideAnnouncement && <AnnouncementBar />}
        <div className="px-6 sm:px-10 lg:px-16">
          <FeatureNav active={active} />
        </div>

        {/* 首屏：标题 + 3D 共享 100svh */}
        <section
          data-snap
          className="relative flex min-h-[calc(100svh-var(--shell-top-h))] flex-col py-8 sm:py-10"
        >
          <div className="px-6 sm:px-10 lg:px-16">
            <div className="grid grid-cols-1 items-end gap-6 md:grid-cols-12">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                className="md:col-span-9"
              >
                {!hideMeta && (
                  <div className="mb-4 flex items-center gap-4 font-mono text-xs uppercase tracking-[0.2em] text-muted">
                    <span className="text-accent">{number}</span>
                    <span className="h-px w-8 bg-border-strong" />
                    <span>{category}</span>
                  </div>
                )}
                <h1 className="text-balance text-4xl font-medium leading-[1.05] tracking-tight sm:text-5xl md:text-6xl lg:text-[5rem]">
                  {title}
                </h1>
                <p className="mt-5 max-w-2xl text-pretty text-base leading-relaxed text-muted sm:text-lg md:text-xl">
                  {subtitle}
                </p>
              </motion.div>
            </div>
          </div>

          {/* 3D 主视觉区 — 占满首屏剩余空间 */}
          <div className="mt-6 flex-1 px-6 sm:px-10 lg:px-16">{heroStage}</div>
        </section>

        {/* 后续 ScreenSection 列表 */}
        <div className={cn('pb-24', className)}>{children}</div>

        {/* footer snap 屏 */}
        <section
          data-snap
          className="border-t border-border px-6 py-10 sm:px-10 lg:px-16"
        >
          <div className="flex items-center justify-between font-mono text-xs text-muted">
            <span>© 2026 Aura · 开源</span>
            <span>
              <span className="text-accent">{number}</span> · {category}
            </span>
          </div>
        </section>
      </main>
    </SmoothScroll>
  )
}
