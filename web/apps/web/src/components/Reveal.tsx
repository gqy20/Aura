'use client'

import { useEffect, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react'

interface RevealProps {
  children: ReactNode
  delay?: number
  direction?: 'y' | 'x'
  distance?: number
  duration?: number
  rootMargin?: string
  className?: string
  style?: CSSProperties
  as?: 'div' | 'section' | 'li' | 'tr'
}

/**
 * 替代 motion `whileInView` 的 Reveal 组件
 *
 * 设计目标：
 * - SSR / 禁用 JS / 爬虫 / Playwright fullPage 截图：默认渲染显示（friendly fallback）
 * - 首屏元素（mount 时已在视口内）：不跑动画，直接显示
 * - 视口外元素：进入视口时跑 fade-up 动画；不进入视口时也保持可见
 * - prefers-reduced-motion：跳过动画
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
  ref: RefObject<HTMLElement | null>
}

function useReveal(ref: RefObject<HTMLElement | null>, options: Pick<RevealProps, 'delay' | 'rootMargin'>) {
  const delay = options.delay ?? 0
  const rootMargin = options.rootMargin ?? '0px 0px -10% 0px'
  const [shown, setShown] = useState(true)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const el = ref.current
    if (!el) return

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return

    const rect = el.getBoundingClientRect()
    const inViewportNow = rect.top < window.innerHeight && rect.bottom > 0
    if (inViewportNow) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          window.setTimeout(() => {
            setShown(false)
            requestAnimationFrame(() => setShown(true))
          }, delay)
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

function RevealDiv(props: Omit<RevealInnerProps, 'ref'>) {
  const { children, delay, direction, distance, duration, rootMargin, className, style } = props
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

function RevealSection(props: Omit<RevealInnerProps, 'ref'>) {
  const { children, delay, direction, distance, duration, rootMargin, className, style } = props
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

function RevealLi(props: Omit<RevealInnerProps, 'ref'>) {
  const { children, delay, direction, distance, duration, rootMargin, className, style } = props
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

function RevealTr(props: Omit<RevealInnerProps, 'ref'>) {
  const { children, delay, direction, distance, duration, rootMargin, className, style } = props
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
