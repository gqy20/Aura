'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'

// 3D 客户端渲染
const AgentGraphDynamic = dynamic(
  () => import('@/components/three/AgentGraph').then((m) => m.AgentGraph),
  { ssr: false },
)

const TOOL_CATEGORIES = [
  {
    name: 'Memory',
    color: '#7c5cff',
    desc: '检索结构化记忆',
    tools: ['SearchMemory', 'SearchRecords', 'SearchSummaries'],
  },
  {
    name: 'Context',
    color: '#5cefff',
    desc: '拉取当前上下文',
    tools: ['GetCurrentTime', 'GetRecentContext', 'GetUserContextSettings'],
  },
  {
    name: 'Device',
    color: '#ffb85c',
    desc: '读取设备/环境',
    tools: ['GetDeviceStatus', 'GetWeather'],
  },
  {
    name: 'Health',
    color: '#ff7c9c',
    desc: '查询健康数据',
    tools: ['QueryHealthData'],
  },
  {
    name: 'Action',
    color: '#5cffb0',
    desc: '触发系统级动作',
    tools: ['CreateLocalReminder'],
  },
] as const

const PROVIDERS = [
  { name: 'GLM', model: 'glm-5v-turbo', runtime: '智谱 · Anthropic 兼容', location: 'remote' as const, color: '#a07cff' },
  { name: 'KIMI', model: 'kimi-for-coding', runtime: '月之暗面 · Anthropic 兼容', location: 'remote' as const, color: '#9090a8' },
  { name: 'MODELSCOPE', model: 'Qwen3.5-397B-A17B', runtime: '魔搭 · Anthropic 兼容', location: 'remote' as const, color: '#ff7c9c' },
  { name: 'LOCAL_QWEN', model: 'Qwen3.5-{0.8B,2B,4B}-MNN', runtime: '本地 MNN 推理', location: 'on-device' as const, color: '#5cffb0' },
]

const PIPELINE_STAGES = [
  { stage: 'Input', desc: '用户消息 + 历史上下文 + 命中的记忆', color: '#7c5cff' },
  { stage: 'Assemble', desc: 'Prompt 组装 · 工具描述 + 系统提示', color: '#a07cff' },
  { stage: 'LLM Call', desc: 'Koog PromptExecutor → 云端 / 本地', color: '#5cefff' },
  { stage: 'Tool Decide', desc: '大模型输出工具调用 → 注册到 ToolRegistry', color: '#ffb85c' },
  { stage: 'Tool Run', desc: '执行本地 / 远程 MCP 工具 · 收集结果', color: '#5cffb0' },
  { stage: 'Stream', desc: 'EventHandler → 流式推回 UI', color: '#ff7c9c' },
]

