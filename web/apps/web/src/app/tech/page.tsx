'use client'

import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'
import { ScreenSection } from '@/components/ScreenSection'

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
      subtitle="这里不再讲 Aura 为什么动人，而是讲这套体验如何被真实系统支撑起来。"
      active="tech"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 239, 255, 0.12), transparent 60%), radial-gradient(ellipse 60% 40% at 78% 82%, rgba(124, 92, 255, 0.08), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
      heroStage={
        <HeroStage
          variant="presence"
          three={
            <div className="relative h-full w-full overflow-hidden">
              <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(124,92,255,0.16),transparent_28%),radial-gradient(circle_at_80%_30%,rgba(92,239,255,0.12),transparent_28%),radial-gradient(circle_at_60%_80%,rgba(92,255,176,0.10),transparent_26%)]" />
              <div className="absolute inset-0 grid place-items-center">
                <div className="grid gap-4 text-center md:grid-cols-3">
                  {['云端', '本地', '运行时'].map((item) => (
                    <div key={item} className="rounded-xl border border-border bg-background/25 p-5 backdrop-blur-sm">
                      <p className="font-mono text-[11px] uppercase tracking-[0.18em] text-accent">{item}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          }
          stats={[
            { n: '4', label: '核心层', desc: '运行时、双心智、个人模型、工具与 MCP' },
            { n: '5', label: '执行段', desc: '输入、路由、执行、返回、沉淀' },
            { n: 'MCP', label: '可扩展', desc: '地图、出行、餐饮和开发者自建服务' },
          ]}
          caption="技术页只保留最关键的四层结构和一条执行路径"
        />
      }
    >

      <ScreenSection innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">系统主线</h2>
          <span className="font-mono text-xs text-muted">4 层结构</span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
          {SYSTEM_LAYERS.map((layer, index) => (
            <Reveal key={layer.name} delay={index * 80} className="rounded-xl border border-border p-6">
              <p className="font-mono text-xs uppercase tracking-[0.18em] text-accent">{layer.name}</p>
              <p className="mt-3 text-base leading-relaxed text-foreground">{layer.desc}</p>
              <ul className="mt-4 flex flex-wrap gap-2">
                {layer.points.map((point) => (
                  <li key={point} className="rounded-full border border-border bg-subtle/40 px-3 py-1 font-mono text-[11px] text-muted">
                    {point}
                  </li>
                ))}
              </ul>
            </Reveal>
          ))}
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">执行路径</h2>
          <span className="font-mono text-xs text-muted">一次请求怎么流动</span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {EXECUTION_FLOW.map(([title, desc], index) => (
            <Reveal key={title} delay={index * 70} className="rounded-xl border border-border p-6">
              <p className="font-mono text-xs text-accent">0{index + 1}</p>
              <h3 className="mt-3 text-lg font-medium text-foreground">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{desc}</p>
            </Reveal>
          ))}
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">边界</h2>
          <span className="font-mono text-xs text-muted">真实约束</span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
          {BOUNDARIES.map((item, index) => (
            <Reveal key={item} delay={index * 70} className="rounded-xl border border-border p-6">
              <p className="font-mono text-xs text-accent">0{index + 1}</p>
              <p className="mt-3 text-sm leading-relaxed text-muted">{item}</p>
            </Reveal>
          ))}
        </div>
      </ScreenSection>

      <ScreenSection className="mt-0" innerClassName="max-w-[1280px] justify-center">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">相关</h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { href: '/presence', label: 'Presence', desc: '如何持续在场' },
            { href: '/memory', label: 'Memory', desc: '如何形成个人模型' },
            { href: '/agent', label: 'Agent', desc: '如何接入工具与 MCP' },
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
