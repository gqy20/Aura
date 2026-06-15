'use client'

import { useEffect, useRef } from 'react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

interface TimelineLightUpProps {
  /** 时间点数组 — 每个节点渲染为一列 */
  points: Array<{
    time: string
    color: string
    label: string
    note?: string
  }>
  /** 横轴连接线颜色（默认 border） */
  axisColor?: string
}

if (typeof window !== 'undefined') {
  gsap.registerPlugin(ScrollTrigger)
}

/**
 * 24h presence timeline 节点点亮动画
 *
 * 设计要点：
 * - 节点初始：opacity 0, scale 0.4, 颜色 muted（轴色）
 * - 进入视口触发：按索引 stagger，依次 (1) 节点放大到 1 + 切到自己颜色，(2) 时间/标签淡入
 * - 横轴线：宽度从 0 → 100% 同步推进
 * - 用 GSAP ScrollTrigger 而不是 motion whileInView：
 *   1. IntersectionObserver 在 fullPage 截图场景不可靠
 *   2. GSAP 时间线便于精确编排顺序
 *   3. prefers-reduced-motion 下整体退化为直接显示
 */
export function TimelineLightUp({ points, axisColor = 'rgba(255,255,255,0.2)' }: TimelineLightUpProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const axisRef = useRef<HTMLDivElement>(null)
  const dotsRef = useRef<(HTMLSpanElement | null)[]>([])
  const metaRef = useRef<(HTMLDivElement | null)[]>([])

  useEffect(() => {
    if (typeof window === 'undefined') return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return

    const ctx = gsap.context(() => {
      // 横轴线展开
      if (axisRef.current) {
        gsap.fromTo(
          axisRef.current,
          { scaleX: 0, transformOrigin: 'left center' },
          {
            scaleX: 1,
            ease: 'none',
            scrollTrigger: {
              trigger: containerRef.current,
              start: 'top 80%',
              end: 'bottom 50%',
              scrub: 0.5,
            },
          },
        )
      }

      // 每个节点单独一个 ScrollTrigger，按时间顺序点亮
      dotsRef.current.forEach((dot, i) => {
        if (!dot) return
        const meta = metaRef.current[i]

        gsap
          .timeline({
            scrollTrigger: {
              trigger: containerRef.current,
              start: `top+=${i * 8}% center`,
              once: true,
            },
          })
          .fromTo(
            dot,
            { scale: 0.3, opacity: 0.3, backgroundColor: axisColor },
            {
              scale: 1,
              opacity: 1,
              backgroundColor: points[i].color,
              boxShadow: `0 0 16px ${points[i].color}80`,
              duration: 0.5,
              ease: 'power2.out',
            },
          )
          .fromTo(
            meta,
            { opacity: 0, y: 6 },
            { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' },
            '-=0.2',
          )
      })
    }, containerRef)

    return () => ctx.revert()
  }, [points, axisColor])

  return (
    <div ref={containerRef} className="relative">
      {/* 横向轴线（带 GSAP scaleX 动画） */}
      <div
        ref={axisRef}
        className="absolute left-0 right-0 top-[14px] h-px bg-border"
      />

      <div className="relative grid grid-cols-8 gap-2">
        {points.map((p, i) => (
          <div key={p.time} className="flex flex-col items-start">
            {/* 节点圆点 */}
            <span
              ref={(el) => {
                dotsRef.current[i] = el
              }}
              className="block h-3.5 w-3.5 rounded-full"
              style={{ backgroundColor: axisColor }}
            />
            {/* 时间 + 标签 + 备注（GSAP stagger 后才显现） */}
            <div
              ref={(el) => {
                metaRef.current[i] = el
              }}
              className="mt-2"
              style={{ opacity: 0 }}
            >
              <span className="font-mono text-[10px] text-muted">{p.time}</span>
              <p className="mt-1 text-xs font-medium text-foreground">{p.label}</p>
              {p.note && <p className="text-[10px] text-muted">{p.note}</p>}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