export default function AgentPage() {
  return (
    <FeatureShell
      number="03"
      category="Runtime"
      title="云端办事，本地懂事。"
      subtitle="这一页不再讲它懂不懂你，而是讲它怎样连接工具、MCP 和现实生活，让一次对话真的走向行动。"
      active="agent"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(255, 124, 156, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(160, 92, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
    >
      {/* ─── 第一屏：3D 沉浸 + 数字速记 ─── */}
      <HeroStage
        variant="agent"
        three={
          <>
            <AgentGraphDynamic />

            {/* 图例（已无边框，浮在 3D 左上） */}
            <div className="pointer-events-none absolute left-6 top-6 flex flex-col gap-1.5 font-mono text-[10px]">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-white" />
                <span className="text-muted">智能体核心</span>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ background: '#7c5cff' }}
                />
                <span className="text-muted">工具 / MCP / 生活能力</span>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className="h-1.5 w-1.5 rotate-45"
                  style={{
                    background: 'transparent',
                    border: '1px solid #9090a8',
                  }}
                />
                <span className="text-muted">云端对话体 + 本地陪伴体</span>
              </div>
            </div>
          </>
        }
        stats={[
          { n: '10', label: '工具', desc: '记忆、上下文、设备、健康、动作五类能力' },
          { n: 'MCP', label: '扩展', desc: '地图、本地生活、出行、咖啡、快餐都能接' },
          { n: '12', label: '迭代上限', desc: 'maxIterations=12 · 有界防失控' },
        ]}
        caption="工具层：把外部能力和生活能力接进 Aura"
      />

      {/* ─── 双心智与工具层 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            它怎么从聊天走向行动
          </h2>
          <span className="font-mono text-xs text-muted">
            5 类能力
          </span>
        </div>
        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          Aura 的工具层不是“会调 API”，而是把记忆、上下文、设备、健康、提醒和 MCP 组织成一层可控的行动能力。
        </p>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {TOOL_CATEGORIES.map((cat, i) => (
            <Reveal
              key={cat.name}
              direction="y"
              delay={i * 80}
              className="rounded-xl border border-border p-6"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span
                    className="h-2 w-2 rounded-full"
                    style={{ backgroundColor: cat.color }}
                  />
                  <span className="font-mono text-xs uppercase tracking-wider text-foreground">
                    {cat.name}
                  </span>
                </div>
                <span className="font-mono text-[10px] text-muted">
                  {cat.tools.length} tools
                </span>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted">
                {cat.desc}
              </p>
              <div className="mt-4 flex flex-wrap gap-1.5">
                {cat.tools.map((t) => (
                  <span
                    key={t}
                    className="rounded-full border border-border bg-subtle/40 px-2.5 py-0.5 font-mono text-[10px] text-foreground"
                  >
                    {t}
                  </span>
                ))}
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ─── 推理 Pipeline 6 阶段 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            流式管线
          </h2>
          <span className="font-mono text-xs text-muted">
            事件处理器 · 6 个阶段
          </span>
        </div>

        <div className="mt-10">
          <div className="grid grid-cols-1 gap-0 md:grid-cols-6">
            {PIPELINE_STAGES.map((s, i) => (
              <Reveal
                key={s.stage}
                delay={i * 80}
                className="relative"
              >
                {/* 阶段编号 */}
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-accent">
                    0{i + 1}
                  </span>
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: s.color }}
                  />
                </div>

                <h3 className="mt-3 font-mono text-sm uppercase tracking-wider text-foreground">
                  {s.stage}
                </h3>
                <p className="mt-2 text-xs leading-relaxed text-muted">
                  {s.desc}
                </p>

                {/* 连接线（非最后一项） */}
                {i < PIPELINE_STAGES.length - 1 && (
                  <div
                    className="absolute right-0 top-1.5 hidden h-px w-4 bg-border-strong md:block"
                    style={{ transform: 'translateX(100%)' }}
                  />
                )}
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ─── 双模路由：云端 / 本地 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            双模路由
          </h2>
          <span className="font-mono text-xs text-muted">
            3 远程 + 1 本地 · 用户在 Settings 选择
          </span>
        </div>

        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          3 个云端 Provider 和 1 个本地 Provider 共享同一个 Koog Agent 接口；真正重要的不是 Provider 名字，而是云端负责办事、本地负责理解。
        </p>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {PROVIDERS.map((p, i) => (
            <Reveal
              key={p.name}
              delay={i * 100}
              className="rounded-xl border border-border p-6"
            >
              <div className="flex items-start justify-between">
                <div>
                  <p
                    className="font-mono text-xs uppercase tracking-wider"
                    style={{ color: p.color }}
                  >
                    {p.name}
                  </p>
                  <p className="mt-3 text-sm text-foreground">{p.runtime}</p>
                  <p className="mt-1 font-mono text-[10px] text-muted">{p.model}</p>
                </div>
                {p.location === 'on-device' && (
                  <span className="rounded-full border border-accent/40 bg-accent/10 px-2 py-0.5 font-mono text-[10px] text-accent">
                    本地
                  </span>
                )}
              </div>

              {/* 性能条 */}
              <div className="mt-6">
                <div className="flex items-center justify-between font-mono text-[10px] text-muted">
                  <span>TTFT</span>
                  <span>{p.location === 'on-device' ? '~1.2s' : '~0.4s'}</span>
                </div>
                <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-subtle">
                  <div
                    style={{
                      width: p.location === 'on-device' ? '40%' : '85%',
                      backgroundColor: p.color,
                    }}
                    className="h-full rounded-full"
                  />
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ─── 关键设计原则 ─── */}
      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            生活能力示例
          </h2>
        </div>

        <ol className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2">
          {[
            {
              n: '01',
              t: '下班后遛弯',
              d: '高德 + 天气 + 步数 + 情绪，给出一条能走、能逛、能吃的路线。',
            },
            {
              n: '02',
              t: '今天吃什么',
              d: 'howtocook + 口味偏好 + 体力 + 预算，给出做饭、外卖或外出就餐方案。',
            },
            {
              n: '03',
              t: '周末出行',
              d: '12306 + 地图 + 天气，把出发时间、接驳、目的地和回程建议串成计划。',
            },
            {
              n: '04',
              t: '瑞幸 / 麦当劳',
              d: '把散步、咖啡、快餐结合成低决策成本的生活方案。',
            },
            {
              n: '05',
              t: 'MCP 可扩展',
              d: '魔搭创空间托管自定义 MCP，再同步到 Aura 工具选择中。',
            },
            {
              n: '06',
              t: '失败可见',
              d: '工具调用、MCP 失败和模型异常都有记录，方便调试和展示。',
            },
          ].map((p, i) => (
            <Reveal
              key={p.n}
              as="li"
              delay={i * 60}
              className="flex gap-4 rounded-xl border border-border p-6"
            >
              <span className="shrink-0 font-mono text-xs text-accent">{p.n}</span>
              <div>
                <h3 className="font-medium text-foreground">{p.t}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted">{p.d}</p>
              </div>
            </Reveal>
          ))}
        </ol>
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
            { href: '/memory', label: 'Memory', desc: 'Aura 如何记住你' },
            { href: '/tech', label: 'Tech', desc: 'Aura 如何组织系统' },
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
