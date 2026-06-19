'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { ChapterBlock } from '@/components/feature/ChapterBlock'
import { FooterMeta } from '@/components/feature/FooterMeta'
import { Reveal } from '@/components/Reveal'

const AgentGraphDynamic = dynamic(
  () => import('@/components/three/AgentGraph').then((m) => m.AgentGraph),
  { ssr: false },
)

const SIBLINGS = [
  { href: '/presence', label: 'Presence', key: 'presence' as const },
  { href: '/memory', label: 'Memory', key: 'memory' as const },
  { href: '/agent', label: 'Agent', key: 'agent' as const },
  { href: '/tech', label: 'Tech', key: 'tech' as const },
]

const TOOL_CATEGORIES = [
  {
    name: 'Memory',
    color: 'var(--color-accent)',
    desc: '检索结构化记忆与摘要。',
    tools: ['SearchMemory', 'SearchRecords', 'SearchSummaries'],
  },
  {
    name: 'Context',
    color: 'var(--aura-listening)',
    desc: '获取当前时间、上下文与用户设置。',
    tools: ['GetCurrentTime', 'GetRecentContext', 'GetUserContextSettings'],
  },
  {
    name: 'Device',
    color: 'var(--aura-speaking)',
    desc: '读取设备与环境信息。',
    tools: ['GetDeviceStatus', 'GetWeather'],
  },
  {
    name: 'Health',
    color: 'var(--aura-health)',
    desc: '查询健康与运动数据。',
    tools: ['QueryHealthData'],
  },
  {
    name: 'Action',
    color: 'var(--aura-memory)',
    desc: '触发提醒与本地动作。',
    tools: ['CreateLocalReminder'],
  },
] as const

const PIPELINE_STAGES = [
  { stage: 'Input', desc: '用户消息、上下文和命中的记忆进入一次请求。', color: 'var(--color-accent)' },
  { stage: 'Assemble', desc: '系统提示、工具描述和用户状态被拼成可执行 Prompt。', color: 'var(--aura-thinking)' },
  { stage: 'LLM Call', desc: 'Koog PromptExecutor 路由到云端或本地模型。', color: 'var(--aura-listening)' },
  { stage: 'Tool Run', desc: '需要外部能力时，进入 ToolRegistry 或 MCP。', color: 'var(--aura-speaking)' },
  { stage: 'Stream', desc: '事件流把结果和状态实时推回 UI。', color: 'var(--aura-memory)' },
]

const PROVIDERS = [
  { name: 'GLM', model: 'glm-5v-turbo', runtime: '云端对话体', color: 'var(--aura-thinking)' },
  { name: 'KIMI', model: 'kimi-for-coding', runtime: '云端对话体', color: 'var(--aura-muted)' },
  { name: 'MODELSCOPE', model: 'Qwen3.5-397B-A17B', runtime: '云端对话体', color: 'var(--aura-health)' },
  { name: 'LOCAL_QWEN', model: 'Qwen3.5-{0.8B,2B,4B}-MNN', runtime: '本地陪伴体', color: 'var(--aura-memory)' },
] as const

const SCENARIOS = [
  ['下班后遛弯', '地图 + 天气 + 步数 + 情绪，给出一条能走、能逛、能吃的路线。'],
  ['今天吃什么', 'howtocook + 口味偏好 + 体力 + 预算，给出做饭或外出就餐方案。'],
  ['周末出行', '12306 + 地图 + 天气，把出发、接驳、目的地和返程串成计划。'],
  ['咖啡与轻餐', '瑞幸、麦当劳等 MCP 可以和顺路、时段、步行意愿一起推荐。'],
]

