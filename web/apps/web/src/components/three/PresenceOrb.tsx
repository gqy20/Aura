'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { Float, Icosahedron, MeshDistortMaterial, Trail } from '@react-three/drei'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as THREE from 'three'

/**
 * Presence 状态机 3D 主体
 *
 * 视觉：
 * - 中心：Icosahedron + MeshDistortMaterial，distort/speed/color 随状态变化
 * - 周围：6 个发光小卫星（轨道 + 自转），代表 6 种常见状态
 * - 状态切换时：主球 distort 增加、颜色切换、卫星发光强度脉冲
 * - 鼠标视差：整组旋转
 *
 * 状态（精简自 PresenceMode）：
 * - IDLE / LISTENING / THINKING / SPEAKING / REMEMBERING / TIRED
 */

type StateKey = 'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING' | 'REMEMBERING' | 'TIRED' | 'SLEEPING'

interface StateConfig {
  label: string
  color: string
  emissive: string
  distort: number
  speed: number
  description: string
}

const STATES: Record<StateKey, StateConfig> = {
  IDLE: {
    label: 'Idle',
    color: '#7c5cff',
    emissive: '#3a1cff',
    distort: 0.25,
    speed: 0.6,
    description: 'Quietly present, watching',
  },
  LISTENING: {
    label: 'Listening',
    color: '#5cefff',
    emissive: '#1c8fff',
    distort: 0.4,
    speed: 1.4,
    description: 'Attentive, focused on input',
  },
  THINKING: {
    label: 'Thinking',
    color: '#a07cff',
    emissive: '#5030ff',
    distort: 0.55,
    speed: 1.8,
    description: 'Reasoning over context',
  },
  SPEAKING: {
    label: 'Speaking',
    color: '#ffb85c',
    emissive: '#ff8030',
    distort: 0.5,
    speed: 2.0,
    description: 'Streaming the response',
  },
  REMEMBERING: {
    label: 'Remembering',
    color: '#5cffb0',
    emissive: '#1cff80',
    distort: 0.35,
    speed: 1.0,
    description: 'Recalling memory graph',
  },
  TIRED: {
    label: 'Tired',
    color: '#9090a8',
    emissive: '#404060',
    distort: 0.2,
    speed: 0.4,
    description: 'Low energy, dimmed',
  },
  SLEEPING: {
    label: 'Sleeping',
    color: '#404060',
    emissive: '#1a1a30',
    distort: 0.1,
    speed: 0.2,
    description: 'Dimmed to sleep',
  },
}

const STATE_ORDER: StateKey[] = [
  'IDLE',
  'LISTENING',
  'THINKING',
  'SPEAKING',
  'REMEMBERING',
  'TIRED',
]

function lerpColor(a: THREE.Color, b: THREE.Color, t: number) {
  a.r += (b.r - a.r) * t
  a.g += (b.g - a.g) * t
  a.b += (b.b - a.b) * t
}

function PresenceCore({ state }: { state: StateConfig }) {
  const meshRef = useRef<THREE.Mesh>(null)
  const colorObj = useRef(new THREE.Color(state.color))
  const emissiveObj = useRef(new THREE.Color(state.emissive))

  useFrame((_, delta) => {
    if (!meshRef.current) return
    const targetColor = new THREE.Color(state.color)
    const targetEmissive = new THREE.Color(state.emissive)
    lerpColor(colorObj.current, targetColor, 0.08)
    lerpColor(emissiveObj.current, targetEmissive, 0.08)
    const mat = meshRef.current.material as THREE.MeshStandardMaterial
    mat.color.copy(colorObj.current)
    mat.emissive.copy(emissiveObj.current)
  })

  return (
    <Icosahedron ref={meshRef} args={[1, 12]}>
      <MeshDistortMaterial
        color={state.color}
        emissive={state.emissive}
        emissiveIntensity={0.6}
        distort={state.distort}
        speed={state.speed}
        roughness={0.15}
        metalness={0.4}
      />
    </Icosahedron>
  )
}

