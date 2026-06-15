'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { Float, MeshDistortMaterial, RoundedBox } from '@react-three/drei'
import { useRef } from 'react'
import * as THREE from 'three'

/**
 * Hero 3D 主视觉
 *
 * 用 RoundedBox（手机形态）+ MeshDistortMaterial（流体感）
 * 抽象表达"陪伴"，不依赖任何 .glb 资源
 * Float 让它缓慢浮动
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
    if (inner.current) {
      inner.current.rotation.z += 0.002
    }
  })

  return (
    <group ref={group} scale={0.7}>
      <Float speed={1.4} rotationIntensity={0.3} floatIntensity={0.6}>
        {/* 手机外框 */}
        <RoundedBox args={[1.2, 2.4, 0.12]} radius={0.18} smoothness={6}>
          <meshStandardMaterial
            color="#1a1a1f"
            metalness={0.8}
            roughness={0.25}
          />
        </RoundedBox>

        {/* 手机屏幕（发光） */}
        <mesh ref={inner} position={[0, 0, 0.065]}>
          <planeGeometry args={[1.05, 2.25]} />
          <MeshDistortMaterial
            color="#7c5cff"
            distort={0.35}
            speed={1.8}
            roughness={0.1}
            metalness={0.2}
            emissive="#3a1cff"
            emissiveIntensity={0.4}
          />
        </mesh>

        {/* 手机内屏 - 内容抽象块 */}
        <mesh position={[0, 0.4, 0.066]}>
          <planeGeometry args={[0.85, 0.45]} />
          <meshBasicMaterial color="#f7f8f8" transparent opacity={0.08} />
        </mesh>
        <mesh position={[0, -0.3, 0.066]}>
          <planeGeometry args={[0.85, 0.55]} />
          <meshBasicMaterial color="#f7f8f8" transparent opacity={0.05} />
        </mesh>
      </Float>
    </group>
  )
}

export function PhoneOrb() {
  return (
    <Canvas
      camera={{ position: [0, 0, 5], fov: 32 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <ambientLight intensity={0.4} />
      <pointLight position={[3, 3, 3]} intensity={1.2} color="#7c5cff" />
      <pointLight position={[-3, -2, 2]} intensity={0.6} color="#4a3aff" />
      <directionalLight position={[2, 5, 5]} intensity={0.8} />
      <HeroPhone />
    </Canvas>
  )
}
