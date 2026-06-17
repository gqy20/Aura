'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'

// 3D 客户端渲染
const MemoryNetworkDynamic = dynamic(
  () => import('@/components/three/MemoryNetwork').then((m) => m.MemoryNetwork),
  { ssr: false },
)

const MEMORY_TYPES = [
  {
    name: 'FACT',
    color: '#7c5cff',
    desc: '静态事实 · 关于你的客观信息',
    examples: ['住在北京', '养了一只橘猫', '是产品经理'],
  },
  {
    name: 'EPISODE',
    color: '#5cffb0',
    desc: '事件 · 有时间地点的互动',
    examples: ['上周聊过露营', '昨天抱怨过加班', '三月看过这部电影'],
  },
  {
    name: 'PROCEDURAL',
    color: '#ffb85c',
    desc: '程序性 · 你的偏好和习惯',
    examples: ['喜欢先回答再追问', '晚上十点后会想睡觉', '对猫过敏'],
  },
]

const SUMMARY_TYPES = [
  { name: 'DAILY', color: '#5cefff', desc: '每日摘要 · 当天所有对话' },
  { name: 'SESSION', color: '#7c5cff', desc: '会话摘要 · 单次深度聊天' },
  { name: 'TOPIC', color: '#a07cff', desc: '主题摘要 · 跨会话的话题' },
  { name: 'PROJECT', color: '#ffb85c', desc: '项目摘要 · 长期任务' },
  { name: 'RELATIONSHIP', color: '#ff7c9c', desc: '关系摘要 · 人物脉络' },
]

// 容量演示（281 条 = 3 类记忆 + 5 类摘要，权重归一化到 100%）
const STORAGE_BREAKDOWN = [
  { type: 'memories · FACT', count: 118, weight: 0.42 },
  { type: 'memories · EPISODE', count: 84, weight: 0.3 },
  { type: 'memories · PROCEDURAL', count: 23, weight: 0.08 },
  { type: 'summaries · DAILY', count: 31, weight: 0.11 },
  { type: 'summaries · SESSION', count: 11, weight: 0.04 },
  { type: 'summaries · TOPIC', count: 6, weight: 0.02 },
  { type: 'summaries · PROJECT', count: 6, weight: 0.02 },
  { type: 'summaries · RELATIONSHIP', count: 2, weight: 0.01 },
] // 118+84+23+31+11+6+6+2 = 281

