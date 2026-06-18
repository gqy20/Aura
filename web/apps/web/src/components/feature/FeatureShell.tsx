'use client'

import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { MagneticCursor } from '@/components/MagneticCursor'
import { FeatureNav } from './FeatureNav'
import { cn } from '@/lib/utils'

interface FeatureShellProps {
  number: string
  category: string
  title: string
  active: 'presence' | 'memory' | 'agent' | 'tech'
  /** 首屏的 3D 主视觉 — 与标题共享第一屏 */
  heroStage: ReactNode
  /** 后续 ChapterBlock 列表（不再强制 100svh，自然撑高） */
  children: ReactNode
  className?: string
  bgGradient?: string
  hideMeta?: boolean
}

/**
 * 特性深度页共享 layout
 *
 * - 第一屏（snap，整屏 100svh）：FeatureNav + 大标题 + HeroStage 3D
 * - children：ScreenSection 列表（每个自带 snap-start snap-always）
 * - 最后一屏（snap）：footer
 *
 * 使用浏览器原生 CSS scroll-snap（globals.css 配 scroll-snap-type: y mandatory）
 */
export function FeatureShell({
  number,
  category,
  title,
  active,
  heroStage,
  children,
  className,
  bgGradient,
  hideMeta = false,
}: FeatureShellProps) {
  return (
    <>
      <MagneticCursor />
      <main
        className="relative min-h-screen overflow-x-clip"
        style={{ background: bgGradient ?? '#08090a' }}
      >
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: bgGradient ?? '#08090a' }}
        />

        {/* 第一屏：nav + 标题 + 3D 共享 100svh */}
        <section className="relative flex h-[100svh] snap-start snap-always flex-col overflow-hidden">
          <div className="px-6 sm:px-10 lg:px-16">
            <FeatureNav active={active} />
          </div>

          <div className="flex flex-1 flex-col overflow-hidden px-6 pt-6 pb-12 sm:px-10 sm:pt-8 sm:pb-16 lg:px-16">
            <div className="grid grid-cols-1 items-end gap-6 md:grid-cols-12">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                className="md:col-span-9"
              >
                {!hideMeta && (
                  <div className="mb-4 flex items-center gap-4 label-mono text-xs text-muted">
                    <span className="text-accent">{number}</span>
                    <span className="h-px w-8 bg-border-strong" />
                    <span>{category}</span>
                  </div>
                )}
                <h1 className="text-balance text-3xl font-medium leading-display tracking-tight sm:text-4xl md:text-5xl">
                  {title}
                </h1>
              </motion.div>
            </div>

            {/* 3D 主视觉区 — 占满首屏剩余空间 */}
            <div className="mt-6 flex-1">{heroStage}</div>
          </div>
        </section>

        {/* 后续 ChapterBlock 列表 — 不再强制 100svh，章节间由 ChapterBlock 自己管 breathing room */}
        <div className={cn(className)}>{children}</div>
      </main>
    </>
  )
}
