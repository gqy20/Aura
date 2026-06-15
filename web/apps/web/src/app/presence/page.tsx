'use client'

import dynamic from 'next/dynamic'
import { motion } from 'motion/react'
import { FeatureShell } from '@/components/feature/FeatureShell'
import {
  PresenceOrb,
  STATES,
  STATE_ORDER,
  usePresenceAutoCycle,
  type StateKey,
} from '@/components/three/PresenceOrb'

// 3D 客户端渲染
const PresenceOrbDynamic = dynamic(
  () => import('@/components/three/PresenceOrb').then((m) => m.PresenceOrb),
  { ssr: false },
)

const REACTIONS = [
  { name: 'ERROR_RECOVER', priority: 50, cooldown: '0s', duration: '2.6s', desc: 'Highest priority · no cooldown' },
  { name: 'MEMORY_SPARK', priority: 40, cooldown: '1.5s', duration: '2.3s', desc: 'Save memory feedback' },
  { name: 'SEARCH_SWEEP', priority: 30, cooldown: '1.5s', duration: '1.9s', desc: 'Search tool running' },
  { name: 'RETURN_BLINK', priority: 20, cooldown: '10 min', duration: '1.6s', desc: 'Ambient · 10 min lock' },
  { name: 'TOUCH_NUZZLE', priority: 10, cooldown: '0.9s', duration: '1.2s', desc: 'Ambient · tap reaction' },
]

// 24h 状态时间线（演示数据）
const TIMELINE = [
  { time: '07:30', state: 'IDLE' as StateKey, label: 'Wake-up' },
  { time: '08:15', state: 'LISTENING' as StateKey, label: 'Morning check-in' },
  { time: '09:40', state: 'THINKING' as StateKey, label: 'Plan day' },
  { time: '12:00', state: 'IDLE' as StateKey, label: 'Noon' },
  { time: '14:20', state: 'REMEMBERING' as StateKey, label: 'Recall old chat' },
  { time: '17:45', state: 'SPEAKING' as StateKey, label: 'Recipe help' },
  { time: '21:10', state: 'TIRED' as StateKey, label: 'Wind down' },
  { time: '23:30', state: 'SLEEPING' as StateKey, label: 'Dim to sleep' },
]

