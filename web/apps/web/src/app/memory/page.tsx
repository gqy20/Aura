'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'
import { ScreenSection } from '@/components/ScreenSection'

const MemoryNetworkDynamic = dynamic(
  () => import('@/components/three/MemoryNetwork').then((m) => m.MemoryNetwork),
  { ssr: false },
)

const MEMORY_TYPES = [
  {
    name: 'FACT',
    color: '#7c5cff',
    desc: '关于你的稳定信息。',
    examples: ['住在北京', '养了一只猫', '做产品工作'],
  },
  {
    name: 'EPISODE',
    color: '#5cffb0',
    desc: '有时间线的互动与事件。',
    examples: ['上周聊过露营', '昨天抱怨过加班', '三月看过那部电影'],
  },
  {
    name: 'PROCEDURAL',
    color: '#ffb85c',
    desc: '你的习惯、偏好和节奏。',
    examples: ['喜欢先结论后展开', '晚上十点后更想安静', '对猫毛过敏'],
  },
]

const STORAGE_BREAKDOWN = [
  { type: '长期记忆', count: 225, weight: 0.8 },
  { type: '跨会话摘要', count: 60, weight: 0.17 },
  { type: '关系与偏好', count: 16, weight: 0.03 },
]

const STORAGE_COLORS = ['#7c5cff', '#5cefff', '#ffb85c']

export default function MemoryPage() {
  return (
    <FeatureShell
      number="02"
      category="System"
      title="它不是记住一句话，而是在逐渐理解你"
      subtitle="Aura 把对话、情绪和视觉线索沉淀成一个可追溯、可修正、可控制的个人模型。"
      active="memory"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 255, 176, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 20% 80%, rgba(92, 239, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
    >
      <HeroStage
        variant="memory"
        three={
          <>
            <MemoryNetworkDynamic />
            <div className="pointer-events-none absolute left-6 top-6 flex flex-col gap-1.5 font-mono text-[10px]">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-white" />
                <span className="text-muted">中心枢纽</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: '#7c5cff' }} />
                <span className="text-muted">记忆 · 3 类</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: '#5cefff' }} />
                <span className="text-muted">摘要 · 5 类</span>
              </div>
            </div>
          </>
        }
        stats={[
          { n: '3', label: '记忆类', desc: '事实、事件、偏好三类长期记忆' },
          { n: '5', label: '摘要类', desc: '每日、会话、主题、项目、关系五类摘要' },
          { n: '可信', label: '边界', desc: '来源可追溯、结论可修正、用户可控制' },
        ]}
        caption="记忆、摘要与用户控制，共同构成 Aura 的长期个人模型"
      />

      <ScreenSection innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">记住什么</h2>
          <span className="font-mono text-xs text-muted">3 类长期信息</span>
        </div>
        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          Aura 不把你压成一句标签，而是把稳定事实、阶段经历和长期偏好分开存放。
        </p>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
          {MEMORY_TYPES.map((t, i) => (
            <Reveal
              key={t.name}
              direction="y"
              delay={i * 100}
              className="rounded-xl border border-border p-6"
            >
              <div className="flex items-center gap-3">
                <span className="h-2 w-2 rounded-full" style={{ backgroundColor: t.color }} />
                <span className="font-mono text-xs uppercase tracking-wider text-foreground">{t.name}</span>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted">{t.desc}</p>
              <ul className="mt-4 space-y-1.5">
                {t.examples.map((ex) => (
                  <li key={ex} className="flex items-baseline gap-2 text-xs text-muted">
                    <span className="text-accent">·</span>
                    <span>{ex}</span>
                  </li>
                ))}
              </ul>
            </Reveal>
          ))}
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">怎么长大</h2>
          <span className="font-mono text-xs text-muted">本地 SQLite · 281 条演示数据</span>
        </div>
        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-12">
          <div className="md:col-span-3">
            <p className="font-mono text-xs uppercase tracking-wider text-muted">分布</p>
            <p className="mt-2 text-sm text-muted">记忆负责长期理解，摘要负责压缩，关系与偏好负责细节回流。</p>
          </div>
          <div className="md:col-span-9">
            <div className="flex h-3 w-full overflow-hidden rounded-full bg-subtle">
              {STORAGE_BREAKDOWN.map((row, i) => (
                <div
                  key={row.type}
                  className="h-full"
                  style={{ width: `${row.weight * 100}%`, backgroundColor: STORAGE_COLORS[i] }}
                />
              ))}
            </div>

            <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
              {STORAGE_BREAKDOWN.map((row, i) => (
                <div key={row.type} className="flex items-center justify-between border-b border-border/50 pb-2">
                  <div className="flex items-center gap-2">
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: STORAGE_COLORS[i] }} />
                    <span className="font-mono text-xs text-foreground">{row.type}</span>
                  </div>
                  <span className="font-mono text-xs text-muted">
                    {row.count} · {(row.weight * 100).toFixed(0)}%
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">为什么值得信任</h2>
          <span className="font-mono text-xs text-muted">3 个约束</span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
          {[
            ['来源可追溯', '每条长期理解都应回到消息、情绪或视觉内容本身。'],
            ['结论可修正', '系统会继续更新、归并与修正，而不是一句话定终身。'],
            ['用户可控制', '用户可以删除、静音或归档，让边界始终掌握在自己手里。'],
          ].map(([title, desc]) => (
            <div key={title} className="rounded-xl border border-border p-6">
              <h3 className="font-medium text-foreground">{title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted">{desc}</p>
            </div>
          ))}
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">相关</h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/presence', label: 'Presence', desc: 'Aura 如何呈现自己' },
            { href: '/agent', label: 'Agent', desc: 'Aura 如何思考与行动' },
            { href: '/', label: '返回首页', desc: 'Aura 总览' },
          ].map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="group rounded-xl border border-border p-6 transition-all hover:border-border-strong hover:bg-subtle/40"
            >
              <p className="font-mono text-xs uppercase tracking-wider text-muted">{link.label}</p>
              <p className="mt-2 text-foreground transition-colors group-hover:text-accent">{link.desc} →</p>
            </a>
          ))}
        </div>
      </ScreenSection>
    </FeatureShell>
  )
}
