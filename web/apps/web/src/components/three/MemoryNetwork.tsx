'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { Float } from '@react-three/drei'
import { useMemo, useRef } from 'react'
import * as THREE from 'three'

/**
 * Memory 网络 3D 主体
 *
 * 视觉：
 * - 中心 hub（白色发光，Memory 总入口）
 * - 6 个一级节点（3 MemoryType + 3 SummaryType），按 type 颜色区分
 * - 二级子节点（演示数据），半径更小
 * - LineSegments 连接中心 → 一级 → 二级
 * - 鼠标视差
 *
 * 色值与 globals.css @theme 的对照（Three.js 无法引用 CSS var，需手动同步）：
 *   accent    → --color-accent      #7c5cff  (FACT)
 *   memory    → --aura-memory       #5cffb0  (EPISODE)
 *   speaking  → --aura-speaking     #ffb85c  (PROCEDURAL)
 *   listening → --aura-listening    #5cefff  (DAILY)
 *   thinking  → --aura-thinking     #a07cff  (TOPIC)
 *   health    → --aura-health       #ff7c9c  (RELATIONSHIP)
 */

interface NodeSpec {
  position: [number, number, number]
  color: string
  size: number
  label: string
  group: 'memory' | 'summary'
}

const NODES: NodeSpec[] = [
  // MemoryType 三类（FACT / EPISODE / PROCEDURAL）
  { position: [-1.8, 0.6, 0], color: '#7c5cff', size: 0.18, label: 'FACT', group: 'memory' },
  { position: [1.6, 0.8, -0.4], color: '#5cffb0', size: 0.18, label: 'EPISODE', group: 'memory' },
  { position: [0.4, -1.4, 0.2], color: '#ffb85c', size: 0.18, label: 'PROCEDURAL', group: 'memory' },

  // SummaryType 5 类（DAILY / SESSION / TOPIC / PROJECT / RELATIONSHIP）
  { position: [-1.2, -0.8, 0.5], color: '#5cefff', size: 0.14, label: 'DAILY', group: 'summary' },
  { position: [1.8, -0.2, 0.6], color: '#a07cff', size: 0.14, label: 'TOPIC', group: 'summary' },
  { position: [-0.4, 1.6, -0.3], color: '#ff7c9c', size: 0.14, label: 'RELATIONSHIP', group: 'summary' },
]

// 二级子节点（演示数据）— 围绕一级节点
const SUB_NODES: { anchor: number; offset: [number, number, number]; color: string }[] = [
  { anchor: 0, offset: [0.3, 0.2, 0.1], color: '#7c5cff' },
  { anchor: 0, offset: [-0.3, -0.1, 0.2], color: '#7c5cff' },
  { anchor: 1, offset: [-0.3, 0.2, 0.1], color: '#5cffb0' },
  { anchor: 1, offset: [0.2, -0.2, -0.2], color: '#5cffb0' },
  { anchor: 2, offset: [0.2, 0.3, 0.0], color: '#ffb85c' },
  { anchor: 3, offset: [0.2, 0.1, 0.1], color: '#5cefff' },
  { anchor: 4, offset: [-0.2, 0.2, 0.0], color: '#a07cff' },
  { anchor: 5, offset: [0.2, -0.2, 0.1], color: '#ff7c9c' },
]

function Node({ position, color, size, pulse = 0 }: { position: [number, number, number]; color: string; size: number; pulse?: number }) {
  const ref = useRef<THREE.Mesh>(null)
  useFrame((state) => {
    if (!ref.current) return
    const t = state.clock.elapsedTime
    const s = size * (1 + Math.sin(t * 1.5 + pulse) * 0.1)
    ref.current.scale.setScalar(s)
  })
  return (
    <mesh ref={ref} position={position}>
      <sphereGeometry args={[1, 32, 32]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={0.8}
        roughness={0.2}
        metalness={0.1}
      />
    </mesh>
  )
}

function Connection({
  start,
  end,
  color,
  opacity = 0.4,
}: {
  start: [number, number, number]
  end: [number, number, number]
  color: string
  opacity?: number
}) {
  const ref = useRef<THREE.LineSegments>(null)
  const geometry = useMemo(() => {
    const g = new THREE.BufferGeometry()
    const positions = new Float32Array([
      start[0], start[1], start[2],
      end[0], end[1], end[2],
    ])
    g.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    return g
  }, [start, end])

  return (
    <lineSegments ref={ref} geometry={geometry}>
      <lineBasicMaterial color={color} transparent opacity={opacity} />
    </lineSegments>
  )
}

function MemoryScene() {
  const group = useRef<THREE.Group>(null)
  const hub = useRef<THREE.Mesh>(null)

  useFrame((s) => {
    if (group.current) {
      const { x, y } = s.mouse
      group.current.rotation.y = THREE.MathUtils.lerp(
        group.current.rotation.y,
        x * 0.3,
        0.05,
      )
      group.current.rotation.x = THREE.MathUtils.lerp(
        group.current.rotation.x,
        -y * 0.2,
        0.05,
      )
    }
    if (hub.current) {
      hub.current.rotation.y += 0.003
    }
  })

  return (
    <group ref={group} scale={0.9}>
      <Float speed={1.0} rotationIntensity={0.15} floatIntensity={0.3}>
        {/* Hub：Memory 总入口 */}
        <mesh ref={hub}>
          <icosahedronGeometry args={[0.32, 2]} />
          <meshStandardMaterial
            color="#ffffff"
            emissive="#ffffff"
            emissiveIntensity={0.5}
            roughness={0.3}
            metalness={0.5}
          />
        </mesh>

        {/* Hub 内层辉光 */}
        <mesh>
          <sphereGeometry args={[0.5, 32, 32]} />
          <meshBasicMaterial color="#7c5cff" transparent opacity={0.12} />
        </mesh>
      </Float>

      {/* 一级节点 + 到 Hub 的连线 */}
      {NODES.map((node, i) => (
        <group key={i}>
          <Node position={node.position} color={node.color} size={node.size} pulse={i} />
          <Connection start={[0, 0, 0]} end={node.position} color={node.color} opacity={0.35} />
        </group>
      ))}

      {/* 二级子节点 + 到一级节点的连线 */}
      {SUB_NODES.map((sub, i) => {
        const anchor = NODES[sub.anchor]
        const subPos: [number, number, number] = [
          anchor.position[0] + sub.offset[0],
          anchor.position[1] + sub.offset[1],
          anchor.position[2] + sub.offset[2],
        ]
        return (
          <group key={`sub-${i}`}>
            <Node position={subPos} color={sub.color} size={0.07} pulse={i + 10} />
            <Connection
              start={anchor.position}
              end={subPos}
              color={sub.color}
              opacity={0.2}
            />
          </group>
        )
      })}

      {/* 灯光 */}
      <pointLight position={[3, 2, 3]} intensity={1.0} color="#7c5cff" />
      <pointLight position={[-3, -2, 2]} intensity={0.5} color="#5cefff" />
      <ambientLight intensity={0.3} />
    </group>
  )
}

export function MemoryNetwork() {
  return (
    <Canvas
      camera={{ position: [0, 0, 5], fov: 40 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <MemoryScene />
    </Canvas>
  )
}
