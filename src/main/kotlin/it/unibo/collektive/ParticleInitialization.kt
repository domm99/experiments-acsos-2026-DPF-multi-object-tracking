package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice

internal fun CollektiveDevice<*>.particleInitializationArea(sideLength: Double): ParticleInitializationArea {
    val halfSideLength = sideLength / 2.0
    return ParticleInitializationArea(
        minX = getOrDefault("InitialParticlesMinX", -halfSideLength),
        maxX = getOrDefault("InitialParticlesMaxX", halfSideLength),
        minY = getOrDefault("InitialParticlesMinY", -halfSideLength),
        maxY = getOrDefault("InitialParticlesMaxY", halfSideLength),
        clampParticles = getOrDefault("ClampParticlesToInitializationArea", false),
    )
}

internal data class ParticleInitializationArea(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val clampParticles: Boolean,
)
