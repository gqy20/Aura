'use client'

import { motion, useMotionValue, useSpring, useReducedMotion } from 'motion/react'
import { useEffect, useState } from 'react'

/**
 * 磁性光标
 * - 小圆点：精确跟随
 * - 外环：spring 弹性跟随
 * - hover 链接/按钮：外环放大 + 变色
 * - 桌面端启用，移动端 / 触摸设备 / 减少动效偏好禁用
 * - 隐藏原生光标
 */
export function MagneticCursor() {
  const reduced = useReducedMotion()
  const [enabled, setEnabled] = useState(false)
  const [hovering, setHovering] = useState(false)

  const dotX = useMotionValue(-100)
  const dotY = useMotionValue(-100)
  const ringX = useSpring(-100, { stiffness: 200, damping: 22, mass: 0.6 })
  const ringY = useSpring(-100, { stiffness: 200, damping: 22, mass: 0.6 })

  useEffect(() => {
    // 桌面端 + 非减少动效
    const isCoarse = window.matchMedia('(pointer: coarse)').matches
    if (isCoarse || reduced) return
    setEnabled(true)

    // 隐藏原生光标
    document.documentElement.classList.add('cursor-hidden')

    const onMove = (e: MouseEvent) => {
      dotX.set(e.clientX)
      dotY.set(e.clientY)
      ringX.set(e.clientX)
      ringY.set(e.clientY)
    }

    const onOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement
      const isInteractive = target.closest('a, button, [data-cursor="hover"]')
      setHovering(!!isInteractive)
    }

    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseover', onOver)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseover', onOver)
      document.documentElement.classList.remove('cursor-hidden')
    }
  }, [dotX, dotY, ringX, ringY, reduced])

  if (!enabled) return null

  return (
    <>
      {/* 圆点 — 精确跟随 */}
      <motion.div
        aria-hidden
        className="pointer-events-none fixed left-0 top-0 z-[9999] hidden -translate-x-1/2 -translate-y-1/2 md:block"
        style={{ x: dotX, y: dotY }}
      >
        <div
          className="h-1.5 w-1.5 rounded-full bg-white mix-blend-difference"
          style={{ willChange: 'transform' }}
        />
      </motion.div>

      {/* 圆环 — spring 弹性跟随 */}
      <motion.div
        aria-hidden
        className="pointer-events-none fixed left-0 top-0 z-[9998] hidden -translate-x-1/2 -translate-y-1/2 md:block"
        style={{ x: ringX, y: ringY }}
        animate={{
          scale: hovering ? 2.4 : 1,
          opacity: hovering ? 0.9 : 0.5,
        }}
        transition={{ type: 'spring', stiffness: 220, damping: 20 }}
      >
        <div
          className="h-8 w-8 rounded-full border"
          style={{
            borderColor: hovering ? 'rgba(124, 92, 255, 0.8)' : 'rgba(255,255,255,0.4)',
            backgroundColor: hovering ? 'rgba(124, 92, 255, 0.1)' : 'transparent',
            willChange: 'transform, width, height',
            transition: 'border-color 200ms, background-color 200ms',
          }}
        />
      </motion.div>
    </>
  )
}
