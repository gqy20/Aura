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
    default: 'Aura — 与你同行的 AI 陪伴',
    template: '%s · Aura',
  },
  description:
    'Aura 是一款 Android 上的 AI 陪伴应用，把存在感、记忆和本地大模型装进你的口袋。',
  applicationName: 'Aura',
  authors: [{ name: 'Aura 团队' }],
  keywords: ['Aura', 'AI 陪伴', 'Android', 'Koog', 'LLM', '本地大模型', '记忆系统'],
  openGraph: {
    title: 'Aura — 与你同行的 AI 陪伴',
    description: '存在感、记忆与本地大模型，融于掌心。',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Aura — 与你同行的 AI 陪伴',
    description: '存在感、记忆与本地大模型，融于掌心。',
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
