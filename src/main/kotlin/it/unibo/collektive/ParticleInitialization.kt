package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.filtering.ParticleFilter

/**
 * Particle filter parameters read from the simulation environment.
 *
 * @property numberOfParticles Number of particles maintained for each tracked target.
 * @property maxInitialSpeed Maximum absolute value used to initialize particle velocity components.
 * @property sideLength Side length of the simulation area used as the default initialization range.
 */
internal data class FilterConfiguration(val numberOfParticles: Int, val maxInitialSpeed: Double, val sideLength: Int)

/**
 * Reads the particle filter configuration from the device environment.
 */
internal val CollektiveDevice<*>.filterConfiguration: FilterConfiguration
    get() = FilterConfiguration(
        numberOfParticles = this["NumberOfParticles"],
        maxInitialSpeed = this["MaxInitialSpeed"],
        sideLength = this["SideLength"],
    )

/**
 * Creates a [ParticleFilter] initialized with the device configuration and target identifiers.
 *
 * The initialization area can be overridden through the `InitialParticles*` molecules and, when
 * `ClampParticlesToInitializationArea` is true, also bounds the prediction step.
 *
 * @param configuration Particle filter parameters read from the device environment.
 * @param targetsIds Identifiers of the targets that should have an initial particle population.
 * @return A configured particle filter sharing the device random generator.
 */
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

/**
 * Reads the rectangular particle initialization area from the device environment.
 *
 * When no explicit bounds are configured, particles are initialized in a square centered at the origin
 * with side [sideLength].
 *
 * @param sideLength Default side length used to derive missing bounds.
 * @return Particle initialization bounds and clamping behavior.
 */
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

/**
 * Rectangular area used to initialize and optionally clamp particles.
 *
 * @property minX Minimum x coordinate.
 * @property maxX Maximum x coordinate.
 * @property minY Minimum y coordinate.
 * @property maxY Maximum y coordinate.
 * @property clampParticles Whether predictions should stay inside these bounds.
 */
internal data class ParticleInitializationArea(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val clampParticles: Boolean,
)
