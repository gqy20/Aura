'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { Float, MeshDistortMaterial, RoundedBox } from '@react-three/drei'
import { useRef } from 'react'
import * as THREE from 'three'

/**
 * Hero 3D 主视觉
 *
 * 修复 P3 致命问题（屏幕不可见）：
 * - RoundedBox metalness 0.8 → 0.3（不再变全黑）
 * - 屏幕 emissive 强度 0.4 → 1.6，颜色 #3a1cff → #7c5cff
 * - 加多色补光（紫 + 青 + 暖白）打破单光源死黑
 * - 屏幕 z 位置 0.065 → 0.07，确保浮于 RoundedBox 前
 * - 缩放 0.7 → 0.85 + 调整 camera fov 32 → 36 让整体更饱满
 * - 内容抽象块（“通知” + “聊天”）改用更亮的 accent 色 + 提高不透明度
 *
 * 色值与 globals.css @theme 的对照（Three.js 无法引用 CSS var，需手动同步）：
 *   accent    → --color-accent      #7c5cff  (屏幕 + 点光)
 *   listening → --aura-listening    #5cefff  (点光补色)
 */

function HeroPhone() {
  const group = useRef<THREE.Group>(null)
  const inner = useRef<THREE.Mesh>(null)

  useFrame((state) => {
    if (group.current) {
      // 鼠标视差
      const { x, y } = state.mouse
      group.current.rotation.y = THREE.MathUtils.lerp(
        group.current.rotation.y,
        x * 0.25,
        0.05,
      )
      group.current.rotation.x = THREE.MathUtils.lerp(
        group.current.rotation.x,
        -y * 0.15,
        0.05,
      )
    }
  })

  return (
    <group ref={group} scale={0.85}>
      <Float speed={1.4} rotationIntensity={0.3} floatIntensity={0.6}>
        {/* 手机外框 — 降低 metalness 让它不再全黑 */}
        <RoundedBox args={[1.2, 2.4, 0.12]} radius={0.18} smoothness={6}>
          <meshStandardMaterial
            color="#2a2a32"
            metalness={0.3}
            roughness={0.55}
            emissive="#1a1a25"
            emissiveIntensity={0.4}
          />
        </RoundedBox>

        {/* 手机屏幕（发光核心）— emissive 拉满 + 颜色提亮 */}
        <mesh ref={inner} position={[0, 0, 0.07]}>
          <planeGeometry args={[1.05, 2.25]} />
          <MeshDistortMaterial
            color="#7c5cff"
            distort={0.4}
            speed={1.8}
            roughness={0.1}
            metalness={0}
            emissive="#7c5cff"
            emissiveIntensity={1.6}
            toneMapped={false}
          />
        </mesh>

        {/* 顶部"通知"抽象块 */}
        <mesh position={[0, 0.55, 0.072]}>
          <planeGeometry args={[0.85, 0.35]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.15} />
        </mesh>
        <mesh position={[0, 0.55, 0.073]}>
          <planeGeometry args={[0.6, 0.08]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.35} />
        </mesh>

        {/* 中部"聊天"块（最大） */}
        <mesh position={[0, 0.05, 0.072]}>
          <planeGeometry args={[0.85, 0.7]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.1} />
        </mesh>
        <mesh position={[-0.15, 0.15, 0.073]}>
          <planeGeometry args={[0.45, 0.12]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.45} />
        </mesh>
        <mesh position={[0.1, -0.05, 0.073]}>
          <planeGeometry args={[0.55, 0.12]} />
          <meshBasicMaterial color="#5cefff" transparent opacity={0.5} />
        </mesh>
        <mesh position={[-0.1, -0.25, 0.073]}>
          <planeGeometry args={[0.6, 0.12]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.3} />
        </mesh>

        {/* 底部"输入栏" */}
        <mesh position={[0, -0.6, 0.072]}>
          <planeGeometry args={[0.85, 0.3]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.08} />
        </mesh>
        <mesh position={[0, -0.6, 0.073]}>
          <planeGeometry args={[0.7, 0.05]} />
          <meshBasicMaterial color="#ffffff" transparent opacity={0.4} />
        </mesh>
      </Float>
    </group>
  )
}

export function PhoneOrb() {
  return (
    <Canvas
      camera={{ position: [0, 0, 5], fov: 36 }}
      gl={{
        antialias: true,
        alpha: true,
        // 强制开启色调映射，让 emissive 真正亮起来
        toneMapping: THREE.ACESFilmicToneMapping,
        toneMappingExposure: 1.2,
      }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      {/* 多色补光 — 紫 + 青 + 暖白，让任何角度都有高光 */}
      <ambientLight intensity={0.6} color="#ffffff" />
      <pointLight position={[3, 3, 4]} intensity={2.0} color="#7c5cff" />
      <pointLight position={[-3, -2, 3]} intensity={1.2} color="#5cefff" />
      <pointLight position={[0, 0, 4]} intensity={1.5} color="#ffffff" />
      <directionalLight position={[2, 5, 5]} intensity={1.0} color="#ffffff" />
      <HeroPhone />
    </Canvas>
  )
}
