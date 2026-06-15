'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { TimelineLightUp } from '@/components/feature/TimelineLightUp'
import { Reveal } from '@/components/Reveal'
import {
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
  { name: 'ERROR_RECOVER', priority: 50, cooldown: '0s', duration: '2.6s', desc: '最高优先级 · 无冷却', color: '#ff5c7c' },
  { name: 'MEMORY_SPARK', priority: 40, cooldown: '1.5s', duration: '2.3s', desc: '保存记忆反馈', color: '#ffb85c' },
  { name: 'SEARCH_SWEEP', priority: 30, cooldown: '1.5s', duration: '1.9s', desc: '搜索工具执行中', color: '#5cefff' },
  { name: 'RETURN_BLINK', priority: 20, cooldown: '10 min', duration: '1.6s', desc: '环境态 · 10 分钟锁定', color: '#a07cff' },
  { name: 'TOUCH_NUZZLE', priority: 10, cooldown: '0.9s', duration: '1.2s', desc: '环境态 · 点击反应', color: '#5cffb0' },
]

// 24h 状态时间线（演示数据）
const TIMELINE = [
  { time: '07:30', state: 'IDLE' as StateKey, label: '唤醒' },
  { time: '08:15', state: 'LISTENING' as StateKey, label: '早间问候' },
  { time: '09:40', state: 'THINKING' as StateKey, label: '规划一天' },
  { time: '12:00', state: 'IDLE' as StateKey, label: '午间' },
  { time: '14:20', state: 'REMEMBERING' as StateKey, label: '回忆旧聊' },
  { time: '17:45', state: 'SPEAKING' as StateKey, label: '帮忙看食谱' },
  { time: '21:10', state: 'TIRED' as StateKey, label: '准备休息' },
  { time: '23:30', state: 'SLEEPING' as StateKey, label: '渐渐入睡' },
]

// 喂给 TimelineLightUp 的扁平化数据
const TIMELINE_POINTS = TIMELINE.map((p) => ({
  time: p.time,
  color: STATES[p.state].color,
  label: STATES[p.state].label,
  note: p.label,
}))

