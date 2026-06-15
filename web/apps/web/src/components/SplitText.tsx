'use client'

import { useEffect, useState } from 'react'
import { cn } from '@/lib/utils'

interface SplitTextProps {
  text: string
  className?: string
  stagger?: number
  delay?: number
  byWord?: boolean
  trigger?: 'mount' | 'view'
}

/**
 * 文字逐字符/逐词进场动画
 * - 默认渲染显示（SSR / 爬虫 / fullPage 截图友好）
 * - 客户端 mount 后用 CSS @keyframes 跑一次轻量 stagger 入场
 *   （从 opacity 0 + translateY 0.3em → 1 + 0，rotateX -30° → 0）
 * - 无障碍：prefers-reduced-motion 直接显示
 *
 * 不用 motion 是因为 motion 的 `initial` 默认值会让 fullPage 截图看不到字符
 */
export function SplitText({
  text,
  className,
  stagger = 0.04,
  delay = 0,
  byWord = false,
}: SplitTextProps) {
  const [reduced, setReduced] = useState(true) // SSR + 初始 = 不跑动画
  const [animate, setAnimate] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)')
    if (mql.matches) {
      setReduced(true)
      setAnimate(false)
      return
    }
    setReduced(false)
    // 下一帧再启动动画，确保元素已渲染
    const id = window.requestAnimationFrame(() => setAnimate(true))
    return () => window.cancelAnimationFrame(id)
  }, [])

  const units = byWord ? text.split(/(\s+)/) : Array.from(text)

  return (
    <span
      className={cn('inline', className)}
      style={{ perspective: 1000, whiteSpace: 'pre' }}
    >
      {units.map((unit, i) => {
        const isSpace = /^\s+$/.test(unit)
        const baseStyle: React.CSSProperties = {
          display: 'inline-block',
          transformOrigin: '0% 100%',
          marginRight: '0.04em',
          // 默认 visible（不跑动画时）
          opacity: 1,
          transform: 'none',
        }
        if (animate && !reduced && !isSpace) {
          baseStyle.animation = `split-in 0.8s cubic-bezier(0.22, 1, 0.36, 1) ${delay + i * stagger}s both`
        }
        return (
          <span key={i} style={baseStyle}>
            {unit}
          </span>
        )
      })}
      <style>{`
        @keyframes split-in {
          from {
            opacity: 0;
            transform: translateY(0.3em) rotateX(-30deg);
          }
          to {
            opacity: 1;
            transform: translateY(0) rotateX(0deg);
          }
        }
      `}</style>
    </span>
  )
}
