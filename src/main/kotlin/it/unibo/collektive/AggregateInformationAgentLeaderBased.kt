package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.filtering.ParticleFilter

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypointLeaderBased(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device.randomGenerator, position, device) {
        val isDown = device["isDown"] as Boolean
        if (!isDown) {
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
                        isLeader,
                    ) { m1, m2 -> m1 + m2 }
                val point: Point? = when {
                    isLeader -> {
                        val sampledParticles = filter.resample(zebra.zebraID)
                        val newParticles = filter.predictParticles(sampledParticles)
                        filter.updateWeights(zebra.zebraID, newParticles, convergedMeasurements)
                        filter.estimatePosition(zebra.zebraID)
                    }
                    else ->null
                }
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
        filter.yielding { estimationsHistory.updateHistory(estimations) }
    }
}

context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.isLeaderBasedOnLocation(bound: Int): Boolean {
    val dist = device.environment.distanceFromNetworkCentroid(position.coordinates())
    val weight = centralityWeight(dist, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}
