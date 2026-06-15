'use client'

import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'

interface RevealProps {
  children: ReactNode
  /** 延迟毫秒（错落入场用） */
  delay?: number
  /** 位移方向 */
  direction?: 'y' | 'x'
  /** 位移距离 px */
  distance?: number
  /** 动画时长 ms */
  duration?: number
  /** 触发阈值 — 元素露出多少时触发 */
  rootMargin?: string
  className?: string
  style?: CSSProperties
  /** 渲染的 HTML 标签，默认 div */
  as?: 'div' | 'section' | 'li' | 'tr'
}

/**
 * 替代 motion `whileInView` 的 Reveal 组件
 *
 * 设计目标：
 * - SSR / 禁用 JS / 爬虫：默认渲染显示（friendly fallback）
 * - 首屏元素（mount 时已在视口内）：不跑动画，直接显示
 * - 视口外元素：进入视口时跑 fade-up 动画
 * - Playwright fullPage 截图：默认显示，IntersectionObserver 不触发也没事
 *
 * 实现：把 `as` 拆成 4 个具体组件，避免动态 Tag 字符串导致 ref 类型联合不收敛。
 */
export function Reveal(props: RevealProps) {
  const { as = 'div', ...rest } = props
  switch (as) {
    case 'section':
      return <RevealSection {...rest} />
    case 'li':
      return <RevealLi {...rest} />
    case 'tr':
      return <RevealTr {...rest} />
    case 'div':
    default:
      return <RevealDiv {...rest} />
  }
}

interface RevealInnerProps extends Omit<RevealProps, 'as'> {
  ref: React.RefObject<HTMLElement | null>
}

/**
 * Reveal 核心 hook — 给 4 个具名组件复用
 *
 * 行为：
 * 1. SSR + 首屏 = visible（`useState(true)`）
 * 2. mount 后立刻看元素是不是在视口内：
 *    - 在视口内 → 保持 visible，不跑动画
 *    - 在视口外 → 下一帧隐藏（避免一帧闪烁），注册 IO 等进入视口
 * 3. 进入视口后按 delay 触发 setShown(true)，并 disconnect observer
 */
function useReveal(
  ref: React.RefObject<HTMLElement | null>,
  { delay = 0, rootMargin = '0px 0px -10% 0px' }: Pick<RevealProps, 'delay' | 'rootMargin'>,
) {
  const [shown, setShown] = useState(true) // SSR + 初始 = 可见

  useEffect(() => {
    if (typeof window === 'undefined') return
    const el = ref.current
    if (!el) return

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) {
      setShown(true)
      return
    }

    // 检查元素当前是否已在视口内
    const rect = el.getBoundingClientRect()
    const inViewportNow = rect.top < window.innerHeight && rect.bottom > 0
    if (inViewportNow) {
      // 已经在视口内（首屏）→ 不做动画，直接显示
      setShown(true)
      return
    }

    // 在视口外 → 隐藏后观察
    setShown(false)
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          window.setTimeout(() => setShown(true), delay)
          observer.disconnect()
        }
      },
      { rootMargin },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [delay, rootMargin, ref])

  return { shown }
}

function buildBaseStyle(
  shown: boolean,
  direction: 'y' | 'x',
  distance: number,
  duration: number,
  style?: CSSProperties,
): CSSProperties {
  const offsetTransform =
    direction === 'y' ? `translateY(${distance}px)` : `translateX(${distance}px)`
  return {
    opacity: shown ? 1 : 0,
    transform: shown ? 'translate(0, 0)' : offsetTransform,
    transition: `opacity ${duration}ms cubic-bezier(0.22, 1, 0.36, 1), transform ${duration}ms cubic-bezier(0.22, 1, 0.36, 1)`,
    willChange: shown ? 'auto' : 'opacity, transform',
    ...style,
  }
}

function RevealDiv({
  children,
  delay,
  direction,
  distance,
  duration,
  rootMargin,
  className,
  style,
}: Omit<RevealInnerProps, 'ref'>) {
  const ref = useRef<HTMLDivElement>(null)
  const { shown } = useReveal(ref, { delay, rootMargin })
  return (
    <div
      ref={ref}
      className={className}
      style={buildBaseStyle(shown, direction!, distance!, duration!, style)}
    >
      {children}
    </div>
  )
}

function RevealSection({
  children,
  delay,
  direction,
  distance,
  duration,
  rootMargin,
  className,
  style,
}: Omit<RevealInnerProps, 'ref'>) {
  const ref = useRef<HTMLElement>(null)
  const { shown } = useReveal(ref, { delay, rootMargin })
  return (
    <section
      ref={ref}
      className={className}
      style={buildBaseStyle(shown, direction!, distance!, duration!, style)}
    >
      {children}
    </section>
  )
}

function RevealLi({
  children,
  delay,
  direction,
  distance,
  duration,
  rootMargin,
  className,
  style,
}: Omit<RevealInnerProps, 'ref'>) {
  const ref = useRef<HTMLLIElement>(null)
  const { shown } = useReveal(ref, { delay, rootMargin })
  return (
    <li
      ref={ref}
      className={className}
      style={buildBaseStyle(shown, direction!, distance!, duration!, style)}
    >
      {children}
    </li>
  )
}

function RevealTr({
  children,
  delay,
  direction,
  distance,
  duration,
  rootMargin,
  className,
  style,
}: Omit<RevealInnerProps, 'ref'>) {
  const ref = useRef<HTMLTableRowElement>(null)
  const { shown } = useReveal(ref, { delay, rootMargin })
  return (
    <tr
      ref={ref}
      className={className}
      style={buildBaseStyle(shown, direction!, distance!, duration!, style)}
    >
      {children}
    </tr>
  )
}
