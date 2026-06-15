'use client'

import { motion, useReducedMotion, type Variants } from 'motion/react'
import { cn } from '@/lib/utils'

interface SplitTextProps {
  text: string
  className?: string
  stagger?: number
  delay?: number
  byWord?: boolean
  trigger?: 'mount' | 'view'
}

/**
 * 文字逐字符/逐词进场动画
 * 用 motion 替代付费 GSAP SplitText
 */
export function SplitText({
  text,
  className,
  stagger = 0.04,
  delay = 0,
  byWord = false,
  trigger = 'mount',
}: SplitTextProps) {
  const reduced = useReducedMotion()
  const units = byWord ? text.split(/(\s+)/) : Array.from(text)
  const initialAnimate = trigger === 'view' ? false : true

  const container: Variants = {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: reduced ? 0 : stagger,
        delayChildren: reduced ? 0 : delay,
      },
    },
  }

  const child: Variants = {
    hidden: { opacity: 0, y: reduced ? 0 : '60%', rotateX: reduced ? 0 : -45 },
    visible: {
      opacity: 1,
      y: 0,
      rotateX: 0,
      transition: {
        duration: reduced ? 0.01 : 0.8,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  }

  return (
    <motion.span
      className={cn('inline', className)}
      style={{ perspective: 1000, whiteSpace: 'pre' }}
      variants={container}
      initial="hidden"
      {...(initialAnimate
        ? { animate: 'visible' }
        : { whileInView: 'visible', viewport: { once: true, margin: '-10%' } })}
    >
      {units.map((unit, i) => (
        <motion.span
          key={i}
          variants={child}
          className="inline-block"
          style={{
            transformOrigin: '0% 100%',
            willChange: 'transform, opacity',
            marginRight: '0.04em',
          }}
        >
          {unit}
        </motion.span>
      ))}
    </motion.span>
  )
}
