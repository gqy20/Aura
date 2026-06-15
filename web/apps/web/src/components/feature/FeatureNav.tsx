'use client'

import Link from 'next/link'
import { motion } from 'motion/react'
import { cn } from '@/lib/utils'
import { AuraLogo } from '@/components/AuraLogo'

interface FeatureNavProps {
  /** 当前页路径，用于高亮 */
  active?: 'home' | 'presence' | 'memory' | 'agent'
}

const NAV_LINKS = [
  { href: '/presence', label: 'Presence', key: 'presence' as const },
  { href: '/memory', label: 'Memory', key: 'memory' as const },
  { href: '/agent', label: 'Agent', key: 'agent' as const },
]

/**
 * 特性页共享 nav
 *
 * - 桌面端：横向链接
 * - 移动端：保持横向 + 缩小字号
 * - 当前页有底部细线 + accent 色
 */
export function FeatureNav({ active = 'home' }: FeatureNavProps) {
  return (
    <motion.nav
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      className="flex h-20 items-center justify-between"
    >
      <Link href="/" className="flex items-center gap-2" aria-label="Aura home">
        <AuraLogo size={28} />
        <span className="font-mono text-sm font-medium tracking-tight">
          aura<span className="text-accent">.</span>
        </span>
      </Link>

      <div className="flex items-center gap-6 text-sm sm:gap-8">
        {NAV_LINKS.map((link) => {
          const isActive = active === link.key
          return (
            <Link
              key={link.key}
              href={link.href}
              className={cn(
                'group relative transition-colors',
                isActive ? 'text-foreground' : 'text-muted hover:text-foreground',
              )}
            >
              {link.label}
              {isActive && (
                <motion.span
                  layoutId="nav-active"
                  className="absolute -bottom-1 left-0 h-px w-full bg-accent"
                  transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                />
              )}
            </Link>
          )
        })}
        <Link
          href="https://github.com"
          className="text-muted transition-colors hover:text-foreground"
        >
          GitHub ↗
        </Link>
      </div>
    </motion.nav>
  )
}
