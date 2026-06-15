'use client'

import dynamic from 'next/dynamic'
import { FeatureShell } from '@/components/feature/FeatureShell'
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
    name: 'Action',
    color: '#5cffb0',
    desc: '触发系统级动作',
    tools: ['CreateLocalReminder'],
  },
] as const

const PROVIDERS = [
  { name: 'Cloud Qwen', runtime: 'Remote · HTTPS', fallback: true, color: '#a07cff' },
  { name: 'OpenAI', runtime: 'Remote · HTTPS', fallback: false, color: '#9090a8' },
  { name: 'Local MNN', runtime: 'On-device · JNI', fallback: true, color: '#5cffb0' },
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
      title="协调一切的智能体。"
      subtitle="Aura 的灵魂是 Koog AIAgent：内置 9 个结构化工具、3 套大模型 Provider、流式响应、可观测事件流——它不是大模型，是大模型之上的编排者。"
      active="agent"
      bgGradient="radial-gradient(ellipse 70% 50% at 50% 0%, rgba(255, 124, 156, 0.14), transparent 60%), radial-gradient(ellipse 60% 40% at 80% 80%, rgba(160, 92, 255, 0.10), transparent 60%), #08090a"
    >
      {/* ─── 3D 主体 + 工具分类 ─── */}
      <section className="grid grid-cols-1 gap-12 md:grid-cols-12 md:gap-16">
        {/* 左：3D Canvas */}
        <div className="relative md:col-span-7">
          <div className="relative aspect-[4/3] w-full overflow-hidden rounded-2xl border border-border bg-subtle/30">
            <AgentGraphDynamic />

            {/* 图例 */}
            <div className="absolute left-4 top-4 flex flex-col gap-1.5 font-mono text-[10px]">
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full bg-white" />
                <span className="text-muted">智能体核心</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: '#7c5cff' }} />
                <span className="text-muted">9 个工具（4 类）</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="h-1.5 w-1.5 rotate-45" style={{ background: 'transparent', border: '1px solid #9090a8' }} />
                <span className="text-muted">3 个 LLM 提供方</span>
              </div>
            </div>
          </div>

          <p className="mt-4 font-mono text-xs text-muted">
            Koog AIAgent.builder() · graphStrategy(streamingSingleRunStrategy()) · maxIterations=20
          </p>
        </div>

        {/* 右：4 类工具 */}
        <div className="md:col-span-5">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            九个工具，四类划分
          </h2>
          <p className="mt-4 text-pretty leading-relaxed text-muted">
            9 个内置工具 + 动态加载的远程 MCP 工具。AuraAgent 不会"调用 API"——它调度
            ToolRegistry，把世界变成可执行的指令集。
          </p>

          <div className="mt-8 space-y-4">
            {TOOL_CATEGORIES.map((cat, i) => (
              <Reveal
                key={cat.name}
                direction="x"
                delay={i * 100}
                className="rounded-xl border border-border p-5"
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
                <p className="mt-2 text-sm text-muted">{cat.desc}</p>
                <div className="mt-3 flex flex-wrap gap-1.5">
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
        </div>
      </section>

      {/* ─── 推理 Pipeline 6 阶段 ─── */}
      <section className="mt-32">
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            双模路由
          </h2>
          <span className="font-mono text-xs text-muted">
            云端优先 · 本地兜底
          </span>
        </div>

        <p className="mt-6 max-w-3xl text-pretty leading-relaxed text-muted">
          Aura 永远先尝试云端 Qwen（推理快、质量高）。当网络不通时，自动回退到
          本地 MNN 推理，确保对话不中断。两个提供方共享同一个 Koog
          Agent 接口，UI 层无感。
        </p>

        <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
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
                </div>
                {p.fallback && (
                  <span className="rounded-full border border-accent/40 bg-accent/10 px-2 py-0.5 font-mono text-[10px] text-accent">
                    兜底
                  </span>
                )}
              </div>

              {/* 性能条 */}
              <div className="mt-6">
                <div className="flex items-center justify-between font-mono text-[10px] text-muted">
                  <span>TTFT</span>
                  <span>{i === 2 ? '~1.2s' : '~0.4s'}</span>
                </div>
                <div className="mt-1 h-1 w-full overflow-hidden rounded-full bg-subtle">
                  <div
                    style={{
                      width: i === 2 ? '40%' : '85%',
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            设计原则
          </h2>
        </div>

        <ol className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2">
          {[
            {
              n: '01',
              t: '拒绝黑盒',
              d: '所有工具调用都有结构化记录器（toolCallRecorder）：工具名 / 参数 / 结果 / 状态全可追溯。',
            },
            {
              n: '02',
              t: '流式优先',
              d: 'AIAgent 用 streamingSingleRunStrategy + EventHandler，token 级别的流式推回 UI，无整段等待。',
            },
            {
              n: '03',
              t: '有界迭代',
              d: 'maxIterations=20。大模型死循环不消耗资源，到上限自动停。',
            },
            {
              n: '04',
              t: 'MCP 是一等公民',
              d: '远程 MCP server 动态加入 ToolRegistry，列表来自 McpServerListRepository，UI 软开关。',
            },
            {
              n: '05',
              t: '默认清空工具注册表',
              d: '当 prompt 含图片或关闭工具时，ToolRegistry.EMPTY——减少无关工具对大模型的干扰。',
            },
            {
              n: '06',
              t: '失败可见',
              d: 'onToolCallFailed / agent_run_failed 都走 AppLogger，logcat 可追。',
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
      <section className="mt-32">
        <div className="flex items-end justify-between border-b border-border pb-4">
          <h2 className="text-2xl font-medium tracking-tight sm:text-3xl">
            相关
          </h2>
        </div>
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { href: '/presence', label: 'Presence', desc: 'Aura 如何呈现自己' },
            { href: '/memory', label: 'Memory', desc: 'Aura 如何记住你' },
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
