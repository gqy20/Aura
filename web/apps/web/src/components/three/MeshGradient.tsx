'use client'

import { Canvas, useFrame } from '@react-three/fiber'
import { useMemo, useRef } from 'react'
import * as THREE from 'three'

/**
 * Mesh Gradient 背景
 *
 * 自写 fragment shader，比 @paper-design/shaders 轻。
 * 多色径向渐变 + 缓慢漂移，无外部资源。
 */

const vertexShader = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position, 1.0);
  }
`

const fragmentShader = /* glsl */ `
  precision highp float;
  varying vec2 vUv;
  uniform float uTime;
  uniform vec2 uResolution;

  // 伪随机
  float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
  }

  vec3 colorRamp(float t) {
    // Aura 紫蓝调色板
    vec3 c1 = vec3(0.486, 0.361, 1.000); // #7C5CFF accent
    vec3 c2 = vec3(0.180, 0.310, 0.780); // 深蓝
    vec3 c3 = vec3(0.090, 0.090, 0.110); // 背景深色
    vec3 c4 = vec3(0.380, 0.220, 0.860); // 紫色变体

    if (t < 0.33) return mix(c3, c2, t / 0.33);
    if (t < 0.66) return mix(c2, c4, (t - 0.33) / 0.33);
    return mix(c4, c1, (t - 0.66) / 0.34);
  }

  void main() {
    vec2 uv = vUv;
    vec2 p = (uv - 0.5) * 2.0;
    p.x *= uResolution.x / uResolution.y;

    float t = uTime * 0.04;

    // 3 个漂移的色心
    vec2 c1 = vec2(sin(t * 1.2) * 0.7, cos(t * 0.9) * 0.6);
    vec2 c2 = vec2(cos(t * 0.7 + 2.0) * 0.6, sin(t * 1.1 + 1.0) * 0.7);
    vec2 c3 = vec2(sin(t * 0.5 + 4.0) * 0.8, cos(t * 0.6 + 3.0) * 0.4);

    float d1 = length(p - c1);
    float d2 = length(p - c2) * 1.3;
    float d3 = length(p - c3) * 1.6;

    // 距离反比形成渐变
    float w1 = 1.0 / (d1 * d1 + 0.2);
    float w2 = 1.0 / (d2 * d2 + 0.3);
    float w3 = 1.0 / (d3 * d3 + 0.4);
    float wsum = w1 + w2 + w3;

    float h1 = w1 / wsum;
    float h2 = w2 / wsum;
    float h3 = w3 / wsum;

    vec3 col = colorRamp(h1) * 0.5
             + colorRamp(h2 + 0.33) * 0.3
             + colorRamp(h3 + 0.66) * 0.2;

    // 暗角
    float vignette = 1.0 - smoothstep(0.6, 1.4, length(p));
    col *= vignette * 0.9 + 0.1;

    // 微噪点
    col += (hash(uv * 1000.0) - 0.5) * 0.015;

    gl_FragColor = vec4(col, 1.0);
  }
`

function GradientPlane() {
  const matRef = useRef<THREE.ShaderMaterial>(null)
  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uResolution: { value: new THREE.Vector2(1, 1) },
    }),
    [],
  )

  useFrame((state) => {
    if (matRef.current) {
      matRef.current.uniforms.uTime.value = state.clock.elapsedTime
      const { width, height } = state.size
      matRef.current.uniforms.uResolution.value.set(width, height)
    }
  })

  return (
    <mesh>
      <planeGeometry args={[2, 2]} />
      <shaderMaterial
        ref={matRef}
        uniforms={uniforms}
        vertexShader={vertexShader}
        fragmentShader={fragmentShader}
        depthWrite={false}
        depthTest={false}
      />
    </mesh>
  )
}

export function MeshGradient() {
  return (
    <Canvas
      orthographic
      camera={{ position: [0, 0, 1], zoom: 1 }}
      gl={{ antialias: true, alpha: false }}
      dpr={[1, 2]}
      className="absolute inset-0 h-full w-full"
    >
      <GradientPlane />
    </Canvas>
  )
}
