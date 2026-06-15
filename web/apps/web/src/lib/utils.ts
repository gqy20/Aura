/**
 * 通用工具函数
 */

/**
 * 合并 className，过滤 falsy 值。
 * 替代 shadcn/ui 的 cn() 实现，避免过早引入依赖。
 */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}

/**
 * 安全的 JSON.parse，带类型守卫
 */
export function safeJsonParse<T>(json: string, fallback: T): T {
  try {
    return JSON.parse(json) as T
  } catch {
    return fallback
  }
}

/**
 * 格式化大数字（1234 → 1.2k）
 */
export function formatCompact(n: number): string {
  if (n < 1000) return String(n)
  if (n < 1_000_000) return `${(n / 1000).toFixed(1).replace(/\.0$/, '')}k`
  if (n < 1_000_000_000) return `${(n / 1_000_000).toFixed(1).replace(/\.0$/, '')}M`
  return `${(n / 1_000_000_000).toFixed(1).replace(/\.0$/, '')}B`
}
