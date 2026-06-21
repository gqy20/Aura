'use client'

import React, { useEffect, useRef, useState, type ReactNode } from 'react'

interface StaggerInProps {
  /** 子元素之间的交错间隔（ms） */
  stagger?: number
  /** 单个元素过渡时长（ms） */
  duration?: number
  /** 初始偏移距离（px） */
  distance?: number
  /** 基础延迟（ms），在 IO 触发后额外等待 */
  baseDelay?: number
  className?: string
  children: ReactNode
}

/**
 * 子元素交错入场组件。
 *
 * 参照 Reveal 的 SSR 安全模式：默认全部可见，
 * 仅在元素进入视口后才触发交错淡入动画。
 * 适用于手机内容等需要「逐层展开」叙事节奏的场景。
 *
 * 用法：
 *   <StaggerIn stagger={160}>
 *     <div>第一拍</div>
 *     <div>第二拍</div>
 *     <div>第三拍</div>
 *   </StaggerIn>
 */
export function StaggerIn({
  stagger = 140,
  duration = 500,
  distance = 16,
  baseDelay = 0,
  className,
  children,
}: StaggerInProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const el = ref.current
    if (!el) return

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    const rect = el.getBoundingClientRect()
    if (rect.top < window.innerHeight && rect.bottom > 0) return

    setVisible(false)

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          window.setTimeout(() => setVisible(true), baseDelay)
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -8% 0px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [baseDelay])

  return (
    <div ref={ref} className={className}>
      {React.Children.map(children, (child, i) => (
        <div
          style={{
            opacity: visible ? 1 : 0,
            transform: visible ? 'translateY(0)' : `translateY(${distance}px)`,
            transition: `opacity ${duration}ms cubic-bezier(0.22, 1, 0.36, 1), transform ${duration}ms cubic-bezier(0.22, 1, 0.36, 1)`,
            transitionDelay: visible ? `${i * stagger}ms` : '0ms',
            willChange: visible ? 'auto' : 'opacity, transform',
          }}
        >
          {child}
        </div>
      ))}
    </div>
  )
}
