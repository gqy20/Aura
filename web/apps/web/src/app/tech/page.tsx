'use client'

import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { ChapterBlock } from '@/components/feature/ChapterBlock'
import { FooterMeta } from '@/components/feature/FooterMeta'
import { Reveal } from '@/components/Reveal'
import { TechArch } from '@/components/three/TechArch'

const SIBLINGS = [
  { href: '/presence', label: 'Presence', key: 'presence' as const },
  { href: '/memory', label: 'Memory', key: 'memory' as const },
  { href: '/agent', label: 'Agent', key: 'agent' as const },
  { href: '/tech', label: 'Tech', key: 'tech' as const },
]

const SYSTEM_LAYERS = [
  {
    name: '陪伴运行时',
    desc: '聊天、Presence、Dream Loop 和 Reminder 共同构成持续运行的系统。',
    points: ['ChatViewModel', 'PresenceController', 'DreamLoopWorker', 'ReminderNotificationWorker'],
  },
  {
    name: '双心智分工',
    desc: '云端负责调用工具和外部世界，本地负责理解、记忆和边界。',
    points: ['Koog AIAgent', 'ReactiveCompanion', 'LocalQwenExecutor', 'LlmConnectivityChecker'],
  },
  {
    name: '可信个人模型',
    desc: '记忆、摘要和洞察都保留来源线索、置信度和用户控制边界。',
    points: ['Memory DAO', 'Memory Summary', 'InsightRepository', 'InsightValidator'],
  },
  {
    name: '工具与 MCP',
    desc: '把设备、健康、提醒和 MCP 收进统一注册表里。',
    points: ['CompanionToolRegistry', 'Tool Calls', 'McpSettings', 'Fallback Isolation'],
  },
]

const EXECUTION_FLOW = [
  ['输入组装', '把消息、上下文、记忆和设备状态整理成一次请求。'],
  ['路由判断', '根据可达性、场景和隐私边界决定走云端还是本地。'],
  ['工具执行', '需要外部能力时进入 ToolRegistry 或 MCP。'],
  ['流式返回', '事件流实时推回 UI，同时记录调用状态。'],
  ['后处理沉淀', '再做记忆抽取、摘要更新和洞察校验。'],
]

const BOUNDARIES = [
  'Dream Loop 优先保持本地整理与轻量推理，不直接走外部工具链。',
  'MCP 失败不会拖垮整次对话，会单独记录并继续返回结果。',
  '本地模型和云端模型共用一套接口，但承担不同职责。',
  '洞察必须先过校验和置信度门槛，避免把偶然聊天当长期判断。',
]

export default function TechPage() {
  return (
    <FeatureShell
      number="04"
      category="System"
      title="它怎么跑起来"
      active="tech"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 239, 255, 0.12), transparent 60%), radial-gradient(ellipse 60% 40% at 78% 82%, rgba(124, 92, 255, 0.08), transparent 60%), #08090a"
      hideMeta
      heroStage={
        <HeroStage
          variant="agent"
          three={<TechArch />}
          stats={[
            { n: '4', label: '核心层', desc: '运行时、双心智、个人模型、工具与 MCP' },
            { n: '5', label: '执行段', desc: '输入、路由、执行、返回、沉淀' },
            { n: 'MCP', label: '可扩展', desc: '地图、出行、餐饮和开发者自建服务' },
          ]}
          caption="技术页只保留最关键的四层结构和一条执行路径"
        />
      }
    >
      <ChapterBlock
        number="01"
        eyebrow="Overview"
        title="四层结构 · 一条执行路径"
        description="Aura 的代码主体由 4 层组成：陪伴运行时把一切连在一起，双心智负责分工，可信个人模型负责记忆边界，工具与 MCP 把外部世界接入。"
      />

      <ChapterBlock
        number="02"
        eyebrow="Architecture"
        title="系统主线"
        description="4 层结构，自下而上 —— 工具与 MCP 是接入层，个人模型是记忆层，双心智是分工层，运行时是入口层。"
      >
        <div className="space-y-12">
          {SYSTEM_LAYERS.map((layer, index) => (
            <Reveal key={layer.name} delay={index * 80}>
              <div className="grid grid-cols-1 gap-6 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="label-mono text-accent">{layer.name}</span>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-base leading-relaxed text-foreground">
                    {layer.desc}
                  </p>
                  <ul className="mt-4 flex flex-wrap gap-2">
                    {layer.points.map((point) => (
                      <li
                        key={point}
                        className="rounded-full border border-border bg-subtle/40 px-3 py-1 font-mono text-[11px] text-muted"
                      >
                        {point}
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
        eyebrow="Execution"
        title="执行路径"
        description="一次请求从组装到沉淀的 5 段路径 —— 每段都有清晰的输入、产出和失败语义。"
      >
        <div className="space-y-0">
          {EXECUTION_FLOW.map(([title, desc], index) => (
            <Reveal key={title} delay={index * 70}>
              <div className="grid grid-cols-1 items-baseline gap-4 border-t border-border py-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="font-mono text-xs text-accent">0{index + 1}</span>
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

      <ChapterBlock
        number="04"
        eyebrow="Boundaries"
        title="边界"
        description="真实约束，不是设计愿景 —— 这些是我们主动选择不去做的事。"
      >
        <div className="space-y-10">
          {BOUNDARIES.map((item, index) => (
            <Reveal key={item} delay={index * 70}>
              <div className="grid grid-cols-1 gap-4 border-t border-border pt-6 md:grid-cols-12 md:gap-12">
                <div className="md:col-span-3">
                  <span className="font-mono text-xs text-accent">0{index + 1}</span>
                  <span className="label-mono mt-2 inline-block text-muted">Constraint</span>
                </div>
                <div className="md:col-span-9">
                  <p className="max-w-prose text-pretty text-base leading-relaxed text-foreground">
                    {item}
                  </p>
                </div>
              </div>
            </Reveal>
          ))}
        </div>
      </ChapterBlock>

      <FooterMeta
        number="04"
        category="System"
        siblings={SIBLINGS}
        currentKey="tech"
      />
    </FeatureShell>
  )
}
