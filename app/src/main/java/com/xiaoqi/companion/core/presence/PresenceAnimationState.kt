package com.xiaoqi.companion.core.presence

data class PresenceAnimationState(
    val pulseDurationMillis: Int,
    val breathDurationMillis: Int,
    val shimmerDurationMillis: Int,
    val tapScaleTarget: Float,
    val orbitParticleCount: Int,
    val orbitRadiusScale: Float,
    val petFrameDurationScale: Float,
    val haloBoost: Float,
    val breathAmplitude: Float,
    val shimmerAmplitude: Float,
    val pulseAmplitude: Float,
    val ringAlpha: Float,
    val glowAlpha: Float,
    val sparkAlpha: Float,
)

fun PresenceUiState.animationState(): PresenceAnimationState {
    val base = when (mode) {
        PresenceMode.THINKING -> PresenceAnimationState(
            pulseDurationMillis = 1800,
            breathDurationMillis = 2400,
            shimmerDurationMillis = 1600,
            tapScaleTarget = 0.95f,
            orbitParticleCount = 6,
            orbitRadiusScale = 1.04f,
            petFrameDurationScale = 0.92f,
            haloBoost = 0.12f,
            breathAmplitude = 0.018f,
            shimmerAmplitude = 0.016f,
            pulseAmplitude = 0.022f,
            ringAlpha = 0.20f,
            glowAlpha = 0.34f,
            sparkAlpha = 0.34f,
        )
        PresenceMode.SPEAKING -> PresenceAnimationState(
            pulseDurationMillis = 1700,
            breathDurationMillis = 2100,
            shimmerDurationMillis = 1500,
            tapScaleTarget = 0.95f,
            orbitParticleCount = 4,
            orbitRadiusScale = 1.02f,
            petFrameDurationScale = 0.84f,
            haloBoost = 0.08f,
            breathAmplitude = 0.016f,
            shimmerAmplitude = 0.014f,
            pulseAmplitude = 0.018f,
            ringAlpha = 0.18f,
            glowAlpha = 0.30f,
            sparkAlpha = 0.30f,
        )
        PresenceMode.SEARCHING -> PresenceAnimationState(
            pulseDurationMillis = 1700,
            breathDurationMillis = 2350,
            shimmerDurationMillis = 1450,
            tapScaleTarget = 0.95f,
            orbitParticleCount = 7,
            orbitRadiusScale = 1.08f,
            petFrameDurationScale = 0.82f,
            haloBoost = 0.16f,
            breathAmplitude = 0.017f,
            shimmerAmplitude = 0.017f,
            pulseAmplitude = 0.024f,
            ringAlpha = 0.21f,
            glowAlpha = 0.36f,
            sparkAlpha = 0.38f,
        )
        PresenceMode.REMEMBERING -> PresenceAnimationState(
            pulseDurationMillis = 2200,
            breathDurationMillis = 2500,
            shimmerDurationMillis = 1750,
            tapScaleTarget = 0.95f,
            orbitParticleCount = 6,
            orbitRadiusScale = 1.06f,
            petFrameDurationScale = 0.95f,
            haloBoost = 0.14f,
            breathAmplitude = 0.015f,
            shimmerAmplitude = 0.015f,
            pulseAmplitude = 0.020f,
            ringAlpha = 0.19f,
            glowAlpha = 0.32f,
            sparkAlpha = 0.32f,
        )
        PresenceMode.HAPPY -> PresenceAnimationState(
            pulseDurationMillis = 2300,
            breathDurationMillis = 2400,
            shimmerDurationMillis = 1700,
            tapScaleTarget = 0.94f,
            orbitParticleCount = 4,
            orbitRadiusScale = 1.0f,
            petFrameDurationScale = 0.9f,
            haloBoost = 0.1f,
            breathAmplitude = 0.014f,
            shimmerAmplitude = 0.014f,
            pulseAmplitude = 0.018f,
            ringAlpha = 0.18f,
            glowAlpha = 0.30f,
            sparkAlpha = 0.28f,
        )
        PresenceMode.SAD, PresenceMode.TIRED, PresenceMode.SLEEPING -> PresenceAnimationState(
            pulseDurationMillis = 3000,
            breathDurationMillis = 3200,
            shimmerDurationMillis = 2300,
            tapScaleTarget = 0.96f,
            orbitParticleCount = 2,
            orbitRadiusScale = 0.94f,
            petFrameDurationScale = 1.15f,
            haloBoost = 0.02f,
            breathAmplitude = 0.010f,
            shimmerAmplitude = 0.010f,
            pulseAmplitude = 0.014f,
            ringAlpha = 0.12f,
            glowAlpha = 0.22f,
            sparkAlpha = 0.18f,
        )
        PresenceMode.ERROR -> PresenceAnimationState(
            pulseDurationMillis = 1500,
            breathDurationMillis = 2200,
            shimmerDurationMillis = 1400,
            tapScaleTarget = 0.95f,
            orbitParticleCount = 3,
            orbitRadiusScale = 0.98f,
            petFrameDurationScale = 0.88f,
            haloBoost = 0.2f,
            breathAmplitude = 0.017f,
            shimmerAmplitude = 0.017f,
            pulseAmplitude = 0.028f,
            ringAlpha = 0.24f,
            glowAlpha = 0.38f,
            sparkAlpha = 0.40f,
        )
        PresenceMode.LISTENING, PresenceMode.IDLE -> PresenceAnimationState(
            pulseDurationMillis = 2600,
            breathDurationMillis = 2600,
            shimmerDurationMillis = 1900,
            tapScaleTarget = 0.94f,
            orbitParticleCount = 3,
            orbitRadiusScale = 1.0f,
            petFrameDurationScale = 1f,
            haloBoost = 0f,
            breathAmplitude = 0.012f,
            shimmerAmplitude = 0.012f,
            pulseAmplitude = 0.016f,
            ringAlpha = 0.16f,
            glowAlpha = 0.28f,
            sparkAlpha = 0.24f,
        )
    }

    val reactionBoost = when (reaction) {
        PresenceReaction.ERROR_RECOVER -> 0.9f
        PresenceReaction.MEMORY_SPARK -> 0.7f
        PresenceReaction.SEARCH_SWEEP -> 0.55f
        PresenceReaction.RETURN_BLINK -> 0.38f
        PresenceReaction.TOUCH_NUZZLE -> 0.28f
        null -> 0f
    }

    return base.copy(
        orbitParticleCount = if (reaction == null) base.orbitParticleCount else maxOf(base.orbitParticleCount, 5),
        haloBoost = base.haloBoost + reactionBoost,
        pulseAmplitude = base.pulseAmplitude + reactionBoost * 0.012f,
        glowAlpha = base.glowAlpha + reactionBoost * 0.06f,
        sparkAlpha = base.sparkAlpha + reactionBoost * 0.05f,
    )
}
