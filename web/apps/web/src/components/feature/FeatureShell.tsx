'use client'

import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { SmoothScroll } from '@/components/SmoothScroll'
import { MagneticCursor } from '@/components/MagneticCursor'
import { FeatureNav } from './FeatureNav'
import { cn } from '@/lib/utils'

interface FeatureShellProps {
  /** 编号 — 01/02/03 */
  number: string
  /** 分类 — Capability / System / Runtime */
  category: string
  /** 大标题 — 80px */
  title: string
  /** 副标题 */
  subtitle: string
  /** 当前页标识（用于 nav 高亮） */
  active: 'presence' | 'memory' | 'agent'
  /** 主内容 */
  children: ReactNode
  /** 整页 extra className */
  className?: string
}

/**
 * 特性深度页共享 layout
 *
 * - 顶部 FeatureNav（含当前页高亮）
 * - 大标题区（编号 + 分类 + 标题 + 副标题）
 * - children：左侧文字描述 / 右侧 3D canvas（由具体页面提供）
 * - 全局深色底 + Lenis + 磁性光标
 */
export function FeatureShell({
  number,
  category,
  title,
  subtitle,
  active,
  children,
  className,
}: FeatureShellProps) {
  return (
    <SmoothScroll>
      <MagneticCursor />
      <main className="relative min-h-screen overflow-hidden">
        {/* 全局深色底 */}
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{ background: '#08090a' }}
        />

        <div className="mx-auto flex min-h-screen max-w-[1400px] flex-col px-6 sm:px-10 lg:px-16">
          <FeatureNav active={active} />

          {/* ─── 大标题区 ─── */}
          <section className="relative pt-12 pb-16 sm:pt-20 sm:pb-24">
            <div className="grid grid-cols-1 items-end gap-8 md:grid-cols-12">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                className="md:col-span-8"
              >
                <div className="mb-6 flex items-center gap-4 font-mono text-xs uppercase tracking-[0.2em] text-muted">
                  <span className="text-accent">{number}</span>
                  <span className="h-px w-8 bg-border-strong" />
                  <span>{category}</span>
                </div>
                <h1 className="text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-7xl lg:text-[5.5rem]">
                  {title}
                </h1>
                <p className="mt-8 max-w-2xl text-pretty text-lg leading-relaxed text-muted sm:text-xl">
                  {subtitle}
                </p>
              </motion.div>
            </div>
          </section>

          {/* ─── 主内容 ─── */}
          <div className={cn('flex-1 pb-24', className)}>{children}</div>

          {/* ─── Footer ─── */}
          <footer className="flex items-center justify-between border-t border-border py-8 font-mono text-xs text-muted">
            <span>© 2026 Aura · Open Source</span>
            <span>
              <span className="text-accent">{number}</span> · {category}
            </span>
          </footer>
        </div>
      </main>
    </SmoothScroll>
  )
}
