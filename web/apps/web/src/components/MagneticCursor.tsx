'use client'

import { useEffect, useRef, useState } from 'react'

/**
 * 磁性光标
 *
 * - 原生光标不隐藏（GPU 硬件加速，延迟为 0）
 * - 圆环：rAF + lerp 平滑跟随，作为纯视觉增强层
 * - hover 链接/按钮：圆环放大 + 变 accent 色
 * - 桌面端启用，移动端 / 触摸设备 / 减少动效偏好禁用
 */
export function MagneticCursor() {
  const ringRef = useRef<HTMLDivElement>(null)
  const [enabled, setEnabled] = useState(false)

  useEffect(() => {
    // 桌面端 + 非减少动效
    const isCoarse = window.matchMedia('(pointer: coarse)').matches
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (isCoarse || reduced) return
    setEnabled(true)

    // 不隐藏原生光标 — 原生光标 GPU 硬件加速，延迟为 0，远优于 JS 驱动

    let mouseX = -100
    let mouseY = -100
    let ringX = -100
    let ringY = -100
    let hovering = false
    let rafId = 0

    const onMove = (e: PointerEvent) => {
      const events = e.getCoalescedEvents()
      const last = events.length > 0 ? events[events.length - 1] : e
      mouseX = last.clientX
      mouseY = last.clientY
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
      ringX += (mouseX - ringX) * 0.85
      ringY += (mouseY - ringY) * 0.85
      if (ringRef.current) {
        ringRef.current.style.transform = `translate3d(${ringX}px, ${ringY}px, 0) translate(-50%, -50%) scale(${hovering ? 2.4 : 1})`
      }
      rafId = requestAnimationFrame(tick)
    }
    rafId = requestAnimationFrame(tick)

    window.addEventListener('pointermove', onMove, { passive: true })
    window.addEventListener('mouseover', onOver, { passive: true })

    return () => {
      cancelAnimationFrame(rafId)
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('mouseover', onOver)
    }
  }, [])

  if (!enabled) return null

  return (
    <>
      {/* 圆环 — 纯视觉增强层，rAF lerp 跟随 */}
      <div
        ref={ringRef}
        aria-hidden
        className="magnetic-ring pointer-events-none fixed left-0 top-0 z-[9998] hidden md:block"
      />
    </>
  )
}
