'use client'

import { useMemo, useRef } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import * as THREE from 'three'

/**
 * Tech 页面 3D：4 层架构可视化。
 *
 * 视觉隐喻：4 块透明「层板」从下往上堆叠，
 * 数据流线从输入层向上穿过各层到输出层，
 * 执行路径节点沿对角线分布。
 *
 * 与 AgentGraph / MemoryNetwork 同一技术栈（R3F Canvas），
 * 但更轻量 —— 不需要 auto-state 循环，静态展示 + 鼠标视差。
 */

const C = {
  accent: '#7c5cff',
  listening: '#5cefff',
  speaking: '#ffb85c',
  memory: '#5cffb0',
  thinking: '#a07cff',
  health: '#ff7c9c',
}

interface LayerProps {
  label: string
  color: string
  y: number
}

const LAYERS: LayerProps[] = [
  { label: '工具与 MCP', color: C.health, y: 0 },
  { label: '陪伴运行时', color: C.speaking, y: 1 },
  { label: '双心智分工', color: C.thinking, y: 2 },
  { label: '可信个人模型', color: C.memory, y: 3 },
]

/** 单层半透明面板 */
function LayerPlane({ color, y }: LayerProps) {
  return (
    <mesh position={[0, y * -1.1, 0]} rotation={[-Math.PI / 2.2, 0, 0]}>
      <planeGeometry args={[3.2, 1.8]} />
      <meshStandardMaterial
        color={color}
        transparent
        opacity={0.55}
        roughness={0.15}
        metalness={0.4}
        side={THREE.DoubleSide}
      />
    </mesh>
  )
}

/** 层间垂直连接线 */
function DataFlow({ fromY, toY, color, dashed = false }: { fromY: number; toY: number; color: string; dashed?: boolean }) {
  const geo = useMemo(() => {
    const g = new THREE.BufferGeometry()
    g.setAttribute('position', new THREE.BufferAttribute(new Float32Array([1.2, fromY * -1.1, 0, 1.2, toY * -1.1, 0]), 3))
    return g
  }, [fromY, toY])
  return (
    <primitive object={new THREE.LineSegments(geo, new THREE.LineBasicMaterial({ color, transparent: true, opacity: dashed ? 0.15 : 0.35 }))} />
  )
}

/** 执行路径节点（沿左下→右上对角线） */
function FlowNode({ position, isStart = false, isEnd = false }: { position: [number, number, number]; isStart?: boolean; isEnd?: boolean }) {
  const size = isStart || isEnd ? 0.18 : 0.12
  return (
    <mesh position={position}>
      <sphereGeometry args={[size, 24, 24]} />
      <meshStandardMaterial
        color={C.accent}
        emissive={C.accent}
        emissiveIntensity={isStart || isEnd ? 0.25 : 0.6}
        roughness={0.2}
        metalness={0.35}
      />
      {!isStart && !isEnd && (
        <mesh position={[0, 0.04, 0.1]}>
          <sphereGeometry args={[0.03, 12, 12]} />
          <meshStandardMaterial color="#ffffff" emissive="#ffffff" emissiveIntensity={2} />
        </mesh>
      )}
    </mesh>
  )
}

function Scene() {
  const group = useRef<THREE.Group>(null)

  useFrame((state) => {
    if (group.current) {
      const { x, y } = state.mouse
      group.current.rotation.y = THREE.MathUtils.lerp(group.current.rotation.y, x * 0.22, 0.06)
      group.current.rotation.x = THREE.MathUtils.lerp(group.current.rotation.x, -y * 0.12, 0.06)
    }
  })

  return (
    <group ref={group} scale={0.9}>
      {/* 4 层堆叠 */}
      {LAYERS.map((l) => (
        <LayerPlane key={l.label} {...l} />
      ))}

      {/* 层间连接 */}
      <DataFlow fromY={0} toY={1} color={C.health} />
      <DataFlow fromY={1} toY={2} color={C.speaking} />
      <DataFlow fromY={2} toY={3} color={C.thinking} />

      {/* 执行路径节点（输入→路由→工具→返回→沉淀） */}
      <FlowNode position={[-1.6, 0.15, 0]} isStart />
      <FlowNode position={[-0.5, -0.95, 0]} />
      <FlowNode position={[0.5, -2.05, 0]} />
      <FlowNode position={[1.5, -3.15, 0]} />
      <FlowNode position={[2.3, -3.5, 0]} isEnd />

      {/* 光源 */}
      <pointLight position={[3, 2.5, 3]} intensity={0.7} color={C.accent} />
      <pointLight position={[-2.5, 1, 3]} intensity={0.35} color={C.listening} />
      <ambientLight intensity={0.28} />
    </group>
  )
}

export function TechArch() {
  return (
    <Canvas
      camera={{ position: [0, 0, 5], fov: 42 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <Scene />
    </Canvas>
  )
}
