import type { Metadata, Viewport } from 'next'
import { Inter, JetBrains_Mono, Instrument_Serif } from 'next/font/google'
import './globals.css'

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-sans',
})

const jetbrainsMono = JetBrains_Mono({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-mono',
})

const instrumentSerif = Instrument_Serif({
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-serif',
  weight: '400',
  style: ['normal', 'italic'],
})

export const metadata: Metadata = {
  title: {
    default: 'Aura — 长期认识你的 AI 陪伴',
    template: '%s · Aura',
  },
  description:
    'Aura 是一款 Android AI 陪伴应用，把对外办事、对内理解和长期陪伴组织成一个持续运行的体验。',
  applicationName: 'Aura',
  authors: [{ name: 'Aura 团队' }],
  keywords: ['Aura', 'AI 陪伴', 'Android', 'Koog', 'LLM', '本地大模型', '记忆系统'],
  openGraph: {
    title: 'Aura — 长期认识你的 AI 陪伴',
    description: '让记忆、洞察与生活建议在手机里持续发生。',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Aura — 长期认识你的 AI 陪伴',
    description: '让记忆、洞察与生活建议在手机里持续发生。',
  },
  icons: {
    icon: '/favicon.svg',
    shortcut: '/favicon.svg',
    apple: '/favicon.svg',
  },
}

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#ffffff' },
    { media: '(prefers-color-scheme: dark)', color: '#08090a' },
  ],
  width: 'device-width',
  initialScale: 1,
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="zh-CN"
      className={`${inter.variable} ${jetbrainsMono.variable} ${instrumentSerif.variable}`}
      suppressHydrationWarning
    >
      <body className="min-h-screen bg-background font-sans text-foreground antialiased">
        {children}
      </body>
    </html>
  )
}
