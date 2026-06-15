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
      className="relative flex min-h-[70vh] items-center py-24"
    >
      <Reveal duration={800} className="mx-auto w-full max-w-6xl px-8 sm:px-12">
        <motion.div
          style={{ y: reduced ? 0 : yParallax }}
          className="grid grid-cols-1 items-center gap-8 md:grid-cols-12"
        >
          <div className="md:col-span-2">
            <span className="font-mono text-sm text-muted">{number}</span>
          </div>
          <div className="md:col-span-10">
            <h2 className="text-balance text-4xl font-medium leading-[1.1] tracking-tight sm:text-5xl md:text-6xl">
              {title}
            </h2>
            <p className="mt-6 max-w-2xl text-pretty text-lg leading-relaxed text-muted sm:text-xl">
              {description}
            </p>
          </div>
        </motion.div>
      </Reveal>
    </section>
  )
}
