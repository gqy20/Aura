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
  subtitle: string
  active: 'presence' | 'memory' | 'agent' | 'tech'
  /** 首屏的 3D 主视觉 — 与标题共享第一屏 */
  heroStage: ReactNode
  /** 后续 ScreenSection 列表 */
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
  subtitle,
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
                <h1 className="text-balance text-4xl font-medium leading-display tracking-tight sm:text-5xl md:text-6xl lg:text-[5rem]">
                  {title}
                </h1>
                <p className="mt-5 max-w-2xl text-pretty text-base leading-relaxed text-muted sm:text-lg md:text-xl">
                  {subtitle}
                </p>
              </motion.div>
            </div>

            {/* 3D 主视觉区 — 占满首屏剩余空间 */}
            <div className="mt-6 flex-1">{heroStage}</div>
          </div>
        </section>

        {/* 后续 ScreenSection 列表 */}
        <div className={cn('pb-24', className)}>{children}</div>

        {/* footer snap 屏 — 必须 h-[100svh] 才能被浏览器 snap 停在这屏 */}
        <section className="flex h-[100svh] snap-start snap-always flex-col justify-end border-t border-border px-6 pt-12 pb-12 sm:px-10 sm:pb-16 lg:px-16">
          <div className="flex items-center justify-between font-mono text-xs text-muted">
            <span>© 2026 Aura · 开源</span>
            <span>
              <span className="text-accent">{number}</span> · {category}
            </span>
          </div>
        </section>
      </main>
    </>
  )
}
