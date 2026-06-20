'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface PhoneMockupBadge {
  label: string
  value: string
  color: string
  pulse?: boolean
}

interface PhoneMockupProps {
  children: ReactNode
  badge?: PhoneMockupBadge
  /** 屏幕内氛围光（场景 ③ 的月光等），整段 background 值 */
  screenGlow?: string
  className?: string
}

/**
 * 2D 手机框：圆角机身 + 刘海 + 屏幕插槽 + 左上状态徽标。
 * 场景屏用它承载真实对话 / Insight 卡 / 路线 —— 刻意不引 3D，
 * 因为这些时刻的价值在「可读的文字」，抽象色块球（PhoneOrb）放不下。
 */
export function PhoneMockup({ children, badge, screenGlow, className }: PhoneMockupProps) {
  return (
    <div className={cn('relative mx-auto w-[288px]', className)}>
      <div className="relative rounded-[2.4rem] border border-border-strong bg-[#15151d] p-2.5 shadow-[0_40px_90px_-30px_rgba(0,0,0,0.9)]">
        <div className="relative overflow-hidden rounded-[2rem] bg-[#0c0d13]" style={{ minHeight: 600 }}>
          {/* 刘海 */}
          <div
            aria-hidden
            className="absolute left-1/2 top-2.5 z-30 h-5 w-24 -translate-x-1/2 rounded-full bg-[#0c0d13]"
          />

          {/* 屏幕氛围光 */}
          {screenGlow && (
            <div
              aria-hidden
              className="pointer-events-none absolute inset-0 z-0"
              style={{ background: screenGlow }}
            />
          )}

          {/* 左上状态徽标 */}
          {badge && (
            <div className="absolute left-3.5 top-3.5 z-20 flex items-center gap-1.5 font-mono text-[10px]">
              <span
                className={cn('h-1.5 w-1.5 rounded-full', badge.pulse && 'animate-pulse')}
                style={{ backgroundColor: badge.color }}
              />
              <span className="text-muted">{badge.label}</span>
              <span className="font-medium text-foreground">{badge.value}</span>
            </div>
          )}

          {/* 屏幕内容 */}
          <div className="relative z-10 flex h-full min-h-[600px] flex-col px-4 pb-5 pt-14">
            {children}
          </div>
        </div>
      </div>
    </div>
  )
}
