'use client'

import Link from 'next/link'

interface FooterMetaProps {
  /** 当前章节号 — "01" */
  number: string
  /** 当前章节分类 — "Capability" */
  category: string
  /** 兄弟页面链接 — 与 FeatureNav 顺序一致 */
  siblings: Array<{ href: string; label: string; key: string }>
  /** 当前页 key（用于跳过自身链接） */
  currentKey?: string
}

const FOOTER_RULE_DESCRIPTION =
  '一套持续运行的产品，而不是聊天框里的一次回复。'

/**
 * Footer 压扁：从 1 整屏"相关链接"卡片墙 → 1 行 mono + inline 链接
 *
 * 设计参考：
 * - Linear Method footer：极简 mono + 当前章节号
 * - Anthropic：metadata 单行
 */
export function FooterMeta({
  number,
  category,
  siblings,
  currentKey,
}: FooterMetaProps) {
  return (
    <footer className="flex min-h-[100svh] snap-start snap-always flex-col justify-end border-t border-border px-6 pt-16 pb-12 sm:px-10 sm:pt-20 sm:pb-16 lg:px-16">
      <div className="mx-auto w-full max-w-7xl">
        <div className="label-mono flex items-center gap-4 text-muted">
          <span className="text-accent">{number}</span>
          <span className="h-px w-8 bg-border-strong" aria-hidden />
          <span>{category}</span>
          <span className="text-foreground/40">·</span>
          <span>{FOOTER_RULE_DESCRIPTION}</span>
        </div>

        <p className="mt-10 max-w-2xl text-balance text-2xl font-medium leading-snug tracking-tight text-foreground sm:text-3xl">
          {`Aura 不会把所有能力塞进一次回复 — 它让记忆、洞察与生活建议在手机里持续发生。`}
        </p>

        <div className="mt-16 flex flex-col gap-6 border-t border-border pt-8 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
            {siblings.map((s) => {
              const isCurrent = s.key === currentKey
              return (
                <Link
                  key={s.key}
                  href={s.href}
                  className={
                    isCurrent
                      ? 'text-foreground'
                      : 'text-muted transition-colors hover:text-foreground'
                  }
                >
                  {s.label}
                  {isCurrent && <span className="ml-1 text-accent">·</span>}
                </Link>
              )
            })}
          </div>
          <div className="font-mono text-xs text-muted">
            © 2026 Aura · 开源
          </div>
        </div>
      </div>
    </footer>
  )
}
