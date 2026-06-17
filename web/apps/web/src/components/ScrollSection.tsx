'use client'

import { motion, useScroll, useTransform, useReducedMotion } from 'motion/react'
import { useRef } from 'react'
import { Reveal } from '@/components/Reveal'

interface ScrollSectionProps {
  number: string
  title: string
  description: string
}

/**
 * 滚动叙事段落
 * - Reveal 替代 motion whileInView 做入场动画（默认可见 + 进入视口时跑）
 * - 内层 motion.div 保留 useScroll + useTransform 做视差（MotionValue 必须用 motion 组件）
 */
export function ScrollSection({ number, title, description }: ScrollSectionProps) {
  const ref = useRef<HTMLDivElement>(null)
  const reduced = useReducedMotion()
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ['start end', 'end start'],
  })
  const yParallax = useTransform(scrollYProgress, [0, 1], [40, -40])

  return (
    <section
      ref={ref}
      data-snap
      className="relative flex h-[100svh] items-center overflow-hidden px-6 py-20 sm:px-10 lg:px-16"
    >
      <Reveal duration={800} className="mx-auto w-full max-w-[1280px]">
        <motion.div
          style={{ y: reduced ? 0 : yParallax }}
          className="grid grid-cols-1 items-end gap-10 md:grid-cols-12 md:gap-16"
        >
          <div className="md:col-span-3">
            <span className="font-mono text-xs uppercase tracking-[0.18em] text-muted">
              chapter {number}
            </span>
          </div>
          <div className="md:col-span-9">
            <h2 className="max-w-4xl text-balance text-4xl font-medium leading-[1.06] tracking-tight sm:text-5xl md:text-6xl">
              {title}
            </h2>
            <p className="mt-5 max-w-xl text-pretty text-base leading-relaxed text-muted sm:text-lg">
              {description}
            </p>
          </div>
        </motion.div>
      </Reveal>
    </section>
  )
}
