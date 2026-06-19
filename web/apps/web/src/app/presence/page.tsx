'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { ChapterBlock } from '@/components/feature/ChapterBlock'
import { FooterMeta } from '@/components/feature/FooterMeta'
import { TimelineLightUp } from '@/components/feature/TimelineLightUp'
import { Reveal } from '@/components/Reveal'
import {
  STATES,
  STATE_ORDER,
  usePresenceAutoCycle,
  type StateKey,
} from '@/components/three/PresenceOrb'

const PresenceOrbDynamic = dynamic(
  () => import('@/components/three/PresenceOrb').then((m) => m.PresenceOrb),
  { ssr: false },
)

const SIBLINGS = [
  { href: '/presence', label: 'Presence', key: 'presence' as const },
  { href: '/memory', label: 'Memory', key: 'memory' as const },
  { href: '/agent', label: 'Agent', key: 'agent' as const },
  { href: '/tech', label: 'Tech', key: 'tech' as const },
]

const TIMELINE = [
  { time: '07:30', state: 'IDLE' as StateKey, label: '醒来' },
  { time: '08:15', state: 'LISTENING' as StateKey, label: '晨间问候' },
  { time: '09:40', state: 'THINKING' as StateKey, label: '规划一天' },
  { time: '12:00', state: 'IDLE' as StateKey, label: '午间安静' },
  { time: '14:20', state: 'REMEMBERING' as StateKey, label: '回忆旧聊' },
  { time: '17:45', state: 'SPEAKING' as StateKey, label: '帮忙看餐' },
  { time: '21:10', state: 'TIRED' as StateKey, label: '准备休息' },
  { time: '23:30', state: 'SLEEPING' as StateKey, label: '渐入睡眠' },
]

const TIMELINE_POINTS = TIMELINE.map((p) => ({
  time: p.time,
  color: STATES[p.state].color,
  label: STATES[p.state].label,
  note: p.label,
}))

const PRIORITY_RULES = [
  ['错误优先级', '配置未就绪、出错或工具失败时，直接进入 ERROR 接管。'],
  ['事件触发', 'MEMORY_SPARK、SEARCH_SWEEP 等事件会推高对应状态。'],
  ['流式与加载', 'isStreaming、isLoading 和输入状态会切换显示层。'],
  ['情绪映射', 'happy、sad、tired 等情绪会映射到可见状态。'],
]

const REACTIONS = [
  { name: 'ERROR_RECOVER', color: 'var(--aura-alert)', desc: '优先级最高，遇错直接接管。' },
  { name: 'MEMORY_SPARK', color: 'var(--aura-speaking)', desc: '触发记忆回流，补上上下文。' },
  { name: 'SEARCH_SWEEP', color: 'var(--aura-listening)', desc: '工具执行时给出轻量反馈。' },
]

export default function PresencePage() {
  const { stateKey, index } = usePresenceAutoCycle(3200)
  const currentState = STATES[stateKey]

  return (
    <FeatureShell
      number="01"
      category="Capability"
      title="它不是只会在你发消息时出现"
      active="presence"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(124, 92, 255, 0.18), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(92, 167, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      heroStage={
        <HeroStage
          variant="presence"
          three={
            <>
              <PresenceOrbDynamic stateKey={stateKey} />
              <div className="pointer-events-none absolute bottom-6 left-1/2 flex -translate-x-1/2 gap-2">
                {STATE_ORDER.slice(0, 6).map((s, i) => (
                  <span
                    key={s}
                    className="h-1 rounded-full transition-all"
                    style={{
                      width: i === index % 6 ? 24 : 6,
                      backgroundColor:
                        i === index % 6
                          ? STATES[s].color
                          : 'rgba(255,255,255,0.2)',
                    }}
                  />
                ))}
              </div>
            </>
          }
          badge={{
            label: '状态',
            value: currentState.label,
            color: currentState.color,
          }}
          stats={[
            { n: '11', label: '状态', desc: '听、想、说、记、累、恢复等 11 种 PresenceMode' },
            { n: '5', label: '反应', desc: '点击、回忆、检索、错误和环境反馈' },
            { n: '24h', label: '在场', desc: '不是一段回复，而是一整天可感知的状态流' },
          ]}
          caption={`${currentState.description} · Aura 当前如何在场`}
        />
      }
    >
      <ChapterBlock
        number="01"
        eyebrow="Overview"
        title="陪伴运行时，不只是聊天"
        description="Aura 会根据输入、流式回复、工具状态、情绪和关系变化持续调整自己 —— 它是一段持续运行的状态流，而不只是你发消息时弹出的一次回复。"
      />

      <ChapterBlock
        number="02"
        eyebrow="Capability"
        title="11 种状态 · 4 条优先级规则"
        description="PresenceMode 是一组带权重的有限状态机。每种状态都有进入条件、停留时长和退出路径，状态之间由优先级规则推动切换。"
      >
        <div className="grid grid-cols-1 gap-x-12 gap-y-16 md:grid-cols-2">
          <div>
            <h3 className="label-mono mb-6 text-muted">Priority Rules</h3>
            <ol className="space-y-8">
              {PRIORITY_RULES.map(([title, desc], i) => (
                <Reveal key={title} as="li" direction="y" delay={i * 60}>
                  <div className="flex items-baseline gap-4">
                    <span className="font-mono text-xs text-accent">0{i + 1}</span>
                    <h4 className="text-lg font-medium text-foreground">{title}</h4>
                  </div>
                  <p className="mt-2 max-w-md pl-8 text-pretty text-sm leading-relaxed text-muted">
                    {desc}
                  </p>
                </Reveal>
              ))}
            </ol>
          </div>

          <div>
            <h3 className="label-mono mb-6 text-muted">Reaction Set</h3>
            <ul className="space-y-6">
              {REACTIONS.map((reaction, i) => (
                <Reveal key={reaction.name} as="li" delay={i * 60}>
                  <div className="flex items-center justify-between border-b border-border/60 pb-3">
                    <span
                      className="font-mono text-xs uppercase tracking-wider"
                      style={{ color: reaction.color }}
                    >
                      {reaction.name}
                    </span>
                    <span className="label-mono text-muted">quiet</span>
                  </div>
                  <p className="mt-3 text-pretty text-sm leading-relaxed text-muted">
                    {reaction.desc}
                  </p>
                </Reveal>
              ))}
            </ul>
            <p className="mt-10 max-w-md text-pretty text-sm leading-relaxed text-muted">
              PresenceReactionPolicy 会过滤冷却、优先级和任务态，确保不会一有风吹草动就打断你 —— 陪伴是克制的，不是炫技的。
            </p>
          </div>
        </div>
      </ChapterBlock>

      <ChapterBlock
        number="03"
        eyebrow="Day Cycle"
        title="24 小时在场时间线"
        description="一天里 8 个关键时刻 —— 从清晨醒来、午间安静到渐入睡眠，Aura 的状态会跟着你的节奏切换。"
      >
        <TimelineLightUp points={TIMELINE_POINTS} />
        <p className="label-mono mt-12 text-muted">示例 · 模拟数据</p>
      </ChapterBlock>

      <FooterMeta
        number="01"
        category="Capability"
        siblings={SIBLINGS}
        currentKey="presence"
      />
    </FeatureShell>
  )
}
