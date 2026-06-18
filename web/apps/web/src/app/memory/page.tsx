'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { ChapterBlock } from '@/components/feature/ChapterBlock'
import { FooterMeta } from '@/components/feature/FooterMeta'
import { Reveal } from '@/components/Reveal'

const MemoryNetworkDynamic = dynamic(
  () => import('@/components/three/MemoryNetwork').then((m) => m.MemoryNetwork),
  { ssr: false },
)

const SIBLINGS = [
  { href: '/presence', label: 'Presence', key: 'presence' as const },
  { href: '/memory', label: 'Memory', key: 'memory' as const },
  { href: '/agent', label: 'Agent', key: 'agent' as const },
  { href: '/tech', label: 'Tech', key: 'tech' as const },
]

const MEMORY_TYPES = [
  {
    name: 'FACT',
    color: 'var(--color-accent)',
    desc: '关于你的稳定信息。',
    examples: ['住在北京', '养了一只猫', '做产品工作'],
  },
  {
    name: 'EPISODE',
    color: 'var(--aura-memory)',
    desc: '有时间线的互动与事件。',
    examples: ['上周聊过露营', '昨天抱怨过加班', '三月看过那部电影'],
  },
  {
    name: 'PROCEDURAL',
    color: 'var(--aura-speaking)',
    desc: '你的习惯、偏好和节奏。',
    examples: ['喜欢先结论后展开', '晚上十点后更想安静', '对猫毛过敏'],
  },
]

const STORAGE_BREAKDOWN = [
  { type: '长期记忆', count: 225, weight: 0.8 },
  { type: '跨会话摘要', count: 60, weight: 0.17 },
  { type: '关系与偏好', count: 16, weight: 0.03 },
]

const STORAGE_COLORS = ['var(--color-accent)', 'var(--aura-listening)', 'var(--aura-speaking)']

const TRUST_BOUNDARIES = [
  ['来源可追溯', '每条长期理解都应回到消息、情绪或视觉内容本身。'],
  ['结论可修正', '系统会继续更新、归并与修正，而不是一句话定终身。'],
  ['用户可控制', '用户可以删除、静音或归档，让边界始终掌握在自己手里。'],
]

export default function MemoryPage() {
  return (
    <FeatureShell
      number="02"
      category="System"
      title="它不是记住一句话，而是在逐渐理解你"
      active="memory"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 255, 176, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 20% 80%, rgba(92, 239, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      heroStage={
        <HeroStage
          variant="memory"
          three={<MemoryNetworkDynamic />}
          stats={[
            { n: '3', label: '记忆类', desc: '事实、事件、偏好三类长期记忆' },
            { n: '5', label: '摘要类', desc: '每日、会话、主题、项目、关系五类摘要' },
            { n: '可信', label: '边界', desc: '来源可追溯、结论可修正、用户可控制' },
          ]}
          caption="记忆、摘要与用户控制，共同构成 Aura 的长期个人模型"
        />
      }
    >
      <ChapterBlock
        number="01"
        eyebrow="Overview"
        title="记住什么，不记住什么"
        description="Aura 不把你压成一句标签，而是把稳定事实、阶段经历和长期偏好分开存放。摘要负责压缩长程上下文，关系与偏好负责细节回流 —— 用户始终可以否决任何一条。"
        width="prose"
      />

      <ChapterBlock
        number="02"
        eyebrow="Memory"
        title="3 类长期信息"
        description="事实回答「你是谁」，事件回答「你们一起经历过什么」，偏好回答「你怎么更舒服」。"
      >
        <div className="space-y-12">
          {MEMORY_TYPES.map((t, i) => (
            <Reveal key={t.name} direction="y" delay={i * 80}>
              <div className="grid grid-cols-1 gap-6 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <div className="flex items-center gap-3">
                    <span
                      className="h-2 w-2 rounded-full"
                      style={{ backgroundColor: t.color }}
                    />
                    <span className="font-mono text-xs uppercase tracking-wider text-foreground">
                      {t.name}
                    </span>
                  </div>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-base leading-relaxed text-foreground">
                    {t.desc}
                  </p>
                  <ul className="mt-4 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted">
                    {t.examples.map((ex) => (
                      <li key={ex} className="font-mono text-xs">
                        {ex}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <ChapterBlock
        number="03"
        eyebrow="Storage"
        title="怎么长大"
        description="记忆负责长期理解，摘要负责压缩，关系与偏好负责细节回流。本地 SQLite · 281 条演示数据。"
      >
        <div className="flex h-3 w-full overflow-hidden rounded-full bg-subtle">
          {STORAGE_BREAKDOWN.map((row, i) => (
            <div
              key={row.type}
              className="h-full"
              style={{
                width: `${row.weight * 100}%`,
                backgroundColor: STORAGE_COLORS[i],
              }}
            />
          ))}
        </div>

        <div className="mt-10 grid grid-cols-1 gap-8 sm:grid-cols-3">
          {STORAGE_BREAKDOWN.map((row, i) => (
            <div key={row.type}>
              <div className="flex items-center gap-2">
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: STORAGE_COLORS[i] }}
                />
                <span className="font-mono text-xs text-foreground">{row.type}</span>
              </div>
              <p className="mt-4 text-3xl font-medium tracking-tight">
                {row.count}
              </p>
              <p className="mt-1 font-mono text-xs text-muted">
                {(row.weight * 100).toFixed(0)}% of total
              </p>
            </div>
          ))}
        </div>
      </ChapterBlock>

      <ChapterBlock
        number="04"
        eyebrow="Trust"
        title="为什么值得信任"
        description="长期记忆最危险的事是把偶然聊天当成判断。Aura 用三条约束守住边界。"
      >
        <div className="space-y-10">
          {TRUST_BOUNDARIES.map(([title, desc], i) => (
            <Reveal key={title} direction="y" delay={i * 80}>
              <div className="grid grid-cols-1 gap-4 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="label-mono text-muted">
                    0{i + 1} · Boundary
                  </span>
                </div>
                <div className="md:col-span-9">
                  <h3 className="text-xl font-medium text-foreground">{title}</h3>
                  <p className="mt-3 max-w-prose text-pretty leading-relaxed text-muted">
                    {desc}
                  </p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <FooterMeta
        number="02"
        category="System"
        siblings={SIBLINGS}
        currentKey="memory"
      />
    </FeatureShell>
  )
}