export default function PresencePage() {
  const { stateKey, index } = usePresenceAutoCycle(3200)
  const currentState = STATES[stateKey]

  return (
    <FeatureShell
      number="01"
      category="Capability"
      title="Presence that lives with you."
      subtitle="Aura 不只是一个 chat 工具，而是一个有「存在感」的陪伴体。它能感知你的设备状态、情绪、时间，适时地响应或沉默——而且不打扰你。"
      active="presence"
    >
      {/* ─── 3D 主体 + 状态说明 ─── */}
      <section className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
        {/* 左：3D Canvas */}
        <div className="relative md:col-span-7">
          <div className="relative aspect-[4/3] w-full overflow-hidden rounded-2xl border border-border bg-subtle/30">
            <PresenceOrbDynamic stateKey={stateKey} />

            {/* 当前状态徽标 */}
            <div className="absolute left-4 top-4 flex items-center gap-2 font-mono text-xs">
              <span
                className="h-2 w-2 animate-pulse rounded-full"
                style={{ backgroundColor: currentState.color }}
              />
              <span className="text-muted">STATE</span>
              <span className="font-medium text-foreground">
                {currentState.label}
              </span>
            </div>

            {/* 状态指示器（6 个点） */}
            <div className="absolute bottom-4 left-1/2 flex -translate-x-1/2 gap-2">
              {STATE_ORDER.map((s, i) => (
                <span
                  key={s}
                  className="h-1 rounded-full transition-all"
                  style={{
                    width: i === index ? 24 : 6,
                    backgroundColor:
                      i === index ? STATES[s].color : 'rgba(255,255,255,0.2)',
                  }}
                />
              ))}
            </div>
          </div>

          <p className="mt-4 font-mono text-xs text-muted">
            {currentState.description} ·{' '}
            <span style={{ color: currentState.color }}>live state</span>
          </p>
        </div>

        {/* 右：状态推导规则 */}
        <div className="md:col-span-5">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            How Aura decides what to feel
          </h2>
          <p className="mt-4 text-pretty leading-relaxed text-muted">
            Presence 状态机从一组输入信号（mood · intensity · isStreaming · isLoading ·
            tool status）推导当前模式，然后用 5 条规则决定该呈现什么反应。
          </p>

          <ol className="mt-8 space-y-5">
            {[
              {
                n: '01',
                t: 'Derive mode from inputs',
                d: '从 mood + intensity + streaming/loading 状态推出 PresenceMode（IDLE / LISTENING / THINKING / SPEAKING / REMEMBERING / TIRED …）',
              },
              {
                n: '02',
                t: 'Filter reaction by cooldown',
                d: '每个 reaction 有自己的冷却时间（900ms ~ 10min）。如果距离上次展示没过冷却，禁止再触发。',
              },
              {
                n: '03',
                t: 'Resolve priority conflicts',
                d: '如果当前 reaction 优先级（10 ~ 50）更高，新 reaction 顶替；否则丢弃。',
              },
              {
                n: '04',
                t: 'Respect task vs ambient',
                d: 'Ambient reaction（TOUCH_NUZZLE / RETURN_BLINK）只在非任务态展示，避免在 LLM 思考时打断。',
              },
              {
                n: '05',
                t: 'Render with duration',
                d: '展示时长 1.2s ~ 2.6s，独立于 cooldown 控制，避免视觉堆叠。',
              },
            ].map((step) => (
              <motion.li
                key={step.n}
                initial={{ opacity: 0, x: 20 }}
                whileInView={{ opacity: 1, x: 0 }}
                viewport={{ once: true, margin: '-10%' }}
                transition={{ duration: 0.5, ease: [0.22, 1, 0.36, 1] }}
                className="flex gap-4"
              >
                <span className="shrink-0 font-mono text-xs text-accent">
                  {step.n}
                </span>
                <div>
                  <h3 className="font-medium text-foreground">{step.t}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-muted">
                    {step.d}
                  </p>
                </div>
              </motion.li>
            ))}
          </ol>
        </div>
      </section>

      {/* ─── 24h 状态时间线 ─── */}
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            24h presence timeline
          </h2>
          <span className="font-mono text-xs text-muted">demo · synthetic data</span>
        </div>

        <div className="mt-12">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-12">
            {/* 时间刻度 */}
            <div className="md:col-span-2">
              <p className="font-mono text-xs uppercase tracking-wider text-muted">
                Timeline
              </p>
              <p className="mt-2 text-sm text-muted">
                一天 8 个关键时刻的状态切换
              </p>
            </div>

            {/* 时间轴 */}
            <div className="md:col-span-10">
              <div className="relative">
                {/* 横向轴线 */}
                <div className="absolute left-0 right-0 top-[14px] h-px bg-border" />

                <div className="relative grid grid-cols-8 gap-2">
                  {TIMELINE.map((point, i) => (
                    <motion.div
                      key={point.time}
                      initial={{ opacity: 0, y: 10 }}
                      whileInView={{ opacity: 1, y: 0 }}
                      viewport={{ once: true }}
                      transition={{
                        duration: 0.5,
                        delay: i * 0.06,
                        ease: [0.22, 1, 0.36, 1],
                      }}
                      className="flex flex-col items-start"
                    >
                      <span
                        className="h-3.5 w-3.5 rounded-full"
                        style={{ backgroundColor: STATES[point.state].color }}
                      />
                      <span className="mt-2 font-mono text-[10px] text-muted">
                        {point.time}
                      </span>
                      <span className="mt-1 text-xs font-medium text-foreground">
                        {STATES[point.state].label}
                      </span>
                      <span className="text-[10px] text-muted">
                        {point.label}
                      </span>
                    </motion.div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── Reaction 节流策略表 ─── */}
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Reaction throttling
          </h2>
          <span className="font-mono text-xs text-muted">
            PresenceReactionPolicy · 5 rules
          </span>
        </div>

        <div className="mt-8 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs font-mono uppercase tracking-wider text-muted">
                <th className="py-3 pr-6">Reaction</th>
                <th className="py-3 pr-6">Priority</th>
                <th className="py-3 pr-6">Cooldown</th>
                <th className="py-3 pr-6">Duration</th>
                <th className="py-3">Trigger</th>
              </tr>
            </thead>
            <tbody>
              {REACTIONS.map((r, i) => (
                <motion.tr
                  key={r.name}
                  initial={{ opacity: 0 }}
                  whileInView={{ opacity: 1 }}
                  viewport={{ once: true }}
                  transition={{ duration: 0.4, delay: i * 0.05 }}
                  className="border-b border-border/50"
                >
                  <td className="py-4 pr-6 font-mono text-xs">
                    {r.name}
                  </td>
                  <td className="py-4 pr-6">
                    <span
                      className="inline-block h-1.5 w-12 rounded-full"
                      style={{
                        backgroundColor: `rgba(124,92,255,${0.2 + r.priority / 60})`,
                      }}
                    />
                    <span className="ml-3 font-mono text-xs text-muted">
                      {r.priority}
                    </span>
                  </td>
                  <td className="py-4 pr-6 font-mono text-xs text-foreground">
                    {r.cooldown}
                  </td>
                  <td className="py-4 pr-6 font-mono text-xs text-foreground">
                    {r.duration}
                  </td>
                  <td className="py-4 text-muted">{r.desc}</td>
                </motion.tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* ─── 相关链接 ─── */}
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Related
          </h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/memory', label: 'Memory System', desc: 'How Aura remembers' },
            { href: '/local-llm', label: 'Local LLM', desc: 'On-device inference' },
            { href: '/', label: '← Back to home', desc: 'Aura overview' },
          ].map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="group rounded-xl border border-border p-6 transition-all hover:border-border-strong hover:bg-subtle/40"
            >
              <p className="font-mono text-xs uppercase tracking-wider text-muted">
                {link.label}
              </p>
              <p className="mt-2 text-foreground transition-colors group-hover:text-accent">
                {link.desc} →
              </p>
            </a>
          ))}
        </div>
      </section>
    </FeatureShell>
  )
}
