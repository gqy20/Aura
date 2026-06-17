'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'
import { ScreenSection } from '@/components/ScreenSection'

const AgentGraphDynamic = dynamic(
  () => import('@/components/three/AgentGraph').then((m) => m.AgentGraph),
  { ssr: false },
)

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

export default function AgentPage() {
  return (
    <FeatureShell
      number="03"
      category="Runtime"
      title="云端办事，本地懂你"
      subtitle="Aura 不是把模型接进来就结束，而是把工具、MCP 和生活能力组织成真正可行动的智能体。"
      active="agent"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(255, 124, 156, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(160, 92, 255, 0.10), transparent 60%), #08090a"
      hideMeta
      heroStage={
        <HeroStage
          variant="agent"
          three={
            <>
              <AgentGraphDynamic />
              <div className="pointer-events-none absolute left-6 top-6 flex flex-col gap-1.5 font-mono text-[10px]">
                <div className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full bg-white" />
                  <span className="text-muted">智能体核心</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="h-1.5 w-1.5 rounded-full" style={{ background: 'var(--color-accent)' }} />
                  <span className="text-muted">工具 / MCP / 生活能力</span>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className="h-1.5 w-1.5 rotate-45"
                    style={{ background: 'transparent', border: '1px solid var(--aura-muted)' }}
                  />
                  <span className="text-muted">云端对话体 + 本地陪伴体</span>
                </div>
              </div>
            </>
          }
          stats={[
            { n: '10', label: '工具', desc: '记忆、上下文、设备、健康、动作五类能力' },
            { n: 'MCP', label: '扩展', desc: '地图、出行、咖啡、餐饮和开发者自建服务' },
            { n: '12', label: '迭代上限', desc: 'maxIterations=12，保证流程有边界' },
          ]}
          caption="工具层把外部能力真正接入 Aura，而不是停留在回答层"
        />
      }
    >

      <ScreenSection innerClassName="max-w-7xl justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">它怎么从聊天走向行动</h2>
          <span className="font-mono text-xs text-muted">5 类能力</span>
        </div>
        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          这一层不是“会调 API”，而是把记忆、设备、健康、提醒和 MCP 组织成可控的行动能力。
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
                  <span className="h-2 w-2 rounded-full" style={{ backgroundColor: cat.color }} />
                  <span className="font-mono text-xs uppercase tracking-wider text-foreground">{cat.name}</span>
                </div>
                <span className="font-mono text-[10px] text-muted">{cat.tools.length} tools</span>
              </div>
              <p className="mt-3 text-sm leading-relaxed text-muted">{cat.desc}</p>
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
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-7xl justify-center">
        <div className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
          <div className="md:col-span-7">
            <div className="flex items-end justify-between border-b border-border pb-4">
              <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">一条请求如何流动</h2>
              <span className="font-mono text-xs text-muted">5 个阶段</span>
            </div>

            <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2">
              {PIPELINE_STAGES.map((s, i) => (
                <Reveal key={s.stage} delay={i * 70} className="rounded-xl border border-border p-5">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-accent">0{i + 1}</span>
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: s.color }} />
                  </div>
                  <h3 className="mt-3 font-mono text-sm uppercase tracking-wider text-foreground">{s.stage}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted">{s.desc}</p>
                </Reveal>
              ))}
            </div>
          </div>

          <div className="md:col-span-5">
            <div className="flex items-end justify-between border-b border-border pb-4">
              <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">双模路由</h2>
              <span className="font-mono text-xs text-muted">云端 + 本地</span>
            </div>
            <p className="mt-6 text-pretty leading-relaxed text-muted">
              Provider 的重点不在名字，而在分工: 云端负责连工具、办事情，本地负责理解你、保护你。
            </p>

            <div className="mt-8 grid grid-cols-1 gap-3">
              {PROVIDERS.map((p, i) => (
                <Reveal key={p.name} delay={i * 80} className="rounded-xl border border-border p-4">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-mono text-xs uppercase tracking-wider" style={{ color: p.color }}>
                        {p.name}
                      </p>
                      <p className="mt-2 text-sm text-foreground">{p.runtime}</p>
                      <p className="mt-1 font-mono text-[10px] text-muted">{p.model}</p>
                    </div>
                    {p.name === 'LOCAL_QWEN' && (
                      <span className="rounded-full border border-accent/40 bg-accent/10 px-2 py-0.5 font-mono text-[10px] text-accent">
                        on-device
                      </span>
                    )}
                  </div>
                </Reveal>
              ))}
            </div>
          </div>
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-7xl justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">真实生活里的几个场景</h2>
        </div>

        <ol className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
          {[
            ['下班后遛弯', '地图 + 天气 + 步数 + 情绪，给出一条能走、能逛、能吃的路线。'],
            ['今天吃什么', 'howtocook + 口味偏好 + 体力 + 预算，给出做饭或外出就餐方案。'],
            ['周末出行', '12306 + 地图 + 天气，把出发、接驳、目的地和返程串成计划。'],
            ['咖啡与轻餐', '瑞幸、麦当劳等 MCP 可以和顺路、时段、步行意愿一起推荐。'],
          ].map(([title, desc], i) => (
            <Reveal key={title} as="li" delay={i * 60} className="flex gap-4 rounded-xl border border-border p-6">
              <span className="shrink-0 font-mono text-xs text-accent">0{i + 1}</span>
              <div>
                <h3 className="font-medium text-foreground">{title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted">{desc}</p>
              </div>
            </Reveal>
          ))}
        </ol>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-7xl justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">相关</h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/presence', label: 'Presence', desc: 'Aura 如何持续在场' },
            { href: '/memory', label: 'Memory', desc: 'Aura 如何形成个人模型' },
            { href: '/tech', label: 'Tech', desc: 'Aura 如何组织系统' },
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
