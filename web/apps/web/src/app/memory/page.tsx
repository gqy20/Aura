'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
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

// 容量演示
const STORAGE_BREAKDOWN = [
  { type: 'memories · FACT', count: 124, weight: 0.4 },
  { type: 'memories · EPISODE', count: 87, weight: 0.3 },
  { type: 'memories · PROCEDURAL', count: 23, weight: 0.1 },
  { type: 'summaries · DAILY', count: 31, weight: 0.12 },
  { type: 'summaries · SESSION', count: 12, weight: 0.05 },
  { type: 'summaries · TOPIC', count: 4, weight: 0.03 },
]

export default function MemoryPage() {
  return (
    <FeatureShell
      number="02"
      category="System"
      title="Memory that grows with you."
      subtitle="每一次对话都被结构化地保存，不是简单日志，而是可被 LLM 调用的记忆图谱。Aura 记得你今天穿的衬衫，也记得你三年前的梦想。"
      active="memory"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 255, 176, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 20% 80%, rgba(92, 239, 255, 0.10), transparent 60%), #08090a"
    >
      {/* ─── 3D 主体 + Type 分类 ─── */}
      <section className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
        {/* 左：3D Canvas */}
        <div className="relative md:col-span-7">
          <div className="relative aspect-[4/3] w-full overflow-hidden rounded-2xl border border-border bg-subtle/30">
            <MemoryNetworkDynamic />

            {/* 图例 */}
            <div className="absolute left-4 top-4 flex flex-col gap-1.5 font-mono text-[10px]">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-white" />
                <span className="text-muted">hub</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: '#7c5cff' }} />
                <span className="text-muted">memory (3 types)</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: '#5cefff' }} />
                <span className="text-muted">summary (5 types)</span>
              </div>
            </div>
          </div>

          <p className="mt-4 font-mono text-xs text-muted">
            3 MemoryType × 5 SummaryType · Room SQLite + indices on
            type/lastAccessed/pinned/archived
          </p>
        </div>

        {/* 右：MemoryType 详解 */}
        <div className="md:col-span-5">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Three layers of memory
          </h2>
          <p className="mt-4 text-pretty leading-relaxed text-muted">
            Memory 不是黑盒。Room 数据库中每条记忆都有 type / importance /
            confidence / pinned / archived 字段，Agent
            通过结构化检索调用，绝不靠"模糊匹配"。
          </p>

          <div className="mt-8 space-y-4">
            {MEMORY_TYPES.map((t, i) => (
              <Reveal
                key={t.name}
                direction="x"
                delay={i * 100}
                className="rounded-xl border border-border p-5"
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
                <p className="mt-2 text-sm text-muted">{t.desc}</p>
                <ul className="mt-3 space-y-1.5">
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
        </div>
      </section>

      {/* ─── Summary 类型 5 卡片网格 ─── */}
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Summary types
          </h2>
          <span className="font-mono text-xs text-muted">
            memory_summaries · 5 types
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Storage breakdown
          </h2>
          <span className="font-mono text-xs text-muted">
            local SQLite · 281 records · demo
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-12">
          <div className="md:col-span-2">
            <p className="font-mono text-xs uppercase tracking-wider text-muted">
              Distribution
            </p>
            <p className="mt-2 text-sm text-muted">
              FACT 占主体，PROCEDURAL 靠日常积累。Summary 跨层索引，避免重复。
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Every field, intentional
          </h2>
          <span className="font-mono text-xs text-muted">
            MemoryEntity · 15 columns
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            Related
          </h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/presence', label: 'Presence', desc: 'How Aura shows up' },
            { href: '/agent', label: 'Agent', desc: 'How Aura thinks' },
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
