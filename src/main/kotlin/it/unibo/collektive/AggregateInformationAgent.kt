package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.DistanceFromPosition
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.ZebraPositionHistory
import it.unibo.filtering.Particle
import it.unibo.filtering.ParticleFilter
import it.unibo.filtering.Point
import kotlin.collections.plus
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

const val p0 = -40
const val pathLoss = 2
const val measureStdDev = 0.5

fun selectNeighbors(
    originalList: List<FieldEntry<out Any, DistanceFromPosition>>,
    localID: Int,
    n: Int
): List<FieldEntry<out Any, DistanceFromPosition>> {
    val localEntry = originalList.find { it.id == localID }
    val localPoint = localEntry!!.value.currentPosition

    return originalList
        .filter { it.id != localID }
        .sortedBy { entry ->
            val targetPoint = entry.value.currentPosition
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
    val estimations = env.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
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
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targets = position.targetsPosition()
    return evolving(ParticleFilter(numberOfParticles, maxInitialSpeed, sideLength, targets.map { it.zebraID }.toSet(), random = random)) { filter ->
//        val previous = env.getOrDefault("Particles", mutableListOf<List<Particle>>())
        env["NumberOfParticles"] = numberOfParticles
//        previous.add(filter.getAll()) // TODO - this is redundant
//        env["Particles"] = previous
        val numberOfNeighbors = env.getOrDefault("NumberOfNeighbors", 0)
        val estimations = mutableListOf<ZebraPositionHistory>()
        for (zebra in targets) {
            alignedOn(zebra.zebraID) {
                val sampledParticles = filter.resample(zebra.zebraID)
                val newParticles = filter.predictParticles(sampledParticles)
                val selfPosition = position.selfPosition()
                val distance = hypot(zebra.position.x - selfPosition.x, zebra.position.y - selfPosition.y)
                val measure = p0 - 10 * pathLoss * log10(distance) + random.nextGaussian() * measureStdDev
                val info = neighboring(DistanceFromPosition(selfPosition, measure)).all.list
                val neighborsInfo = selectNeighbors(info, localId as Int, numberOfNeighbors).map { it.value }
                filter.updateWeights(zebra.zebraID, newParticles, neighborsInfo)
                val estimation = filter.estimatePosition(zebra.zebraID)
                val newZebra = estimations.find { it.zebraID == zebra.zebraID }?.let { est ->
                    est.copy(positions = est.positions + estimation)
                } ?: ZebraPositionHistory(zebra.zebraID, listOf(estimation))
                estimations.add(newZebra)
            }
        }
        filter.yielding {
            when {
                estimationsHistory.isNotEmpty() && estimations.isNotEmpty() -> {
                    estimationsHistory.map { history ->
                        val zebraPos = estimations.find { zebra -> zebra.zebraID == history.zebraID }?.positions
                        when {
                            zebraPos != null -> history.copy(positions = history.positions + zebraPos)
                            else -> history
                        }
                    }
                }
                else -> estimations
            }
        }
    }
}
