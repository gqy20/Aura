'use client'

import type { ReactNode } from 'react'
import { Reveal } from '@/components/Reveal'

interface SceneSectionProps {
  /** moment 序号，"01" */
  index: string
  /** 分类小标，"陪伴 · 情绪感知" */
  eyebrow: string
  title: string
  description: string
  abilityTags: string[]
  /** 右栏手机可视化 */
  phone: ReactNode
  /** 场景强调色（--aura-*），驱动背景光晕与 eyebrow，chips 保持中性 */
  accent?: string
}

/**
 * 整屏 snap 场景屏：左文案右手机。
 * 与主页 ScrollSection / ScreenSection 同一套一屏一节奏语言，
 * 区别是右栏从「抽象标题」换成「手机里的真实一刻」。
 */
export function SceneSection({
  index,
  eyebrow,
  title,
  description,
  abilityTags,
  phone,
  accent = 'var(--color-accent)',
}: SceneSectionProps) {
  return (
    <section className="relative flex h-[100svh] snap-start snap-always items-center overflow-hidden px-6 py-20 sm:px-10 lg:px-16">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background: `radial-gradient(ellipse 55% 50% at 78% 50%, color-mix(in srgb, ${accent} 16%, transparent), transparent 60%)`,
        }}
      />

      <div className="mx-auto grid w-full max-w-7xl grid-cols-1 items-center gap-12 md:grid-cols-12 md:gap-16">
        <Reveal direction="y" className="md:col-span-5">
          <span className="label-mono text-xs text-muted">moment {index}</span>
          <p className="label-mono mt-6 text-[0.7rem]" style={{ color: accent }}>
            {eyebrow}
          </p>
          <h2 className="mt-4 max-w-md text-balance text-3xl font-medium leading-display tracking-tight sm:text-4xl md:text-[2.6rem]">
            {title}
          </h2>
          <p className="mt-5 max-w-md text-pretty text-sm leading-relaxed text-muted sm:text-base">
            {description}
          </p>
          <ul className="mt-7 flex flex-wrap gap-2">
            {abilityTags.map((t) => (
              <li
                key={t}
                className="rounded-full border border-border bg-subtle/40 px-3 py-1 font-mono text-[11px] text-foreground"
              >
                {t}
              </li>
            ))}
          </ul>
        </Reveal>

        <Reveal direction="y" delay={120} className="flex md:col-span-7 md:justify-center">
          {phone}
        </Reveal>
      </div>
    </section>
  )
}
