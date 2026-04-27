@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.gridElectionBound
import it.unibo.collektive.alchemist.device.sensors.gridFormationValues
import it.unibo.collektive.alchemist.device.sensors.leaderSwitchMargin
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.election.isClosestToCentroid
import it.unibo.collektive.stdlib.election.isClosestToCentroidWithHysteresis
import it.unibo.collektive.stdlib.swarm.computeDistributedSwarmMovement

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypointLeaderBased(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device.randomGenerator, position, device) {
        val isDown = device["isDown"] as Boolean
        if (!isDown) {
            val filterConfiguration = device.filterConfiguration
            val electionBound = device.gridElectionBound(filterConfiguration.sideLength)
            val isLeader = isClosestToCentroid(electionBound).also { device["isLeader"] = it }
            val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
            sensorsExecution(estimations, filterConfiguration, isLeader)
                .also { history ->
                    device["Estimations"] = history
                }
        }
    }

fun Aggregate<Int>.entrypointMovingSensorsLeaderBased(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device, device.randomGenerator, position) {
        val isDown = device["isDown"] as Boolean
        if (!isDown) {
            val filterConfiguration = device.filterConfiguration
            val gridValues = device.gridFormationValues
            val electionBound = (gridValues.rows * gridValues.cols).takeIf { it > 0 } ?: filterConfiguration.sideLength
            val switchMargin = device.leaderSwitchMargin(gridValues.spacing)
            val isLeader = isClosestToCentroidWithHysteresis(electionBound, switchMargin).also {
                device["isLeader"] = it
            }
            val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
            val history = sensorsExecution(estimations, filterConfiguration, isLeader)
            device["Estimations"] = history
            computeDistributedSwarmMovement(gridValues, electionBound, history, isLeader).also {
                device["NextPosition"] = it
            }
        }
    }

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
 * @param filterConfiguration Particle filter settings read from the simulation environment.
 * @param isLeader Whether this node is the elected sink for measurement aggregation.
 * @return Updated zebra position history list.
 */
context(device: CollektiveDevice<*>, position: LocationSensor)
internal fun Aggregate<Int>.sensorsExecution(
    estimationsHistory: List<ZebraPositionHistory>,
    filterConfiguration: FilterConfiguration,
    isLeader: Boolean,
): List<ZebraPositionHistory> {
    val targetsPosition = position.targetsPosition()
    val selfPosition = position.selfPosition()
    return evolving(
        device.createParticleFilter(filterConfiguration, targetsPosition.map { it.zebraID }.toSet()),
    ) { filter ->
        val estimations = mutableListOf<ZebraPositionHistory>()
        for (zebra in targetsPosition) {
            alignedOn(zebra.zebraID) {
                device["Particles${zebra.zebraID}"] = filter.getAll(zebra.zebraID)
                val myMeasure = fromPositionToMeasure(selfPosition, zebra.position, device.randomGenerator)
                val convergedMeasurements =
                    convergeCast(
                        listOf(DistanceFromPosition(selfPosition, myMeasure)),
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
