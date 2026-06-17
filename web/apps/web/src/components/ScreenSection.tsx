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
      className={cn(
        'snap-start snap-always min-h-[100svh] px-6 py-20 sm:px-10 lg:px-16',
        className,
      )}
    >
      <div
        className={cn(
          'mx-auto flex min-h-[calc(100svh-10rem)] w-full max-w-[1280px] flex-col justify-center',
          innerClassName,
        )}
      >
        {children}
      </div>
    </section>
  )
}
