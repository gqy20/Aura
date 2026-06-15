'use client'

import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'

const STORAGE_KEY = 'aura:announcement-dismissed:v0.4'

/**
 * Vercel 风 announcement bar
 * - 顶部细条，强调最新发布
 * - X 按钮可关闭，localStorage 记忆
 * - 桌面 + 移动端统一显示
 *
 * 配置：当前公告（Aura v0.4）
 */
export function AnnouncementBar() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    // SSR 安全 + 读取用户上次关闭状态
    if (typeof window === 'undefined') return
    const dismissed = window.localStorage.getItem(STORAGE_KEY)
    if (!dismissed) setVisible(true)
  }, [])

  const dismiss = () => {
    setVisible(false)
    try {
      window.localStorage.setItem(STORAGE_KEY, '1')
    } catch {
      // localStorage 满了 / 禁用，静默失败
    }
  }

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ y: -36, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -36, opacity: 0 }}
          transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
          className="relative z-50 flex h-9 items-center justify-center gap-3 border-b border-border bg-subtle/40 px-4 text-xs backdrop-blur"
        >
          <span className="inline-flex items-center gap-2 font-mono text-muted">
            <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-accent" />
            <span className="text-foreground">v0.4</span>
            <span className="hidden sm:inline">·</span>
            <span className="hidden sm:inline">372 项测试通过 · 双模态存在感</span>
          </span>
          <a
            href="https://github.com/gqy20/Aura/releases"
            target="_blank"
            rel="noopener"
            className="group inline-flex items-center gap-1 text-foreground transition-colors hover:text-accent"
          >
            查看更新日志
            <span className="transition-transform group-hover:translate-x-0.5">
              →
            </span>
          </a>
          <button
            onClick={dismiss}
            aria-label="关闭公告"
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted transition-colors hover:text-foreground"
          >
            <svg
              className="h-3.5 w-3.5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
