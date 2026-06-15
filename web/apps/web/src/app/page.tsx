import Link from 'next/link'

/**
 * 占位首页 — P0 阶段
 *
 * 设计原则：
 * - 左对齐（Linear/Vercel 风），告别居中堆叠
 * - 单一列 + 8 倍数节奏间距（24/48/96/144）
 * - 顶部 nav 锚点 + 底部 footer 区隔
 * - H1 不硬断行，让浏览器自然 wrap
 *
 * 待 P1 替换为完整 Hero + 滚动叙事。
 */
export default function Home() {
  return (
    <main className="relative min-h-screen overflow-hidden">
      {/* 装饰背景 */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background:
            'radial-gradient(ellipse 60% 40% at 15% 0%, rgba(124, 92, 255, 0.12), transparent), radial-gradient(ellipse 50% 35% at 85% 100%, rgba(124, 92, 255, 0.06), transparent)',
        }}
      />

      <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-8 sm:px-12">
        {/* ─── Nav ─── */}
        <nav className="flex h-20 items-center justify-between">
          <Link href="/" className="font-mono text-sm font-medium tracking-tight">
            aura<span className="text-accent">.</span>
          </Link>
          <div className="flex items-center gap-8 text-sm">
            <Link
              href="https://github.com"
              className="text-muted transition-colors hover:text-foreground"
            >
              GitHub ↗
            </Link>
          </div>
        </nav>

        {/* ─── Hero ─── */}
        <section className="flex flex-1 flex-col justify-center pb-24 pt-12">
          <p className="mb-12 font-mono text-xs uppercase tracking-[0.2em] text-muted">
            v0 · coming soon
          </p>

          <h1 className="max-w-4xl text-balance text-5xl font-medium leading-[1.05] tracking-tight sm:text-6xl md:text-7xl lg:text-[5.5rem]">
            The AI companion
            <br />
            that lives with you.
          </h1>

          <p className="mt-10 max-w-xl text-pretty text-lg leading-relaxed text-muted sm:text-xl">
            Aura 是一个开源 AI 陪伴应用，把 Presence（存在感）、Memory（记忆）和 Local LLM
            装进你的口袋。
          </p>

          <div className="mt-12 flex flex-wrap items-center gap-4">
            <Link
              href="#"
              className="group inline-flex h-12 items-center justify-center rounded-full bg-foreground px-7 text-sm font-medium text-background transition-all hover:bg-foreground/90"
            >
              Start
              <span className="ml-1 transition-transform group-hover:translate-x-0.5">→</span>
            </Link>
            <Link
              href="https://github.com"
              className="inline-flex h-12 items-center justify-center rounded-full border border-border-strong px-7 text-sm font-medium text-foreground transition-all hover:bg-subtle"
            >
              View on GitHub
            </Link>
          </div>
        </section>

        {/* ─── Data Strip ─── */}
        <section className="border-t border-border py-12">
          <dl className="grid grid-cols-2 gap-x-12 gap-y-8 sm:grid-cols-4">
            {[
              { label: 'Tests', value: '41' },
              { label: 'Modules', value: '7' },
              { label: 'Lines of Kotlin', value: '12k+' },
              { label: 'License', value: 'MIT' },
            ].map((stat) => (
              <div key={stat.label} className="flex flex-col gap-2">
                <dt className="text-3xl font-medium tracking-tight sm:text-4xl">{stat.value}</dt>
                <dd className="font-mono text-xs uppercase tracking-wider text-muted">
                  {stat.label}
                </dd>
              </div>
            ))}
          </dl>
        </section>

        {/* ─── Footer ─── */}
        <footer className="flex items-center justify-between border-t border-border py-8 font-mono text-xs text-muted">
          <span>© 2026 Aura · Open Source</span>
          <span>P0 · 部署链路验证</span>
        </footer>
      </div>
    </main>
  )
}
