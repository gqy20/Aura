'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface ChapterBlockProps {
  /** 章节序号 — "01" */
  number: string
  /** 章节分类小标 — "Capability" */
  eyebrow: string
  /** 章节大标题 */
  title: string
  /** 一句话副文案 */
  description?: string
  /** 章节最大宽度策略 */
  width?: 'prose' | 'wide' | 'full'
  /** 章节顶端是否显示 1px 规则线 */
  withRule?: boolean
  /** 章节内容 */
  children?: ReactNode
  className?: string
}

/**
 * 章节容器：取代 ScreenSection 作为子页面 body 容器
 *
 * - 不再强制 100svh：内容自然撑高，章节间用 py-32 / py-48 做 breathing room
 * - 内部最大宽度策略：
 *   - prose（默认）：max-w-prose（≈ 65ch），适合 editorial 长文
 *   - wide：max-w-7xl（1280px），适合 2-3 列卡片墙
 *   - full：max-w-[1400px]，适合全宽视觉
 */
export function ChapterBlock({
  number,
  eyebrow,
  title,
  description,
  width = 'wide',
  withRule = true,
  children,
  className,
}: ChapterBlockProps) {
  const innerWidth =
    width === 'prose'
      ? 'max-w-prose'
      : width === 'full'
        ? 'max-w-[1400px]'
        : 'max-w-7xl'

  return (
    <section
      className={cn(
        'relative flex min-h-[100svh] snap-start snap-always flex-col justify-center px-6 pt-24 pb-24 sm:px-10 sm:pt-32 sm:pb-32 lg:px-16 lg:pt-40 lg:pb-40',
        className,
      )}
    >
      {withRule && (
        <div
          aria-hidden
          className="absolute inset-x-0 top-0 h-px bg-border"
        />
      )}

      <div className={cn('mx-auto', innerWidth)}>
        <header className="mb-16 sm:mb-20">
          <div className="label-mono flex items-center gap-4 text-muted">
            <span className="text-accent">{number}</span>
            <span className="h-px w-8 bg-border-strong" aria-hidden />
            <span>{eyebrow}</span>
          </div>

          <h2 className="mt-6 max-w-4xl text-balance text-3xl font-medium leading-display tracking-tight sm:text-4xl md:text-5xl">
            {title}
          </h2>

          {description && (
            <p className="mt-6 max-w-prose text-pretty text-base leading-relaxed text-muted sm:text-lg">
              {description}
            </p>
          )}
        </header>

        {children}
      </div>
    </section>
  )
}
