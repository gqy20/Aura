'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { Float, Sphere } from '@react-three/drei'
import { useMemo, useRef } from 'react'
import * as THREE from 'three'

/**
 * Agent Graph 3D 主体
 *
 * 视觉：
 * - 中心：Agent Core（白色发光 icosahedron）
 * - 9 个工具节点（4 类颜色）：
 *   - Memory（紫）：SearchMemory / SearchRecords / SearchSummaries
 *   - Context（青）：GetCurrentTime / GetRecentInteractionContext / GetUserContextSettings
 *   - Device（橙）：GetDeviceStatus / GetWeather
 *   - Action（绿）：CreateLocalReminder
 * - 3 个 LLM Provider 节点（深色，背景位置）：
 *   - Cloud Qwen / OpenAI / Local MNN
 * - LineSegments：Core → Tools（实），Core → Providers（虚）
 * - 鼠标视差
 */

interface ToolNode {
  position: [number, number, number]
  name: string
  category: 'memory' | 'context' | 'device' | 'action'
  categoryColor: string
}

const CATEGORY_COLORS = {
  memory: '#7c5cff',
  context: '#5cefff',
  device: '#ffb85c',
  action: '#5cffb0',
} as const

const TOOL_NODES: ToolNode[] = [
  // Memory 三角
  { position: [-2.0, 0.4, 0], name: 'SearchMemory', category: 'memory', categoryColor: CATEGORY_COLORS.memory },
  { position: [-1.7, -0.7, 0.4], name: 'SearchRecords', category: 'memory', categoryColor: CATEGORY_COLORS.memory },
  { position: [-2.2, -0.1, -0.5], name: 'SearchSummaries', category: 'memory', categoryColor: CATEGORY_COLORS.memory },
  // Context 三角
  { position: [2.0, 0.5, 0.2], name: 'GetCurrentTime', category: 'context', categoryColor: CATEGORY_COLORS.context },
  { position: [1.8, -0.6, -0.3], name: 'GetRecentContext', category: 'context', categoryColor: CATEGORY_COLORS.context },
  { position: [2.2, -0.1, 0.5], name: 'GetUserContext', category: 'context', categoryColor: CATEGORY_COLORS.context },
  // Device
  { position: [0.2, 1.9, 0.2], name: 'GetDeviceStatus', category: 'device', categoryColor: CATEGORY_COLORS.device },
  { position: [0.4, 1.7, -0.5], name: 'GetWeather', category: 'device', categoryColor: CATEGORY_COLORS.device },
  // Action
  { position: [0, -2.0, 0.3], name: 'CreateReminder', category: 'action', categoryColor: CATEGORY_COLORS.action },
]

const PROVIDER_NODES: { position: [number, number, number]; name: string; color: string }[] = [
  { position: [-1.4, 0, -2.5], name: 'Cloud Qwen', color: '#a07cff' },
  { position: [0, 0, -2.8], name: 'OpenAI', color: '#9090a8' },
  { position: [1.4, 0, -2.5], name: 'Local MNN', color: '#5cffb0' },
]

function ToolNodeMesh({ position, color }: { position: [number, number, number]; color: string }) {
  const ref = useRef<THREE.Mesh>(null)
  useFrame((s) => {
    if (!ref.current) return
    const t = s.clock.elapsedTime
    const scale = 0.12 + Math.sin(t * 1.8) * 0.015
    ref.current.scale.setScalar(scale)
  })
  return (
    <Sphere ref={ref} args={[1, 24, 24]} position={position}>
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={0.7}
        roughness={0.2}
        metalness={0.3}
      />
    </Sphere>
  )
}

function ProviderNodeMesh({ position, color }: { position: [number, number, number]; color: string }) {
  return (
    <mesh position={position}>
      <octahedronGeometry args={[0.18, 1]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={0.4}
        roughness={0.4}
        metalness={0.6}
        wireframe
      />
    </mesh>
  )
}

function Connection({
  start,
  end,
  color,
  opacity = 0.4,
  dashed = false,
}: {
  start: [number, number, number]
  end: [number, number, number]
  color: string
  opacity?: number
  dashed?: boolean
}) {
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
    <lineSegments geometry={geometry}>
      <lineBasicMaterial
        color={color}
        transparent
        opacity={opacity}
        {...(dashed ? { linewidth: 1 } : {})}
      />
    </lineSegments>
  )
}

function AgentCore() {
  const group = useRef<THREE.Group>(null)
  const inner = useRef<THREE.Mesh>(null)

  useFrame((s) => {
    if (group.current) {
      const { x, y } = s.mouse
      group.current.rotation.y = THREE.MathUtils.lerp(
        group.current.rotation.y,
        x * 0.35,
        0.05,
      )
      group.current.rotation.x = THREE.MathUtils.lerp(
        group.current.rotation.x,
        -y * 0.22,
        0.05,
      )
    }
    if (inner.current) {
      inner.current.rotation.y += 0.005
      inner.current.rotation.x += 0.003
    }
  })

  return (
    <group ref={group}>
      <Float speed={1.2} rotationIntensity={0.2} floatIntensity={0.4}>
        {/* Core：白色发光 icosahedron */}
        <mesh ref={inner}>
          <icosahedronGeometry args={[0.42, 2]} />
          <meshStandardMaterial
            color="#ffffff"
            emissive="#ffffff"
            emissiveIntensity={0.6}
            roughness={0.25}
            metalness={0.5}
          />
        </mesh>

        {/* 内部辉光层 */}
        <mesh>
          <sphereGeometry args={[0.65, 32, 32]} />
          <meshBasicMaterial color="#7c5cff" transparent opacity={0.15} />
        </mesh>
      </Float>
    </group>
  )
}

function AgentScene() {
  return (
    <group scale={0.9}>
      <AgentCore />

      {/* 工具节点 + Core 连线（实线） */}
      {TOOL_NODES.map((tool, i) => (
        <group key={tool.name}>
          <ToolNodeMesh position={tool.position} color={tool.categoryColor} />
          <Connection
            start={[0, 0, 0]}
            end={tool.position}
            color={tool.categoryColor}
            opacity={0.4}
          />
        </group>
      ))}

      {/* Provider 节点 + Core 连线（虚线感：低 opacity） */}
      {PROVIDER_NODES.map((p, i) => (
        <group key={p.name}>
          <ProviderNodeMesh position={p.position} color={p.color} />
          <Connection
            start={[0, 0, 0]}
            end={p.position}
            color={p.color}
            opacity={0.25}
            dashed
          />
        </group>
      ))}

      <pointLight position={[3, 3, 3]} intensity={0.8} color="#7c5cff" />
      <pointLight position={[-3, -2, 2]} intensity={0.5} color="#5cefff" />
      <ambientLight intensity={0.35} />
    </group>
  )
}

export function AgentGraph() {
  return (
    <Canvas
      camera={{ position: [0, 0, 5.5], fov: 38 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <AgentScene />
    </Canvas>
  )
}
