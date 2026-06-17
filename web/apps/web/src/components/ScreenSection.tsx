'use client'

import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface ScreenSectionProps {
  children: ReactNode
  className?: string
  innerClassName?: string
}

export function ScreenSection({
  children,
  className,
  innerClassName,
}: ScreenSectionProps) {
  return (
    <section
      data-snap
      className={cn(
        'h-[100svh] overflow-hidden px-6 py-16 sm:px-10 lg:px-16',
        className,
      )}
    >
      <div
        className={cn(
          'mx-auto flex h-full w-full max-w-[1280px] flex-col justify-center',
          innerClassName,
        )}
      >
        {children}
      </div>
    </section>
  )
}
