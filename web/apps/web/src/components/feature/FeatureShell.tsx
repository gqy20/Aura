'use client'

import { motion } from 'motion/react'
import type { ReactNode } from 'react'
import { SmoothScroll } from '@/components/SmoothScroll'
import { MagneticCursor } from '@/components/MagneticCursor'
import { AnnouncementBar } from '@/components/AnnouncementBar'
import { FeatureNav } from './FeatureNav'
import { cn } from '@/lib/utils'

interface FeatureShellProps {
  /** 编号 — 01/02/03（不渲染当 hideMeta=true） */
  number: string
  /** 分类 — Capability / System / Runtime（不渲染当 hideMeta=true） */
  category: string
  /** 大标题 — 80px */
  title: string
  /** 副标题 */
  subtitle: string
  /** 当前页标识（用于 nav 高亮） */
  active: 'presence' | 'memory' | 'agent' | 'tech'
  /** 主内容 */
  children: ReactNode
  /** 整页 extra className */
  className?: string
  /**
   * 页面专属背景渐变（CSS background 字符串）
   * - /presence 紫蓝
   * - /memory 青绿
   * - /agent 紫粉
   * 默认走深色底 #08090a
   */
  bgGradient?: string
  /** 隐藏标题区上方 "01 — CAPABILITY" meta chip */
  hideMeta?: boolean
  /** 隐藏顶部 AnnouncementBar（v0.4 / 测试数 / 更新日志） */
  hideAnnouncement?: boolean
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
  bgGradient,
  hideMeta = false,
  hideAnnouncement = false,
}: FeatureShellProps) {
  return (
    <SmoothScroll>
      <MagneticCursor />
      <main className="relative min-h-screen snap-y snap-mandatory overflow-x-clip overflow-y-auto">
        {/* 全局深色底 + 页面渐变（中心光晕） */}
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 -z-20"
          style={{
            background: bgGradient ?? '#08090a',
          }}
        />

        {/* 沉浸式 sub-page：nav / 内容 / footer 都铺满视口（不再被 max-w 容器限制）
            - 文字区保留 px-6 sm:px-10 lg:px-16 让阅读舒适
            - 背景渐变 / 3D 通过 HeroStage 内的 left:50% + 100vw 黑魔法撑到视口边
        */}
        {!hideAnnouncement && <AnnouncementBar />}
        <div className="px-6 sm:px-10 lg:px-16">
          <FeatureNav active={active} />

          {/* ─── 大标题区 ─── */}
          <section className="relative snap-start snap-always pt-12 pb-16 sm:pt-20 sm:pb-24">
            <div className="grid grid-cols-1 items-end gap-8 md:grid-cols-12">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
                className="md:col-span-8"
              >
                {!hideMeta && (
                  <div className="mb-6 flex items-center gap-4 font-mono text-xs uppercase tracking-[0.2em] text-muted">
                    <span className="text-accent">{number}</span>
                    <span className="h-px w-8 bg-border-strong" />
                    <span>{category}</span>
                  </div>
                )}
                <h1 className="text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-7xl lg:text-[5.5rem]">
                  {title}
                </h1>
                <p className="mt-8 max-w-2xl text-pretty text-lg leading-relaxed text-muted sm:text-xl">
                  {subtitle}
                </p>
              </motion.div>
            </div>
          </section>
        </div>

        {/* ─── 主内容 — 交给 HeroStage / 子页自行控制 padding ─── */}
        <div className={cn('flex-1 pb-24', className)}>{children}</div>

        {/* ─── Footer ─── */}
        <footer className="flex items-center justify-between border-t border-border px-6 py-8 font-mono text-xs text-muted sm:px-10 lg:px-16">
          <span>© 2026 Aura · 开源</span>
          <span>
            <span className="text-accent">{number}</span> · {category}
          </span>
        </footer>
      </main>
    </SmoothScroll>
  )
}
