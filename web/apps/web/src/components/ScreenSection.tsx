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
        'min-h-[calc(100svh-var(--shell-top-h,0px))] px-6 py-16 sm:px-10 lg:px-16',
        className,
      )}
    >
      <div
        className={cn(
          'mx-auto flex min-h-[calc(100svh-var(--shell-top-h,0px)-8rem)] w-full max-w-[1280px] flex-col justify-center',
          innerClassName,
        )}
      >
        {children}
      </div>
    </section>
  )
}