export default function MemoryPage() {
  return (
    <FeatureShell
      number="02"
      category="System"
      title="它不是记住一句话，而是在理解你。"
      subtitle="Aura 不把你变成一份黑盒画像。它把对话、情绪、视觉内容和摘要沉淀成一个可信、可修正、可控制的个人模型。"
      active="memory"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 255, 176, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 20% 80%, rgba(92, 239, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
    >
      {/* ─── 第一屏：3D 沉浸 + 数字速记 ─── */}
      <HeroStage
        variant="memory"
        three={
          <>
            <MemoryNetworkDynamic />
            {/* 图例（已无边框，浮在 3D 左上） */}
            <div className="pointer-events-none absolute left-6 top-6 flex flex-col gap-1.5 font-mono text-[10px]">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-white" />
                <span className="text-muted">中枢</span>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ background: '#7c5cff' }}
                />
                <span className="text-muted">记忆 · 3 类</span>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ background: '#5cefff' }}
                />
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
        caption="记忆、摘要与用户控制一起构成 Aura 的长期个人模型"
      />

      {/* ─── 可信个人模型 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            可信个人模型
          </h2>
          <span className="font-mono text-xs text-muted">
            来源 · 置信度 · 控制
          </span>
        </div>
        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          Aura 的记忆不是“我好像记得你说过什么”，而是把事实、事件、情绪和视觉内容沉淀成可追溯的数据层。
          它会保留线索，也给用户反悔和修正的权利。
        </p>
      </section>

      {/* ─── MemoryType 详解 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            三层记忆
          </h2>
          <span className="font-mono text-xs text-muted">
            MemoryType · 3 类
          </span>
        </div>
        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          每条记忆都不是一段松散文本。Aura 会区分事实、事件和偏好，让后续检索、摘要和主动 Insight 都建立在结构化基础上。
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
                <span
                  className="h-2 w-2 rounded-full"
                  style={{ backgroundColor: t.color }}
                />
                <span className="font-mono text-xs uppercase tracking-wider text-foreground">
                  {t.name}
                </span>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                {t.desc}
              </p>
              <ul className="mt-4 space-y-1.5">
                {t.examples.map((ex) => (
                  <li
                    key={ex}
                    className="flex items-baseline gap-2 text-xs text-muted"
                  >
                    <span className="text-accent">·</span>
                    <span className="italic">「{ex}」</span>
                  </li>
                ))}
              </ul>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ─── Summary 类型 5 卡片网格 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            摘要类型
          </h2>
          <span className="font-mono text-xs text-muted">
            记忆摘要 · 5 类
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {SUMMARY_TYPES.map((s, i) => (
            <Reveal
              key={s.name}
              delay={i * 80}
              className="rounded-xl border border-border p-5"
            >
              <span
                className="block h-1 w-8 rounded-full"
                style={{ backgroundColor: s.color }}
              />
              <p className="mt-3 font-mono text-xs uppercase tracking-wider text-foreground">
                {s.name}
              </p>
              <p className="mt-2 text-sm leading-relaxed text-muted">{s.desc}</p>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ─── 存储容量分布 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            记忆如何逐渐长大
          </h2>
          <span className="font-mono text-xs text-muted">
            本地 SQLite · 281 条记录 · 演示
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-12">
          <div className="md:col-span-2">
            <p className="font-mono text-xs uppercase tracking-wider text-muted">
              分布
            </p>
            <p className="mt-2 text-sm text-muted">
              FACT 是基础，EPISODE 是轨迹，PROCEDURAL 是习惯。Summary 负责跨会话压缩和重新组织理解。
            </p>
          </div>

          <div className="md:col-span-10">
            {/* 堆叠条 */}
            <div className="flex h-3 w-full overflow-hidden rounded-full bg-subtle">
              {STORAGE_BREAKDOWN.map((row, i) => (
                <div
                  key={row.type}
                  className="h-full"
                  style={{
                    width: `${row.weight * 100}%`,
                    backgroundColor: [
                      '#7c5cff',
                      '#5cffb0',
                      '#ffb85c',
                      '#5cefff',
                      '#a07cff',
                      '#ff7c9c',
                      '#9090a8',
                      '#c9a96e',
                    ][i],
                  }}
                />
              ))}
            </div>

            <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {STORAGE_BREAKDOWN.map((row, i) => (
                <div
                  key={row.type}
                  className="flex items-center justify-between border-b border-border/50 pb-2"
                >
                  <div className="flex items-center gap-2">
                    <span
                      className="h-1.5 w-1.5 rounded-full"
                      style={{
                        backgroundColor: [
                          '#7c5cff',
                          '#5cffb0',
                          '#ffb85c',
                          '#5cefff',
                          '#a07cff',
                          '#ff7c9c',
                          '#9090a8',
                          '#c9a96e',
                        ][i],
                      }}
                    />
                    <span className="font-mono text-xs text-foreground">
                      {row.type}
                    </span>
                  </div>
                  <span className="font-mono text-xs text-muted">
                    {row.count} · {(row.weight * 100).toFixed(0)}%
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ─── 字段语义 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            每个字段都在约束它如何理解你
          </h2>
          <span className="font-mono text-xs text-muted">
            MemoryEntity · 9 核心列
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {[
            { name: 'importance', range: '0..1', desc: '存多久都不删' },
            { name: 'confidence', range: '0..1', desc: '提取时的把握' },
            { name: 'pinned', range: 'bool', desc: '永远在 prompt 里' },
            { name: 'archived', range: 'bool', desc: '不展示但保留' },
            { name: 'expiresAt', range: 'Long?', desc: '临时记忆过期' },
            { name: 'sensitivity', range: 'enum', desc: '普通 / 敏感' },
            { name: 'lastAccessed', range: 'Long', desc: 'LRU 衰减依据' },
            { name: 'sourceMessageIds', range: 'JSON', desc: '可追溯来源' },
            { name: 'imageBase64', range: 'String?', desc: 'M4 视觉入记忆' },
          ].map((f) => (
            <div
              key={f.name}
              className="rounded-lg border border-border p-4"
            >
              <div className="flex items-baseline justify-between">
                <span className="font-mono text-xs text-foreground">
                  {f.name}
                </span>
                <span className="font-mono text-[10px] text-muted">
                  {f.range}
                </span>
              </div>
              <p className="mt-2 text-xs text-muted">{f.desc}</p>
            </div>
          ))}
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
            { href: '/presence', label: 'Presence', desc: 'Aura 如何呈现自己' },
            { href: '/agent', label: 'Agent', desc: 'Aura 如何思考' },
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
