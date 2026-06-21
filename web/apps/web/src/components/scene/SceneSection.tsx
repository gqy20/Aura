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
 *
 * 布局关键：右侧手机（~620px）远高于左侧文字（~370px），
 * 所以用 items-start 顶部对齐 + 左侧 pt 偏移让文字区与手机视觉重心对齐，
 * 而不是 items-center 把文字挤在中间一坨。
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
    <section className="relative flex h-[100svh] snap-start snap-always items-start overflow-hidden px-6 py-20 sm:px-10 lg:px-16">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background: `radial-gradient(ellipse 55% 50% at 78% 50%, color-mix(in srgb, ${accent} 16%, transparent), transparent 60%)`,
        }}
      />

      <div className="mx-auto grid w-full max-w-7xl grid-cols-1 items-start gap-16 md:grid-cols-2 lg:gap-24">
        <Reveal direction="y" className="pt-12 md:pt-16 lg:pt-20">
          <span className="label-mono text-xs text-muted">moment {index}</span>
          <p className="label-mono mt-11 text-[0.72rem]" style={{ color: accent }}>
            {eyebrow}
          </p>
          <h2 className="mt-9 text-balance text-3xl font-medium leading-[1.08] tracking-tight sm:text-4xl md:text-[3.15rem]">
            {title}
          </h2>
          <p className="mt-10 text-pretty text-lg leading-[1.7] text-muted">
            {description}
          </p>
          <ul className="mt-12 flex flex-wrap gap-3">
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

        <Reveal direction="y" delay={120} className="flex justify-center">
          {phone}
        </Reveal>
      </div>
    </section>
  )
}