export default function PresencePage() {
  const { stateKey, index } = usePresenceAutoCycle(3200)
  const currentState = STATES[stateKey]

  return (
    <FeatureShell
      number="01"
      category="Capability"
      title="始终在线的陪伴感。"
      subtitle="Aura 不只是一个聊天工具，而是一个有「存在感」的陪伴体。它能感知你的设备状态、情绪、时间，适时地响应或沉默——而且不打扰你。"
      active="presence"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(124, 92, 255, 0.18), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(92, 167, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
    >
      {/* ─── 第一屏：3D 沉浸 + 数字速记 ─── */}
      <HeroStage
        variant="presence"
        three={
          <>
            <PresenceOrbDynamic stateKey={stateKey} />

            {/* 状态指示器（6 个点，底部居中） */}
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
          { n: '11', label: '状态', desc: 'PresenceMode 11 种' },
          { n: '5', label: '反应', desc: 'Reaction 5 种节流' },
          { n: '3', label: '规则', desc: 'shouldShow 3 条规则' },
        ]}
        caption={`${currentState.description} · 实时状态`}
      />

      {/* ─── 状态推导 + 节流规则 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
          {/* 左：状态推导 */}
          <div className="md:col-span-7">
            <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
              状态推导 · 5 步
            </h2>
            <p className="mt-4 text-pretty leading-relaxed text-muted">
              <code className="font-mono text-xs">PresenceController.derive</code>{' '}
              从 14 个输入信号推导 11 个状态，按优先级覆盖。
            </p>

            <ol className="mt-8 space-y-4">
              {[
                {
                  n: '01',
                  t: '错误态优先',
                  d: '配置未就绪 / 出错 / 工具失败 → 直接 ERROR，跳过其他推导。',
                },
                {
                  n: '02',
                  t: '反应事件触发',
                  d: 'MEMORY_SPARK 反应 → REMEMBERING；memory 工具 STARTED → SEARCHING。',
                },
                {
                  n: '03',
                  t: '工具运行态',
                  d: 'tool STARTED → THINKING；tool SUCCEEDED on memory → REMEMBERING。',
                },
                {
                  n: '04',
                  t: '流式 / 加载 / 输入',
                  d: 'isStreaming → SPEAKING；isLoading → THINKING；有文本/图片 → LISTENING。',
                },
                {
                  n: '05',
                  t: '情绪映射 · 兜底 IDLE',
                  d: 'happy/joy/... → HAPPY；sad/... → SAD；tired/... → TIRED；其他 → IDLE。',
                },
              ].map((step, i) => (
                <Reveal
                  key={step.n}
                  as="li"
                  direction="x"
                  delay={i * 60}
                  className="flex gap-4 rounded-xl border border-border p-5"
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
                </Reveal>
              ))}
            </ol>
          </div>

          {/* 右：节流规则 */}
          <div className="md:col-span-5">
            <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
              反应节流 · 3 规则
            </h2>
            <p className="mt-4 text-pretty leading-relaxed text-muted">
              <code className="font-mono text-xs">PresenceReactionPolicy.shouldShow</code>{' '}
              用 3 条规则决定该呈现什么反应。
            </p>

            <ol className="mt-8 space-y-4">
              {[
                {
                  n: '①',
                  t: '按冷却时间过滤',
                  d: '每个 reaction 有自己的冷却（0 / 1.5s / 1.5s / 10min / 0.9s）。没过冷却直接丢弃。',
                },
                {
                  n: '②',
                  t: '解决优先级冲突',
                  d: '当前 reaction 的 priority（10 / 20 / 30 / 40 / 50）更高则保留，新候选被丢弃。',
                },
                {
                  n: '③',
                  t: '区分任务态 / 环境态',
                  d: '环境态（RETURN_BLINK / TOUCH_NUZZLE）只在非任务态展示，避免打断 LLM 思考/说话/记忆/搜索/错误。',
                },
              ].map((step, i) => (
                <Reveal
                  key={step.n}
                  as="li"
                  direction="x"
                  delay={i * 60}
                  className="flex gap-4 rounded-xl border border-border p-5"
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
                </Reveal>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* ─── 24h 状态时间线 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            24 小时状态时间线
          </h2>
          <span className="font-mono text-xs text-muted">示例 · 模拟数据</span>
        </div>

        <div className="mt-12">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-12">
            {/* 时间刻度 */}
            <div className="md:col-span-2">
              <p className="font-mono text-xs uppercase tracking-wider text-muted">
                时间线
              </p>
              <p className="mt-2 text-sm text-muted">
                一天 8 个关键时刻的状态切换
              </p>
            </div>

            {/* 时间轴 — GSAP ScrollTrigger 按时间顺序点亮 */}
            <div className="md:col-span-10">
              <TimelineLightUp points={TIMELINE_POINTS} />
            </div>
          </div>
        </div>
      </section>

      {/* ─── Reaction 节流策略表 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            反应节流策略
          </h2>
          <span className="font-mono text-xs text-muted">
            反应策略 · 3 规则 + 5 状态条件
          </span>
        </div>

        <div className="mt-8 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs font-mono uppercase tracking-wider text-muted">
                <th className="py-3 pr-6">反应</th>
                <th className="py-3 pr-6">优先级</th>
                <th className="py-3 pr-6">冷却时长</th>
                <th className="py-3 pr-6">持续时长</th>
                <th className="py-3">触发条件</th>
              </tr>
            </thead>
            <tbody>
              {REACTIONS.map((r) => (
                <tr
                  key={r.name}
                  className="border-b border-border/50"
                >
                  <td className="py-4 pr-6 font-mono text-xs">
                    {r.name}
                  </td>
                  <td className="py-4 pr-6">
                    <div className="flex items-center gap-3">
                      {/* 强度条 — 宽度按 priority 比例（50 = 100%，10 = 20%） */}
                      <div className="relative h-1.5 w-20 overflow-hidden rounded-full bg-subtle/60">
                        <span
                          className="absolute inset-y-0 left-0 rounded-full"
                          style={{
                            width: `${(r.priority / 50) * 100}%`,
                            backgroundColor: r.color,
                            boxShadow: `0 0 8px ${r.color}60`,
                          }}
                        />
                      </div>
                      <span
                        className="font-mono text-xs font-medium"
                        style={{ color: r.color }}
                      >
                        {r.priority}
                      </span>
                    </div>
                  </td>
                  <td className="py-4 pr-6 font-mono text-xs text-foreground">
                    {r.cooldown}
                  </td>
                  <td className="py-4 pr-6 font-mono text-xs text-foreground">
                    {r.duration}
                  </td>
                  <td className="py-4 text-muted">{r.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* ─── 相关链接 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            相关
          </h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/memory', label: '记忆系统', desc: 'Aura 如何记住你' },
            { href: '/local-llm', label: '本地大模型', desc: '端侧推理' },
            { href: '/', label: '← 返回首页', desc: 'Aura 总览' },
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
