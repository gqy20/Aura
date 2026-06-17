'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { TimelineLightUp } from '@/components/feature/TimelineLightUp'
import { Reveal } from '@/components/Reveal'
import { ScreenSection } from '@/components/ScreenSection'
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

const REACTIONS = [
  { name: 'ERROR_RECOVER', color: '#ff5c7c', desc: '优先级最高，遇错直接接管。' },
  { name: 'MEMORY_SPARK', color: '#ffb85c', desc: '触发记忆回流，补上上下文。' },
  { name: 'SEARCH_SWEEP', color: '#5cefff', desc: '工具执行时给出轻量反馈。' },
]

export default function PresencePage() {
  const { stateKey, index } = usePresenceAutoCycle(3200)
  const currentState = STATES[stateKey]

  return (
    <FeatureShell
      number="01"
      category="Capability"
      title="它不是只会在你发消息时出现"
      subtitle="Aura 的陪伴感来自持续在场、及时回应和克制打扰。"
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

      <ScreenSection innerClassName="max-w-[1280px] justify-center">
        <div className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
          <div className="md:col-span-7">
            <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
              陪伴运行时，不只是聊天
            </h2>
            <p className="mt-4 text-pretty leading-relaxed text-muted">
              Aura 会根据输入、流式回复、工具状态、情绪和关系变化持续调整自己。
            </p>

            <ol className="mt-8 space-y-4">
              {[
                ['错误优先级', '配置未就绪、出错或工具失败时，直接进入 ERROR。'],
                ['事件触发', 'MEMORY_SPARK、SEARCH_SWEEP 等事件会推高对应状态。'],
                ['流式与加载', 'isStreaming、isLoading 和输入状态会切换显示层。'],
                ['情绪映射', 'happy、sad、tired 等情绪会映射到可见状态。'],
              ].map(([t, d], i) => (
                <Reveal
                  key={t}
                  as="li"
                  direction="x"
                  delay={i * 60}
                  className="flex gap-4 rounded-xl border border-border p-5"
                >
                  <span className="shrink-0 font-mono text-xs text-accent">
                    0{i + 1}
                  </span>
                  <div>
                    <h3 className="font-medium text-foreground">{t}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-muted">{d}</p>
                  </div>
                </Reveal>
              ))}
            </ol>
          </div>

          <div className="md:col-span-5">
            <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
              反应为什么克制
            </h2>
            <p className="mt-4 text-pretty leading-relaxed text-muted">
              PresenceReactionPolicy 会过滤冷却、优先级和任务态，确保不会一有风吹草动就打断你。
            </p>

            <div className="mt-8 grid grid-cols-1 gap-3">
              {REACTIONS.map((reaction) => (
                <div key={reaction.name} className="rounded-xl border border-border p-4">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-xs uppercase tracking-wider" style={{ color: reaction.color }}>
                      {reaction.name}
                    </span>
                    <span className="font-mono text-[10px] text-muted">quiet</span>
                  </div>
                  <p className="mt-2 text-sm text-muted">{reaction.desc}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">24 小时在场时间线</h2>
          <span className="font-mono text-xs text-muted">示例 · 模拟数据</span>
        </div>
        <div className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-12">
          <div className="md:col-span-2">
            <p className="font-mono text-xs uppercase tracking-wider text-muted">时间线</p>
            <p className="mt-2 text-sm text-muted">一天里 8 个关键时刻，Aura 如何从倾听、思考到休息。</p>
          </div>
          <div className="md:col-span-10">
            <TimelineLightUp points={TIMELINE_POINTS} />
          </div>
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">相关</h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/memory', label: '记忆系统', desc: 'Aura 如何记住你' },
            { href: '/tech', label: '技术方案', desc: 'Aura 如何跑起来' },
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
