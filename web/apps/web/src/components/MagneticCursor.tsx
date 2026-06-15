'use client'

import { useEffect, useRef, useState } from 'react'

/**
 * 磁性光标（高性能版）
 *
 * 性能优化：
 * - 圆点：rAF + 直接操作 transform（translate3d），不走 React 渲染
 * - 圆环：rAF + lerp 平滑跟随（更轻量于 motion spring）
 * - hover 状态：仅触发一次 className 切换
 *
 * - hover 链接/按钮：圆环放大 + 变 accent 色
 * - 桌面端启用，移动端 / 触摸设备 / 减少动效偏好禁用
 * - 隐藏原生光标
 */
export function MagneticCursor() {
  const dotRef = useRef<HTMLDivElement>(null)
  const ringRef = useRef<HTMLDivElement>(null)
  const [enabled, setEnabled] = useState(false)

  useEffect(() => {
    // 桌面端 + 非减少动效
    const isCoarse = window.matchMedia('(pointer: coarse)').matches
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (isCoarse || reduced) return
    setEnabled(true)

    // 隐藏原生光标
    document.documentElement.classList.add('cursor-hidden')

    let mouseX = -100
    let mouseY = -100
    let ringX = -100
    let ringY = -100
    let hovering = false
    let rafId = 0

    const onMove = (e: MouseEvent) => {
      mouseX = e.clientX
      mouseY = e.clientY
      // 圆点：1:1 跟手，立即更新（无插值）
      if (dotRef.current) {
        dotRef.current.style.transform = `translate3d(${mouseX}px, ${mouseY}px, 0) translate(-50%, -50%)`
      }
    }

    const onOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      const isInteractive = target.closest('a, button, [data-cursor="hover"]')
      const next = !!isInteractive
      if (next !== hovering) {
        hovering = next
        if (ringRef.current) {
          ringRef.current.classList.toggle('is-hovering', hovering)
        }
      }
    }

    // rAF 循环：圆环 lerp 跟随
    const tick = () => {
      // lerp 系数 0.35（响应快且平滑）
      ringX += (mouseX - ringX) * 0.35
      ringY += (mouseY - ringY) * 0.35
      if (ringRef.current) {
        ringRef.current.style.transform = `translate3d(${ringX}px, ${ringY}px, 0) translate(-50%, -50%) scale(${hovering ? 2.4 : 1})`
      }
      rafId = requestAnimationFrame(tick)
    }
    rafId = requestAnimationFrame(tick)

    window.addEventListener('mousemove', onMove, { passive: true })
    window.addEventListener('mouseover', onOver, { passive: true })

    return () => {
      cancelAnimationFrame(rafId)
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseover', onOver)
      document.documentElement.classList.remove('cursor-hidden')
    }
  }, [])

  if (!enabled) return null

  return (
    <>
      {/* 圆点 — 1:1 跟手 */}
      <div
        ref={dotRef}
        aria-hidden
        className="magnetic-dot pointer-events-none fixed left-0 top-0 z-[9999] hidden md:block"
      />

      {/* 圆环 — rAF lerp 跟随 */}
      <div
        ref={ringRef}
        aria-hidden
        className="magnetic-ring pointer-events-none fixed left-0 top-0 z-[9998] hidden md:block"
      />
    </>
  )
}
