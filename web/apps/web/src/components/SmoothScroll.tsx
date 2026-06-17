'use client'

import { useEffect, useRef } from 'react'
import Lenis from 'lenis'
import Snap from 'lenis/snap'

/**
 * Lenis 全局平滑滚动 Provider + lenis/snap 集成
 *
 * - 桌面端启用
 * - 移动端禁用（保持原生滚动性能）
 * - 减少动效偏好禁用
 * - 扫描 [data-snap] 元素作为 snap 屏，type=mandatory
 *   （每次 wheel 滚动停止后强制对齐到最近的 snap 屏顶端）
 */
export function SmoothScroll({ children }: { children: React.ReactNode }) {
  const lenisRef = useRef<Lenis | null>(null)
  const snapRef = useRef<Snap | null>(null)

  useEffect(() => {
    const isMobile = window.matchMedia('(max-width: 768px)').matches
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (isMobile || prefersReduced) return

    const lenis = new Lenis({
      duration: 1.55,
      lerp: 0.085,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      smoothWheel: true,
      wheelMultiplier: 0.95,
    })
    lenisRef.current = lenis

    const raf = (time: number) => {
      lenis.raf(time)
      requestAnimationFrame(raf)
    }
    requestAnimationFrame(raf)

    // lenis/snap：每次滑动对齐到 [data-snap] 屏
    const snap = new Snap(lenis, {
      type: 'mandatory',
      duration: 1.55,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      debounce: 0,
    })
    const targets = document.querySelectorAll<HTMLElement>('[data-snap]')
    snap.addElements(targets, { align: ['start', 'start'] })
    snapRef.current = snap

    return () => {
      snap.stop()
      snapRef.current = null
      lenis.destroy()
      lenisRef.current = null
    }
  }, [])

  return <>{children}</>
}
