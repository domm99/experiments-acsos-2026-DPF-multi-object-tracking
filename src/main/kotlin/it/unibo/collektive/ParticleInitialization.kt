package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.filtering.ParticleFilter

/**
 * Particle filter parameters read from the simulation environment.
 */
internal data class FilterConfiguration(
    val numberOfParticles: Int,
    val maxInitialSpeed: Double,
    val sideLength: Int,
)

internal val CollektiveDevice<*>.filterConfiguration: FilterConfiguration
    get() = FilterConfiguration(
        numberOfParticles = this["NumberOfParticles"],
        maxInitialSpeed = this["MaxInitialSpeed"],
        sideLength = this["SideLength"],
    )

internal fun CollektiveDevice<*>.createParticleFilter(
    configuration: FilterConfiguration,
    targetsIds: Set<Int>,
): ParticleFilter {
    val sideLength = configuration.sideLength.toDouble()
    val initializationArea = particleInitializationArea(sideLength)
    return ParticleFilter(
        numberOfParticles = configuration.numberOfParticles,
        maxInitialSpeed = configuration.maxInitialSpeed,
        sideLength = sideLength,
        initialMinX = initializationArea.minX,
        initialMaxX = initializationArea.maxX,
        initialMinY = initializationArea.minY,
        initialMaxY = initializationArea.maxY,
        clampParticlesToInitializationArea = initializationArea.clampParticles,
        targetsIDs = targetsIds,
        random = randomGenerator,
    )
}

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
