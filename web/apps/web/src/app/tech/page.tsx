'use client'

import { FeatureShell } from '@/components/feature/FeatureShell'
import { HeroStage } from '@/components/feature/HeroStage'
import { Reveal } from '@/components/Reveal'

const SYSTEM_LAYERS = [
  {
    name: '陪伴运行时',
    desc: '聊天、Presence、Dream Loop 和 Reminder 共同组成 Aura 的持续运行时，不只是一次对话请求。',
    points: ['ChatViewModel', 'PresenceController', 'DreamLoopWorker', 'ReminderNotificationWorker'],
  },
  {
    name: '双心智分工',
    desc: '云端对话体负责调用工具和连接外部世界，本地陪伴体负责理解、记忆、洞察与隐私边界。',
    points: ['Koog AIAgent', 'ReactiveCompanion', 'LocalQwenExecutor', 'LlmConnectivityChecker'],
  },
  {
    name: '可信个人模型',
    desc: '记忆、摘要和洞察都带着来源线索、置信度和用户控制边界，避免把用户画像做成黑箱。',
    points: ['Memory DAO', 'Memory Summary', 'InsightRepository', 'InsightValidator'],
  },
  {
    name: '工具与 MCP',
    desc: '工具层把设备、健康、提醒与 MCP 生态接到统一注册表里，让 Agent 可以办事，也能被约束。',
    points: ['CompanionToolRegistry', 'Tool Calls', 'McpSettings', 'Fallback Isolation'],
  },
] as const

const EXECUTION_FLOW = [
  {
    step: '01',
    title: '输入组装',
    desc: '把用户消息、最近上下文、命中的记忆和当前设备状态整理成一次可解释的请求。',
  },
  {
    step: '02',
    title: '路由判断',
    desc: '根据模型可达性、场景需求和隐私边界，决定由云端对话体还是本地陪伴体接手。',
  },
  {
    step: '03',
    title: '工具执行',
    desc: '需要外部能力时进入 ToolRegistry 或 MCP；不需要时保持纯对话与本地推理路径。',
  },
  {
    step: '04',
    title: '流式返回',
    desc: 'Koog 事件流把内容实时推回 UI，同时记录调用状态，方便调试和展示。',
  },
  {
    step: '05',
    title: '后处理沉淀',
    desc: '消息结束后再进入记忆抽取、摘要更新、洞察校验和 Presence 反应，而不是把一切塞进同一轮回复。',
  },
] as const

const TECH_POINTS = [
  {
    title: '为什么不是单模型方案',
    desc: '比赛要求本地模型是前提，不是亮点。Aura 的重点在于把本地模型放进双心智协作里，让它承担理解你、保护你、持续认识你的那部分工作。',
  },
  {
    title: '为什么要做 Presence',
    desc: '陪伴产品的差异不在回答一次问题，而在它是否持续在场。PresenceController 和 ReactionPolicy 把这种在场感落实成状态与反应逻辑。',
  },
  {
    title: '为什么强调可信',
    desc: '记忆和洞察如果不能说明来源、不能被修正、也不能被用户干预，很快就会失去信任。Aura 用 source、confidence 和控制边界来约束个人模型。',
  },
  {
    title: '为什么接 MCP',
    desc: 'MCP 不是为了堆功能，而是把生活能力接到统一协议上。地图、12306、做饭、咖啡这类能力，才能和健康、记忆、情绪一起组成真正可用的生活建议。',
  },
] as const

const BOUNDARIES = [
  'Dream Loop 不直接走外部工具链，优先保持本地整理与轻量推理。',
  'MCP 失败不会拖垮整次对话，会以独立状态记录并允许继续回复。',
  '本地模型和云端模型共用一致接口，但承担不同职责，不混成一个模糊的自动最优。',
  '洞察要先过校验与置信度门槛，避免把偶然聊天内容固化成长期判断。',
] as const

