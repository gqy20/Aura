'use client'

import { cn } from '@/lib/utils'

interface SectionHeadProps {
  /** 章节序号 — "01" */
  number: string
  /** 章节分类小标 — "Capability" */
  eyebrow: string
  /** 章节大标题 */
  title: string
  /** 一句话副文案 */
  description?: string
  /** 与 Hero 的色系对齐（mono eyebrow 颜色） */
  accentColor?: string
  className?: string
}

/**
 * 章节标头：editorial 风格的「章节号 + 分类 + 标题 + 副文案 + 规则线」
 *
 * 设计参考 Linear Method / Anthropic Newsroom：
 * - mono 章节号 + 分类 + 1px 细线（eyebrow）
 * - 衬线 / sans 标题（text-3xl/4xl）
 * - max-w-prose 副文案
 * - 底部 1px 规则线收尾
 */
export function SectionHead({
  number,
  eyebrow,
  title,
  description,
  accentColor = 'var(--color-accent)',
  className,
}: SectionHeadProps) {
  return (
    <header className={cn('mx-auto max-w-7xl px-6 sm:px-10 lg:px-16', className)}>
      <div className="label-mono flex items-center gap-4 text-muted">
        <span style={{ color: accentColor }}>{number}</span>
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

      <div className="mt-10 h-px w-full bg-border" aria-hidden />
    </header>
  )
}
