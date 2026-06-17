import type { SVGProps } from 'react'

/**
 * Aura Logo — 不均匀渐变环 + 双色月牙
 * 纯 SVG，无依赖，任意尺寸缩放
 */
export function AuraLogo({ size = 28, ...props }: { size?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 200 200"
      xmlns="http://www.w3.org/2000/svg"
      aria-label="Aura"
      role="img"
      {...props}
    >
      <defs>
        <mask id="aura-logo-a">
          <circle cx="94" cy="100" r="30" fill="white" />
          <circle cx="114" cy="116" r="38" fill="black" />
        </mask>
        <mask id="aura-logo-y">
          <circle cx="114" cy="116" r="38" fill="white" />
          <circle cx="94" cy="100" r="30" fill="black" />
        </mask>
      </defs>
      {/* 不均匀渐变环：顺时针，右下最细(10)→左上最粗(30) */}
      <path d="M 178 100 A 78 78 0 0 1 154.8 155.2" stroke="#7c5cff" strokeWidth="10" strokeLinecap="butt" fill="none" opacity=".50" />
      <path d="M 154.8 155.2 A 78 78 0 0 1 100 178" stroke="#7c5cff" strokeWidth="14" strokeLinecap="butt" fill="none" opacity=".60" />
      <path d="M 100 178 A 78 78 0 0 1 45.2 155.2"  stroke="#7c5cff" strokeWidth="20" strokeLinecap="butt" fill="none" opacity=".70" />
      <path d="M 45.2 155.2 A 78 78 0 0 1 22 100"   stroke="#7c5cff" strokeWidth="26" strokeLinecap="butt" fill="none" opacity=".78" />
      <path d="M 22 100 A 78 78 0 0 1 45.2 44.8"    stroke="#7c5cff" strokeWidth="30" strokeLinecap="butt" fill="none" opacity=".85" />
      <path d="M 45.2 44.8 A 78 78 0 0 1 100 22"    stroke="#7c5cff" strokeWidth="26" strokeLinecap="butt" fill="none" opacity=".78" />
      <path d="M 100 22 A 78 78 0 0 1 154.8 44.8"   stroke="#7c5cff" strokeWidth="18" strokeLinecap="butt" fill="none" opacity=".62" />
      <path d="M 154.8 44.8 A 78 78 0 0 1 178 100"  stroke="#7c5cff" strokeWidth="13" strokeLinecap="butt" fill="none" opacity=".52" />
      {/* Aura 紫月牙 */}
      <circle cx="94" cy="100" r="30" fill="#7c5cff" opacity=".88" mask="url(#aura-logo-a)" />
      {/* 你 琥珀金新月 */}
      <circle cx="114" cy="116" r="38" fill="#C9A96E" opacity=".58" mask="url(#aura-logo-y)" />
    </svg>
  )
}
