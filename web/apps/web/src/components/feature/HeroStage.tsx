'use client'

import {
  type ReactNode,
  useEffect,
  useRef,
} from 'react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { StatBlock } from '@/components/StatBlock'
import { cn } from '@/lib/utils'

if (typeof window !== 'undefined') {
  gsap.registerPlugin(ScrollTrigger)
}

export interface HeroStat {
  /** 主数字 */
  n: string
  /** 标签 */
  label: string
  /** 描述 */
  desc?: string
  /** 强调色 */
  color?: string
}

interface HeroStageProps {
  /** 已经过 dynamic 包装的 3D 组件（PresenceOrb / MemoryNetwork / AgentGraph） */
  three: ReactNode
  /** 右下/左下浮层徽标 — 例如「状态 · 思考」 */
  badge?: {
    label: string
    value: string
    color: string
  }
  /** 数字速记列表 — 3 个左右 */
  stats: HeroStat[]
  /** 数字速记位置 */
  statsPosition?: 'right' | 'bottom'
  /** 3D 下方 caption — mono 12px */
  caption?: string
  /** 自定义高度 — 默认 h-[78vh] */
  height?: string
  /** variant — 影响背景光晕、stats 强调色 */
  variant?: 'presence' | 'memory' | 'agent'
  /** Reveal 错落延迟基线 */
  delayBase?: number
  /** 关闭 GSAP 入场动画（用于测试 / 静态截图） */
  disableEnterAnimation?: boolean
  className?: string
}

/**
 * 第一屏统一容器：3D 撑满视口 + 数字速记浮层
 *
 * **关键设计**：3D 突破 FeatureShell max-w-[1400px] 容器限制，撑到视口边缘
 * - 3D 容器：`relative left-1/2 -translate-x-1/2 w-screen`（视口宽度）
 * - 数字速记列：绝对定位到 max-w 容器的右内侧（贴着 1400px 容器右边）
 * - 这样 3D 视觉冲击力 + 数字可读性兼得
 *
 * 视觉规则：
 * - 3D 撑到 78vh（mobile 上 aspect-[4/3] 回退）
 * - 数字速记在视口右侧、垂直居中
 * - 3D 无圆角无边框（Bruno Simon / Vercel 模式）
 * - 徽标浮在 3D 左上角
 *
 * 动效：
 * - 入场：GSAP ScrollTrigger — 3D 从 0.92 scale + 0 opacity 飞到 1
 * - 滚动：3D 整体跟随 scroll 微缩放（max 0.97）
 * - prefers-reduced-motion 退化为静态
 *
 * 设计参考：
 * - Bruno Simon (bruno-simon.com) — 全屏沉浸 3D
 * - Vercel (vercel.com) — 居中标题 + 3D 在下沿
 * - Linear / Midday — 居中大字
 */