export default function TechPage() {
  return (
    <FeatureShell
      number="04"
      category="System"
      title="它不只是能跑，而是怎么跑。"
      subtitle="这里不再讲 Aura 为什么动人，而是讲这套体验如何被真实系统支撑起来：架构怎么分层、请求怎么流转、边界怎么守住。"
      active="tech"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(92, 239, 255, 0.12), transparent 60%), radial-gradient(ellipse 60% 40% at 78% 82%, rgba(124, 92, 255, 0.08), transparent 60%), #08090a"
      hideMeta
      hideAnnouncement
    >
      <HeroStage
        variant="presence"
        three={
          <div className="relative h-full w-full overflow-hidden">
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(124,92,255,0.16),transparent_28%),radial-gradient(circle_at_80%_30%,rgba(92,239,255,0.12),transparent_28%),radial-gradient(circle_at_60%_80%,rgba(92,255,176,0.10),transparent_26%)]" />
            <div className="absolute inset-x-6 top-6 grid gap-4 md:grid-cols-3">
              {[
                {
                  title: '云端',
                  desc: '负责工具、MCP 和外部世界，把对话真正接到现实能力上。',
                },
                {
                  title: '本地',
                  desc: '负责理解、记忆、洞察和边界，让陪伴感与隐私都留在手机里。',
                },
                {
                  title: '运行时',
                  desc: '负责把聊天、Dream Loop、Reminder 和 Presence 串成持续运行的系统。',
                },
              ].map((item) => (
                <div
                  key={item.title}
                  className="rounded-xl border border-border bg-background/25 p-5 backdrop-blur-sm"
                >
                  <p className="font-mono text-[11px] uppercase tracking-[0.18em] text-accent">
                    {item.title}
                  </p>
                  <p className="mt-3 text-sm leading-relaxed text-foreground/90">
                    {item.desc}
                  </p>
                </div>
              ))}
            </div>

            <div className="absolute bottom-8 left-6 right-6 grid gap-4 md:grid-cols-4">
              {[
                ['运行时', '持续运行'],
                ['记忆', '长期沉淀'],
                ['工具', '连接现实'],
                ['边界', '保持克制'],
              ].map(([title, desc]) => (
                <div
                  key={title}
                  className="rounded-xl border border-border bg-background/20 p-4 backdrop-blur-sm"
                >
                  <p className="font-mono text-xs uppercase tracking-[0.18em] text-foreground">
                    {title}
                  </p>
                  <p className="mt-2 text-sm text-muted">{desc}</p>
                </div>
              ))}
            </div>
          </div>
        }
        stats={[
          { n: '4', label: '核心层', desc: '运行时、双心智、个人模型、工具生态' },
          { n: '5', label: '执行阶段', desc: '输入、路由、执行、返回、沉淀' },
          { n: 'MCP', label: '可扩展', desc: '地图、出行、吃饭、咖啡与开发者自建服务' },
        ]}
        caption="技术页：把产品叙事落到真实架构、执行路径和运行边界上"
      />

      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            系统主线
          </h2>
          <span className="font-mono text-xs text-muted">
            4 层结构
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
          {SYSTEM_LAYERS.map((layer, index) => (
            <Reveal
              key={layer.name}
              delay={index * 80}
              className="rounded-xl border border-border p-6"
            >
              <p className="font-mono text-xs uppercase tracking-[0.18em] text-accent">
                {layer.name}
              </p>
              <p className="mt-3 text-base leading-relaxed text-foreground">
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
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            执行路径
          </h2>
          <span className="font-mono text-xs text-muted">
            一次请求如何流转
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 lg:grid-cols-5">
          {EXECUTION_FLOW.map((item, index) => (
            <Reveal
              key={item.step}
              delay={index * 70}
              className="rounded-xl border border-border p-6"
            >
              <p className="font-mono text-xs text-accent">{item.step}</p>
              <h3 className="mt-3 text-lg font-medium text-foreground">{item.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted">{item.desc}</p>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            为什么这样设计
          </h2>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2">
          {TECH_POINTS.map((point, index) => (
            <Reveal
              key={point.title}
              delay={index * 80}
              className="rounded-xl border border-border p-6"
            >
              <h3 className="text-lg font-medium text-foreground">{point.title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted">{point.desc}</p>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            当前边界
          </h2>
          <span className="font-mono text-xs text-muted">
            当前真实约束
          </span>
        </div>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2">
          {BOUNDARIES.map((item, index) => (
            <Reveal
              key={item}
              delay={index * 70}
              className="rounded-xl border border-border p-6"
            >
              <p className="font-mono text-xs text-accent">
                0{index + 1}
              </p>
              <p className="mt-3 text-sm leading-relaxed text-muted">{item}</p>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mt-32 px-6 sm:px-10 lg:px-16">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            相关页面
          </h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { href: '/presence', label: 'Presence', desc: '它如何持续在场' },
            { href: '/memory', label: 'Memory', desc: '它如何形成个人模型' },
            { href: '/agent', label: 'Agent', desc: '它如何接工具与 MCP' },
            { href: '/', label: 'Home', desc: '回到总览叙事' },
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
