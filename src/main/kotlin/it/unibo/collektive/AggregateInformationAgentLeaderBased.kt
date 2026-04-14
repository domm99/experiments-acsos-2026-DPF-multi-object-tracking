package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighborhood
import it.unibo.collektive.aggregate.ids
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.election.isClosestToCentroid
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
            val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
            with(device) {
                sensorsExecution(estimations, numberOfParticles, maxInitialSpeed, sideLength.toDouble())
            }.also { history ->
                device["Estimations"] = history
            }
        }
    }

context(device: CollektiveDevice<*>, position: LocationSensor)
/**
 * Runs one sensing/filtering round and returns updated zebra estimation histories.
 * 1. Read currently visible target positions and local position;
 * 2. Elect a leader;
 * 3. Maintain a persistent [ParticleFilter] via `evolving(...)`;
 * 4. For each target zebra:
 *    align computation by zebra id,
 *    collect local noisy measure,
 *    aggregate neighborhood measurements through [convergeCast] toward the leader,
 *    if leader, perform particle filter cycle: resample -> predict -> update -> estimate.
 * 5. Merge current-round estimates with prior [estimationsHistory] and return the updated history.
 *
 * @param estimationsHistory Previously accumulated estimation history.
 * @param numberOfParticles Particle count used by the filter.
 * @param maxInitialSpeed Max initial particle velocity component.
 * @param sideLength Simulation area side length used for initialization and election bound.
 * @return Updated zebra position history list.
 */
fun Aggregate<Int>.sensorsExecution(
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targetsPosition = position.targetsPosition()
    val selfPosition = position.selfPosition()
    val isLeader = isClosestToCentroid(sideLength.toInt()).also { device["isLeader"] = it }
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
//             val isLeader = isClosestToCentroid(sideLength.toInt()).also { device["isLeaderOf${zebra.zebraID}"] = it }
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

                    else -> null
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
