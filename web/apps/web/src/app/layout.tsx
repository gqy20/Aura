import type { Metadata, Viewport } from 'next'
import { Inter, JetBrains_Mono } from 'next/font/google'
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

export const metadata: Metadata = {
  title: {
    default: 'Aura — AI companion that lives with you',
    template: '%s · Aura',
  },
  description:
    'Aura is an open-source AI companion for Android — presence, memory, and local LLM, designed to live alongside you.',
  applicationName: 'Aura',
  authors: [{ name: 'Aura Project' }],
  keywords: ['AI', 'companion', 'Android', 'open-source', 'Koog', 'LLM'],
  openGraph: {
    title: 'Aura — AI companion that lives with you',
    description: 'Presence, memory, and local LLM in your pocket.',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Aura — AI companion that lives with you',
    description: 'Presence, memory, and local LLM in your pocket.',
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
      lang="en"
      className={`${inter.variable} ${jetbrainsMono.variable}`}
      suppressHydrationWarning
    >
      <body className="min-h-screen bg-background font-sans text-foreground antialiased">
        {children}
      </body>
    </html>
  )
}
