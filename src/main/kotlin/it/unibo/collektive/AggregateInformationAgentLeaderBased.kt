package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.DistanceFromPosition
import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.ZebraPositionHistory
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.filtering.ParticleFilter
import it.unibo.filtering.Point
import it.unibo.filtering.distanceTo
import kotlin.collections.plus
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

fun fromPositionToMeasure(selfPosition: Point, sensedPosition: Point, random: RandomGenerator): Double {
    val distance = hypot(sensedPosition.x - selfPosition.x, sensedPosition.y - selfPosition.y)
    val measure = p0 - 10 * pathLoss * log10(distance) + random.nextGaussian() * measureStdDev
    return measure
}

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypointLeaderBased(
    device: CollektiveDevice<*>,
    position: LocationSensor,
) = context(device.randomGenerator, position, device) {

    val isDown = device["isDown"] as Boolean
    if(!isDown) {
        val sideLength = device["SideLength"] as Int
        val numberOfParticles = device["NumberOfParticles"] as Int
        val maxInitialSpeed = device["MaxInitialSpeed"] as Double
        val isLeader = isLeaderBasedOnLocation(sideLength).also { device["isLeader"] = it }
        val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
        with(device) {
            sensorsExecution(isLeader, estimations, numberOfParticles, maxInitialSpeed, sideLength.toDouble())
        }.also { history ->
            device["Estimations"] = history
        }
    }
}

context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.sensorsExecution(
    isLeader: Boolean,
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targetsPosition = position.targetsPosition()
    val selfPosition = position.selfPosition()
    return evolving(
        ParticleFilter(
            numberOfParticles,
            maxInitialSpeed,
            sideLength,
            targetsIDs = targetsPosition.map { it.zebraID }.toSet(),
            random = device.randomGenerator,
        ),
    ) { filter ->
        val estimations = mutableListOf<ZebraPositionHistory>()
        for (zebra in targetsPosition) {
            alignedOn(zebra.zebraID) {
                device["Particles${zebra.zebraID}"] = filter.getAll(zebra.zebraID)
                val myMeasure = fromPositionToMeasure(selfPosition, zebra.position, device.randomGenerator)
                val convergedMeasurements =
                    convergeCast(
                        listOfNotNull(DistanceFromPosition(selfPosition, myMeasure)),
                        isLeader
                    ) { m1, m2 -> m1 + m2 }
                val point: Point? = if (isLeader) {
                    val sampledParticles = filter.resample(zebra.zebraID)
                    val newParticles = filter.predictParticles(sampledParticles)
                    filter.updateWeights(zebra.zebraID, newParticles, convergedMeasurements)
                    filter.estimatePosition(zebra.zebraID)
                } else null
                val oldZebraInfo = estimations.find { it.zebraID == zebra.zebraID }
                if (point != null) {
                    val newZebraInfo = when {
                        oldZebraInfo != null -> oldZebraInfo.copy(positions = oldZebraInfo.positions + point)
                        else -> ZebraPositionHistory(zebra.zebraID, listOf(point))
                    }
                    estimations.add(newZebraInfo)
                }
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

context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.isLeaderBasedOnLocation(bound: Int): Boolean {
    val dist = device.environment.distanceFromNetworkCentroid(position.coordinates())
    val weight = centralityWeight(dist, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}

fun <T, P : Position<P>> Environment<T, P>.distanceFromNetworkCentroid(position: Point): Double {
    val filtersNode = this.nodes.filter { it.contains(SimpleMolecule("Filter")) }
    val sum = filtersNode.fold(0.0 to 0.0) { acc, next ->
        val nextNodePos = this.getPosition(next).coordinates // Add 10 to avoid negative positions .map { it + 10 }
        acc.first + nextNodePos[0] to acc.second + nextNodePos[1]
    }
    val filtersCount = filtersNode.size
    val center = Point(sum.first / filtersCount, sum.second / filtersCount)
    return center.distanceTo(position) // the smallest, the closest
}

fun centralityWeight(distanceFromCentroid: Double, sigma: Double): Double =
    exp(-(distanceFromCentroid * distanceFromCentroid) / (2 * sigma * sigma))