export function HeroStage({
  three,
  badge,
  stats,
  statsPosition = 'right',
  caption,
  height = 'h-[78vh]',
  variant = 'presence',
  delayBase = 0,
  disableEnterAnimation = false,
  className,
}: HeroStageProps) {
  const sectionRef = useRef<HTMLElement>(null)
  const threeWrapRef = useRef<HTMLDivElement>(null)

  // variant → 强调色（与 FeatureShell bgGradient 配合）
  const variantAccent =
    variant === 'presence'
      ? '#7c5cff'
      : variant === 'memory'
        ? '#5cffb0'
        : '#ff7c9c'

  /**
   * GSAP 入场 + 视差
   * - 3D wrap：进入视口 → scale 0.92 opacity 0 → 1
   * - 3D wrap：scroll-bound 视差（max scale 0.97 at end）
   */
  useEffect(() => {
    if (typeof window === 'undefined') return
    if (disableEnterAnimation) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return

    const section = sectionRef.current
    const wrap = threeWrapRef.current
    if (!section || !wrap) return

    const ctx = gsap.context(() => {
      // 父容器已铺满 viewport，无需 xPercent 黑魔法
      gsap.fromTo(
        wrap,
        { scale: 0.92, opacity: 0 },
        {
          scale: 1,
          opacity: 1,
          duration: 1.2,
          ease: 'expo.out',
          scrollTrigger: {
            trigger: section,
            start: 'top 85%',
            once: true,
          },
        },
      )

      gsap.to(wrap, {
        scale: 0.97,
        ease: 'none',
        scrollTrigger: {
          trigger: section,
          start: 'top top',
          end: 'bottom top',
          scrub: 0.5,
        },
      })
    }, section)

    return () => ctx.revert()
  }, [disableEnterAnimation])

  /**
   * 数字速记列：
   * - right position 模式：绝对定位在 max-w 容器的右内侧
   *   right offset = max(lg:px-16=64px, 50vw - 700px)
   *   - 视口 1920：right 64px
   *   - 视口 1400：right 0（紧贴右边）
   *   - 视口 < 1400：right 64px（容器 padding）
   * - bottom position 模式：在 3D 之下，单列
   */
  const statsBlock = (
    <div
      className={cn(
        'flex flex-col gap-10 md:gap-12',
        statsPosition === 'right'
          ? 'md:justify-center md:py-4'
          : 'mt-10 md:mt-14',
      )}
    >
      {stats.map((s, i) => (
        <StatBlock
          key={s.label}
          n={s.n}
          label={s.label}
          desc={s.desc}
          color={s.color ?? variantAccent}
          delay={delayBase + i * 80}
          size="md"
        />
      ))}
    </div>
  )

  return (
    <section
      ref={sectionRef}
      className={cn('relative', className)}
      style={
        statsPosition === 'right'
          ? ({
              ['--stats-w' as string]: '340px',
              ['--hero-gap' as string]: '64px',
              ['--hero-px' as string]: '64px',
            } as React.CSSProperties)
          : ({
              ['--hero-px' as string]: '64px',
            } as React.CSSProperties)
      }
    >
      {/*
        3D 主体 — 宽度由 CSS 变量决定
        - right 模式：让出右侧 stats 列宽 + gap
        - 默认：左右各 --hero-px 边距
      */}
      <div
        ref={threeWrapRef}
        className={cn(
          'will-change-transform relative overflow-hidden',
          'aspect-[16/9] md:aspect-auto',
          height,
        )}
        style={
          statsPosition === 'right'
            ? ({
                width:
                  'calc(100vw - 2 * var(--hero-px) - var(--stats-w) - var(--hero-gap))',
                marginLeft: 'var(--hero-px)',
              } as React.CSSProperties)
            : ({
                width: 'calc(100vw - 2 * var(--hero-px))',
                marginLeft: 'var(--hero-px)',
              } as React.CSSProperties)
        }
      >
        {three}

        {/* 左上浮层徽标 */}
        {badge && (
          <div className="pointer-events-none absolute left-6 top-6 z-10 flex items-center gap-2 font-mono text-xs md:left-10">
            <span
              className="h-2 w-2 animate-pulse rounded-full"
              style={{ backgroundColor: badge.color }}
            />
            <span className="text-muted">{badge.label}</span>
            <span className="font-medium text-foreground">
              {badge.value}
            </span>
          </div>
        )}

        {/* 渐隐底边 */}
        <div
          aria-hidden
          className="pointer-events-none absolute bottom-0 left-0 right-0 z-10 h-32"
          style={{
            background:
              'linear-gradient(to bottom, transparent 0%, rgba(8,9,10,0.6) 100%)',
          }}
        />
      </div>

      {/* 数字速记列 — 浮在 3D 右侧（视口最右边距 = hero-px） */}
      {statsPosition === 'right' && (
        <div
          className={cn(
            'pointer-events-none absolute z-20 top-1/2 -translate-y-1/2',
          )}
          style={
            {
              left:
                'calc(var(--hero-px) + (100vw - 2 * var(--hero-px) - var(--stats-w) - var(--hero-gap)) + var(--hero-gap))',
              width: 'var(--stats-w)',
            } as React.CSSProperties
          }
        >
          <div className="pointer-events-auto w-full">{statsBlock}</div>
        </div>
      )}

      {/* caption */}
      {caption && (
        <p className="mt-4 px-6 font-mono text-xs text-muted sm:px-10 lg:px-16">
          {caption}
        </p>
      )}
    </section>
  )
}
