import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  // 生产环境启用更激进的图像优化
  images: {
    formats: ['image/avif', 'image/webp'],
  },
}

export default nextConfig