function Satellite({
  angle,
  radius,
  color,
  speed,
  pulse,
}: {
  angle: number
  radius: number
  color: string
  speed: number
  pulse: number
}) {
  const ref = useRef<THREE.Mesh>(null)
  useFrame((state) => {
    if (!ref.current) return
    const t = state.clock.elapsedTime * speed
    const x = Math.cos(angle + t) * radius
    const y = Math.sin(t * 0.7) * 0.3
    const z = Math.sin(angle + t) * radius
    ref.current.position.set(x, y, z)
    const s = 0.06 + Math.sin(t * 2 + pulse) * 0.02
    ref.current.scale.setScalar(s)
  })
  return (
    <Trail width={0.4} length={6} color={color} attenuation={(w) => w * w}>
      <mesh ref={ref}>
        <sphereGeometry args={[1, 16, 16]} />
        <meshBasicMaterial color={color} />
      </mesh>
    </Trail>
  )
}

function PresenceScene({ stateKey }: { stateKey: StateKey }) {
  const group = useRef<THREE.Group>(null)
  const state = STATES[stateKey]

  useFrame((s) => {
    if (group.current) {
      const { x, y } = s.mouse
      group.current.rotation.y = THREE.MathUtils.lerp(
        group.current.rotation.y,
        x * 0.4,
        0.05,
      )
      group.current.rotation.x = THREE.MathUtils.lerp(
        group.current.rotation.x,
        -y * 0.25,
        0.05,
      )
    }
  })

  // 6 个卫星，6 种颜色
  const satellites = useMemo(
    () => [
      { color: '#7c5cff', angle: 0, speed: 0.5 },
      { color: '#5cefff', angle: Math.PI / 3, speed: 0.6 },
      { color: '#a07cff', angle: (Math.PI * 2) / 3, speed: 0.55 },
      { color: '#ffb85c', angle: Math.PI, speed: 0.45 },
      { color: '#5cffb0', angle: (Math.PI * 4) / 3, speed: 0.5 },
      { color: '#9090a8', angle: (Math.PI * 5) / 3, speed: 0.4 },
    ],
    [],
  )

  return (
    <group ref={group} scale={0.85}>
      <Float speed={1.2} rotationIntensity={0.2} floatIntensity={0.5}>
        <PresenceCore state={state} />
      </Float>

      {satellites.map((sat, i) => (
        <Satellite
          key={i}
          angle={sat.angle}
          radius={2.2}
          color={sat.color}
          speed={sat.speed}
          pulse={i}
        />
      ))}

      {/* 远处柔光 */}
      <pointLight position={[3, 2, 3]} intensity={1.2} color={state.color} />
      <pointLight position={[-3, -2, 2]} intensity={0.6} color="#7c5cff" />
    </group>
  )
}

interface PresenceOrbProps {
  stateKey: StateKey
}

/**
 * 对外包装：Canvas + 状态驱动
 */
export function PresenceOrb({ stateKey }: PresenceOrbProps) {
  return (
    <Canvas
      camera={{ position: [0, 0, 5.5], fov: 38 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <ambientLight intensity={0.3} />
      <directionalLight position={[2, 5, 5]} intensity={0.7} />
      <PresenceScene stateKey={stateKey} />
    </Canvas>
  )
}

/**
 * 状态自动轮播 hook
 * - 每 3.2s 切一个状态
 * - 组件卸载时自动清理
 */
export function usePresenceAutoCycle(intervalMs = 3200) {
  const [index, setIndex] = useState(0)
  useEffect(() => {
    const id = setInterval(() => {
      setIndex((i) => (i + 1) % STATE_ORDER.length)
    }, intervalMs)
    return () => clearInterval(id)
  }, [intervalMs])
  return { stateKey: STATE_ORDER[index], index }
}

export { STATES, STATE_ORDER, type StateKey }
