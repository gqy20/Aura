'use client'

import { motion, useScroll, useTransform, useReducedMotion } from 'motion/react'
import { useRef } from 'react'

interface ScrollSectionProps {
  number: string
  title: string
  description: string
  /** 视差方向 */
  direction?: 'up' | 'down'
}

/**
 * 通用滚动叙事段落
 * - 标题/数字入场：上滑 + 渐入
 * - 视差：description 略慢于 scroll，制造层次
 */
export function ScrollSection({
  number,
  title,
  description,
  direction = 'up',
}: ScrollSectionProps) {
  const ref = useRef<HTMLDivElement>(null)
  const reduced = useReducedMotion()
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ['start end', 'end start'],
  })

  const yParallax = useTransform(
    scrollYProgress,
    [0, 1],
    direction === 'up' ? [60, -60] : [-40, 40],
  )
  const opacity = useTransform(scrollYProgress, [0, 0.3, 0.7, 1], [0, 1, 1, 0])

  return (
    <section
      ref={ref}
      className="relative flex min-h-[80vh] items-center py-32"
    >
      <div className="mx-auto w-full max-w-6xl px-8 sm:px-12">
        <motion.div
          style={{
            y: reduced ? 0 : yParallax,
            opacity: reduced ? 1 : opacity,
          }}
          className="grid grid-cols-1 items-center gap-8 md:grid-cols-12"
        >
          {/* 序号 */}
          <div className="md:col-span-2">
            <span className="font-mono text-sm text-muted">{number}</span>
          </div>

          {/* 标题 + 描述 */}
          <div className="md:col-span-10">
            <h2 className="text-balance text-4xl font-medium leading-[1.1] tracking-tight sm:text-5xl md:text-6xl">
              {title}
            </h2>
            <p className="mt-6 max-w-2xl text-pretty text-lg leading-relaxed text-muted sm:text-xl">
              {description}
            </p>
          </div>
        </motion.div>
      </div>
    </section>
  )
}