export default function AgentPage() {
  return (
    <FeatureShell
      number="03"
      category="Runtime"
      title="云端办事，本地懂你"
      active="agent"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(255, 124, 156, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(160, 92, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      heroStage={
        <HeroStage
          variant="agent"
          three={<AgentGraphDynamic />}
          stats={[
            { n: '10', label: '工具', desc: '记忆、上下文、设备、健康、动作五类能力' },
            { n: 'MCP', label: '扩展', desc: '地图、出行、咖啡、餐饮和开发者自建服务' },
            { n: '12', label: '迭代上限', desc: 'maxIterations=12，保证流程有边界' },
          ]}
          caption="工具层把外部能力真正接入 Aura，而不是停留在回答层"
        />
      }
    >
      <ChapterBlock
        number="01"
        eyebrow="Overview"
        title="它怎么从聊天走向行动"
        description="这一层不是「会调 API」，而是把记忆、设备、健康、提醒和 MCP 组织成可控的行动能力 —— 工具能调用、可被否决、可被复用。"
      />

      <ChapterBlock
        number="02"
        eyebrow="Tools"
        title="5 类工具 · 10 个调用"
        description="每个工具都属于一个清晰的能力分类，注册表保证可发现、可观测、可禁用。"
      >
        <div className="space-y-12">
          {TOOL_CATEGORIES.map((cat, i) => (
            <Reveal key={cat.name} direction="y" delay={i * 80}>
              <div className="grid grid-cols-1 gap-6 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <div className="flex items-center gap-3">
                    <span
                      className="h-2 w-2 rounded-full"
                      style={{ backgroundColor: cat.color }}
                    />
                    <span className="font-mono text-xs uppercase tracking-wider text-foreground">
                      {cat.name}
                    </span>
                  </div>
                  <span className="label-mono mt-3 inline-block text-muted">
                    {cat.tools.length} {cat.tools.length === 1 ? 'tool' : 'tools'}
                  </span>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-base leading-relaxed text-foreground">
                    {cat.desc}
                  </p>
                  <ul className="mt-4 flex flex-wrap gap-x-3 gap-y-2">
                    {cat.tools.map((t) => (
                      <li
                        key={t}
                        className="rounded-full border border-border bg-subtle/40 px-3 py-1 font-mono text-[11px] text-foreground"
                      >
                        {t}
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
        eyebrow="Pipeline"
        title="一条请求如何流动"
        description="一次请求的生命周期 = 5 个阶段。每一个都有明确的输入、产出和失败语义 —— 而不是黑盒等待。"
      >
        <div className="space-y-0">
          {PIPELINE_STAGES.map((s, i) => (
            <Reveal key={s.stage} delay={i * 70}>
              <div className="grid grid-cols-1 items-baseline gap-4 border-t border-border py-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="font-mono text-xs text-accent">
                    0{i + 1}
                  </span>
                  <h3 className="mt-1 font-mono text-base uppercase tracking-wider text-foreground">
                    {s.stage}
                  </h3>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-pretty leading-relaxed text-muted">
                    {s.desc}
                  </p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <ChapterBlock
        number="04"
        eyebrow="Routing"
        title="双模路由：云端 + 本地"
        description="Provider 的重点不在名字，而在分工: 云端负责连工具、办事情，本地负责理解你、保护你。"
      >
        <div className="space-y-8">
          {PROVIDERS.map((p, i) => (
            <Reveal key={p.name} delay={i * 80}>
              <div className="grid grid-cols-1 gap-4 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <p
                    className="font-mono text-xs uppercase tracking-wider"
                    style={{ color: p.color }}
                  >
                    {p.name}
                  </p>
                  <p className="label-mono mt-2 text-muted">{p.runtime}</p>
                </div>
                <div className="md:col-span-7">
                  <p className="font-mono text-xs text-muted">{p.model}</p>
                </div>
                <div className="md:col-span-2 md:text-right">
                  {p.name === 'LOCAL_QWEN' && (
                    <span className="rounded-full border border-accent/40 bg-accent/10 px-2.5 py-0.5 font-mono text-[10px] text-accent">
                      on-device
                    </span>
                  )}
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <ChapterBlock
        number="05"
        eyebrow="Scenarios"
        title="真实生活里的几个场景"
        description="Agent 不是 demo 数据 —— 它接 MCP、读设备、按你的偏好给你建议。"
      >
        <div className="space-y-12">
          {SCENARIOS.map(([title, desc], i) => (
            <Reveal key={title} direction="y" delay={i * 60}>
              <div className="grid grid-cols-1 gap-4 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="font-mono text-xs text-accent">0{i + 1}</span>
                  <h3 className="mt-1 text-lg font-medium text-foreground">{title}</h3>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-pretty leading-relaxed text-muted">
                    {desc}
                  </p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <FooterMeta
        number="03"
        category="Runtime"
        siblings={SIBLINGS}
        currentKey="agent"
      />
    </FeatureShell>
  )
}
