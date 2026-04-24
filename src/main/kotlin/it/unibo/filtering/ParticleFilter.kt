@file:Suppress("MagicNumber")

package it.unibo.filtering

import it.unibo.alchemist.util.RandomGenerators.nextDouble
import it.unibo.collektive.PATH_LOSS
import it.unibo.collektive.P_0
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.Particle
import it.unibo.collektive.models.Point
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

/**
 * A simple Particle Filter implementation for 2D position tracking.
 * @param numberOfParticles The number of particles to use in the filter.
 * @param maxInitialSpeed The maximum initial speed of the particles.
 * @param sideLength The side length of the square area where particles are initialized.
 * @param initialMinX Minimum x-coordinate for particle initialization.
 * @param initialMaxX Maximum x-coordinate for particle initialization.
 * @param initialMinY Minimum y-coordinate for particle initialization.
 * @param initialMaxY Maximum y-coordinate for particle initialization.
 * @param clampParticlesToInitializationArea Whether predicted particles should be clamped
 * to the configured initialization rectangle.
 * @param random An instance of a random number generator.
 * @param measurementStdDev The standard deviation of the measurement noise.
 */
class ParticleFilter(
    private val numberOfParticles: Int = 250,
    private val maxInitialSpeed: Double = 2.0,
    private val sideLength: Double = 100.0,
    private val initialMinX: Double = -sideLength / 2.0,
    private val initialMaxX: Double = sideLength / 2.0,
    private val initialMinY: Double = -sideLength / 2.0,
    private val initialMaxY: Double = sideLength / 2.0,
    private val clampParticlesToInitializationArea: Boolean = false,
    targetsIDs: Set<Int> = setOf(),
    val measurementStdDev: Double = 0.5,
    val random: RandomGenerator,
) {
    private val particlesFor: MutableMap<Int, List<Particle>> = targetsIDs.associateWith {
        initParticles()
    }.toMutableMap()

    /**
     * Creates an initial particle cloud uniformly in position and velocity.
     *
     * - Position: `x ~ U(initialMinX, initialMaxX)`, `y ~ U(initialMinY, initialMaxY)`
     * - Velocity: `vx,vy ~ U(-maxInitialSpeed, maxInitialSpeed)`
     * - Weight: uniform (`1 / numberOfParticles`)
     *
     * @return A newly initialized list of particles.
     */
    private fun initParticles(): List<Particle> = List(numberOfParticles) {
        val x = random.nextDouble(initialMinX, initialMaxX)
        val y = random.nextDouble(initialMinY, initialMaxY)
        val vx = random.nextDouble(-maxInitialSpeed, maxInitialSpeed)
        val vy = random.nextDouble(-maxInitialSpeed, maxInitialSpeed)
        Particle(x, y, vx, vy, 1.0 / numberOfParticles)
    }

    /**
     * Returns the current particle population for a target id.
     *
     * If no population exists yet, a new one is returned.
     *
     * @param sampleID Target/sample identifier.
     * @return The particles currently associated with `sampleID`, or a freshly initialized set.
     */
    fun getAll(sampleID: Int): List<Particle> = particlesFor[sampleID] ?: initParticles()

    /**
     * Predicts the new state of the particles based on a simple motion model with added Gaussian noise.
     * @param sampledParticles The list of particles to predict from.
     * @param dt The time step for the prediction.
     * @return A new list of predicted particles.
     */
    fun predictParticles(sampledParticles: List<Particle>, dt: Double = 1.0): List<Particle> {
        val newParticles = ArrayList<Particle>(sampledParticles.size)
        for (p in sampledParticles) {
            val noiseX = random.nextGaussian() * 0.5
            val noiseY = random.nextGaussian() * 0.5
            val noiseVx = random.nextGaussian() * 0.2
            val noiseVy = random.nextGaussian() * 0.2
            val predictedX = p.x + (p.vx * dt) + noiseX
            val predictedY = p.y + (p.vy * dt) + noiseY
            val predictedVx = p.vx + noiseVx
            val predictedVy = p.vy + noiseVy
            val newX = if (clampParticlesToInitializationArea) predictedX.coerceIn(initialMinX, initialMaxX) else predictedX
            val newY = if (clampParticlesToInitializationArea) predictedY.coerceIn(initialMinY, initialMaxY) else predictedY
            val newVx = when {
                clampParticlesToInitializationArea && newX != predictedX -> 0.0
                else -> predictedVx
            }
            val newVy = when {
                clampParticlesToInitializationArea && newY != predictedY -> 0.0
                else -> predictedVy
            }
            newParticles.add(Particle(newX, newY, newVx, newVy, p.weight))
        }
        return newParticles
    }

    /**
     * Updates and normalizes particle weights for a target id given sensor measurements.
     *
     * For each particle and each measurement:
     * - compute geometric distance from particle to sensor
     * - derive expected RSS-like value via `P_0 - 10 * PATH_LOSS * log10(d)`
     * - accumulate log-likelihood under a Gaussian noise model
     *
     * To improve numerical stability, the method subtracts the maximum log-weight before exponentiation.
     * If all resulting weights degenerate to zero, weights are reset to a uniform distribution.
     *
     * @param particlesID Target/sample identifier whose population is being updated.
     * @param newParticles Predicted particles to reweight.
     * @param measurements Sensor measurements `(sensorPosition, measuredValue)`.
     */
    fun updateWeights(particlesID: Int, newParticles: List<Particle>, measurements: List<DistanceFromPosition>) {
        var maxLogW = Double.NEGATIVE_INFINITY
        newParticles.forEach { particle ->
            var newW = 0.0
            measurements.forEach { (sensorPosition, measurement) ->
                val d = hypot(particle.x - sensorPosition.x, particle.y - sensorPosition.y).coerceAtLeast(1.0)
                val expectedMeasure = P_0 - 10 * PATH_LOSS * log10(d) // + random.nextGaussian() * measureStdDev
                val dist = measurement - expectedMeasure
                // P(z|x) ~ exp(-dist^2 / (2 * sigma^2)) (simplified cause we are using log to sum and not to multiply)
                val likelihood = -(0.5 * (dist * dist) / (measurementStdDev * measurementStdDev))
                newW += likelihood
            }
            // totalWeight += newW
            particle.weight = newW
            if (newW > maxLogW) maxLogW = newW
        }

        var totalWeight = 0.0
        newParticles.forEach { particle ->
            particle.weight = exp(particle.weight - maxLogW)
            totalWeight += particle.weight
        }

        // Weights normalization
        if (totalWeight > 0.0) {
            for (p in newParticles) {
                p.weight /= totalWeight
            }
        } else {
            val uniformWeight = 1.0 / numberOfParticles
            for (p in newParticles) {
                p.weight = uniformWeight
            }
        }
        particlesFor[particlesID] = newParticles
    }

    /**
     * Resamples particles for a [sampleID] according to their normalized weights.
     * @return A new list of resampled particles with reset weights.
     */
    fun resample(sampleID: Int): List<Particle> {
        val particles = particlesFor[sampleID].takeUnless { it.isNullOrEmpty() }
            ?: initParticles().also { particlesFor[sampleID] = it }
        val newParticles = ArrayList<Particle>(numberOfParticles)
        val totalWeight = particles.sumOf { it.weight }
        if (totalWeight == 0.0) return particles
        val cumulativeWeights = DoubleArray(numberOfParticles)
        var currentSum = 0.0
        for (i in 0 until numberOfParticles) {
            currentSum += particles[i].weight
            cumulativeWeights[i] = currentSum / totalWeight
        }
        cumulativeWeights[numberOfParticles - 1] = 1.0
        val resetWeight = 1.0 / numberOfParticles
        repeat(numberOfParticles) {
            val r = random.nextDouble()
            var selectedIndex = 0
            for (j in 0 until numberOfParticles) {
                if (r <= cumulativeWeights[j]) {
                    selectedIndex = j
                    break
                }
            }
            val p = particles[selectedIndex]
            newParticles.add(
                Particle(
                    x = p.x,
                    y = p.y,
                    vx = p.vx,
                    vy = p.vy,
                    weight = resetWeight,
                ),
            )
        }
        return newParticles
    }

    /**
     * Computes the current state estimate for a target id as a weighted mean of particles.
     *
     * If no particles are available yet for the id, a new population is initialized first.
     *
     * @param sampleID Target/sample identifier.
     * @return Estimated 2D position.
     */
    fun estimatePosition(sampleID: Int): Point {
        val particles = particlesFor[sampleID].takeUnless { it.isNullOrEmpty() }
            ?: initParticles().also { particlesFor[sampleID] = it }
        var x = 0.0
        var y = 0.0
        for (p in particles) {
            x += p.x * p.weight
            y += p.y * p.weight
        }
        return Point(x, y)
    }
}
