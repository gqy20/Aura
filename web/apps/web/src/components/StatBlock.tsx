'use client'

import { type CSSProperties } from 'react'
import { cn } from '@/lib/utils'
import { Reveal } from '@/components/Reveal'

interface StatBlockProps {
  /** 主数字 — 例如 "11" / "5" / "3" */
  n: string
  /** 数字下方小标签 — 例如 "状态" / "反应" / "节流" */
  label: string
  /** 详细描述 — 一行 14-22 字 */
  desc?: string
  /** 强调色 — 默认 accent (#7c5cff) */
  color?: string
  /** 数字字号 — sm(60) / md(72) / lg(96) */
  size?: 'sm' | 'md' | 'lg'
  /** 数字字体族 */
  family?: 'serif' | 'mono'
  /** Reveal 错落延迟 ms */
  delay?: number
  className?: string
}

/**
 * 大数字速记块 — 浮在 3D hero 右侧，给"印象分"
 *
 * 设计：
 * - 数字超大（衬线/等宽可切换）— text-7xl (112px) / text-8xl (144px)
 * - 默认用衬线（Instrument Serif），给人文/品牌感
 * - 标签用 mono uppercase tracking-wider
 * - 描述 14-16px muted，可选
 * - 入场用 Reveal 组件做 fade-up（SSR + 减少动效安全）
 *
 * 来源参考：
 * - Midday hero（衬线大字）
 * - Linear / Vercel（mono 数字）
 * - Instrument Serif（2025 Awwwards 流行）
 */
export function StatBlock({
  n,
  label,
  desc,
  color,
  size = 'md',
  family = 'serif',
  delay = 0,
  className,
}: StatBlockProps) {
  const numberStyle: CSSProperties = color ? { color } : {}
  const numberClass = cn(
    'leading-none',
    size === 'sm' ? 'text-6xl' : size === 'lg' ? 'text-8xl' : 'text-7xl',
    family === 'serif' ? 'font-serif' : 'font-mono font-medium tracking-tight',
  )

  return (
    <Reveal
      direction="y"
      delay={delay}
      distance={16}
      duration={700}
      className={cn('flex flex-col gap-2', className)}
    >
      <span className={numberClass} style={numberStyle}>
        {n}
      </span>
      <span className="font-mono text-xs uppercase tracking-[0.2em] text-muted">
        {label}
      </span>
      {desc && (
        <p className="text-sm leading-relaxed text-muted/80">{desc}</p>
      )}
    </Reveal>
  )
}
