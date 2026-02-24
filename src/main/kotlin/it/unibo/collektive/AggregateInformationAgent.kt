package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.filtering.Particle
import it.unibo.filtering.ParticleFilter
import it.unibo.filtering.Point
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

const val p0 = -40
const val pathLoss = 2
const val measureStdDev = 0.5

fun selectNeighbors(
    originalList: List<FieldEntry<out Any, Pair<Point, Double>>>,
    localID: Int,
    n: Int
): List<FieldEntry<out Any, Pair<Point, Double>>> {
    val localEntry = originalList.find { it.id == localID }
    val localPoint = localEntry!!.value.first

    return originalList
        .filter { it.id != localID }
        .sortedBy { entry ->
            val targetPoint = entry.value.first
            val dx = targetPoint.x - localPoint.x
            val dy = targetPoint.y - localPoint.y
            (dx * dx) + (dy * dy)
        }
        .take(n)
        .let { listOf(localEntry) + it }
}

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypoint(
    collektiveDevice: CollektiveDevice<*>,
    env: EnvironmentVariables,
    position: LocationSensor,
) = context(env, collektiveDevice.randomGenerator, position) {
    val estimations = env.getOrDefault("Estimations", listOf<Point>())
    localFiltering(estimations, env["NumberOfParticles"], env["MaxInitialSpeed"], env["SideLength"]).also { history ->
        env["Estimations"] = history
    }
}

/**
 * Performs local filtering using a Particle Filter to estimate the position
 * of a target based on neighborhood information.
 *
 * @param random the random generator for stochastic processes
 * @param position the location sensor providing target position and neighborhood data
 */
context(random: RandomGenerator, position: LocationSensor, env: EnvironmentVariables)
fun Aggregate<*>.localFiltering(
    estimationsHistory: List<Point>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<Point> = evolving(ParticleFilter(numberOfParticles, maxInitialSpeed, sideLength, random)) { filter ->
    val previous = env.getOrDefault("Particles", mutableListOf<List<Particle>>())
    env["NumberOfParticles"] = numberOfParticles
    previous.add(filter.getAll()) // TODO - this is redundant
    env["Particles"] = previous
    val numberOfNeighbors = env.getOrDefault("NumberOfNeighbors", 0)
    val sampledParticles = filter.resample()
    val newParticles = filter.predictParticles(sampledParticles)

    val selfPosition = position.selfPosition()
    val targetPosition = position.targetsPosition().first()
    val distance = hypot(targetPosition.x - selfPosition.x, targetPosition.y - selfPosition.y)
    val measure = p0 - 10 * pathLoss * log10(distance) + random.nextGaussian() * measureStdDev

    val info =
        neighboring(selfPosition to measure)
            .all.list

    val neighborsInfo = selectNeighbors(info, localId as Int, numberOfNeighbors)
            .mapNotNull { it.value }

    filter.updateWeights(newParticles, neighborsInfo)
    val estimation = filter.estimatePosition()
    val history = estimationsHistory + estimation
    filter.yielding { history }
}
