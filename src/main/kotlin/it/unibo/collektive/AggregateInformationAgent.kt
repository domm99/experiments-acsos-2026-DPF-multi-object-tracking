@file:Suppress("IgnoredReturnValue", "UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.alchemist.device.sensors.gridFormationValues
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.models.Point
import it.unibo.collektive.stdlib.swarm.computeDistributedSwarmMovement

/**
 * The entrypoint of the simulation performing local information filtering, without grid movement.
 *
 * The updated estimation history is stored back into the `Estimations` molecule.
 *
 * @param device Collektive device used to read simulation parameters and persist estimations.
 * @param position Location sensor used to observe local and target positions.
 * @return Updated zebra estimation histories.
 */
fun Aggregate<Int>.informationFilterEntrypoint(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device, device.randomGenerator, position) {
        val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
        val filterConfiguration = device.filterConfiguration
        val history = localFiltering(
            estimations,
            filterConfiguration,
        )
        device["Estimations"] = history
        history
    }

/**
 * Runs local filtering and computes the next distributed movement of each sensor in the swarm.
 *
 * The target is built from the latest estimations available on each node, then aggregated
 * and broadcast through neighbor-to-neighbor coordination. Each node finally stores its
 * own next movement step inside the `NextPosition` molecule.
 *
 * @param device Collektive device used to read simulation parameters and persist estimations.
 * @param position Location sensor used to observe local and target positions.
 * @return Updated zebra estimation histories.
 */
fun Aggregate<Int>.informationFilterAndDistributedMovementEntrypoint(
    device: CollektiveDevice<*>,
    position: LocationSensor,
) = context(device, device.randomGenerator, position) {
    val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
    val filterConfiguration = device.filterConfiguration
    val history = localFiltering(
        estimations,
        filterConfiguration,
    )
    device["Estimations"] = history


    val positions = device.getOrDefault("Positions", emptyList<Point>())

    val currentPosition = position.selfPosition()
    val newPositions = positions + listOf(currentPosition)
    device["Positions"] = newPositions


    val gridValues = device.gridFormationValues
    val electionBound = (gridValues.rows * gridValues.cols).takeIf { it > 0 } ?: filterConfiguration.sideLength
    val nextPosition = computeDistributedSwarmMovement(gridValues, electionBound, history)
    device["NextPosition"] = nextPosition
    history
}

/**
 * Performs local filtering using a Particle Filter to estimate the position
 * of a target based on neighborhood information.
 *
 * @param estimationsHistory Previously accumulated estimation history.
 * @param filterConfiguration particle filter settings read from the simulation environment
 * @param position the location sensor providing target position and neighborhood data
 * @return Updated zebra estimation histories.
 */
context(device: CollektiveDevice<*>, position: LocationSensor)
internal fun Aggregate<*>.localFiltering(
    estimationsHistory: List<ZebraPositionHistory>,
    filterConfiguration: FilterConfiguration,
): List<ZebraPositionHistory> {
    val targets = position.targetsPosition()
    return evolving(
        device.createParticleFilter(filterConfiguration, targets.map { it.zebraID }.toSet()),
    ) { filter ->
        device["NumberOfParticles"] = filterConfiguration.numberOfParticles
        val numberOfNeighbors = device.getOrDefault("NumberOfNeighbors", 0)
        val estimations = mutableListOf<ZebraPositionHistory>()
        for (zebra in targets) {
            alignedOn(zebra.zebraID) {
                val sampledParticles = filter.resample(zebra.zebraID)
                val newParticles = filter.predictParticles(sampledParticles)
                val selfPosition = position.selfPosition()
                val measure = fromPositionToMeasure(selfPosition, zebra.position, device.randomGenerator)
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
        filter.yielding { estimationsHistory.updateHistory(estimations) }
    }
}

/**
 * Utility function that returns a list with the updated [ZebraPositionHistory] given the current [estimations].
 *
 * Existing zebra histories keep their order, while newly observed zebra ids are appended.
 *
 * @param estimations Current-round estimates to append by zebra id.
 * @return Histories containing both previous and current-round estimations.
 */
internal fun List<ZebraPositionHistory>.updateHistory(
    estimations: List<ZebraPositionHistory>,
): List<ZebraPositionHistory> {
    val positionsByZebra = estimations
        .groupBy { it.zebraID }
        .mapValues { (_, histories) -> histories.flatMap { it.positions } }
    if (positionsByZebra.isEmpty()) return this
    val knownZebraIds = mapTo(mutableSetOf()) { it.zebraID }
    val updatedHistory = map { history ->
        positionsByZebra[history.zebraID]?.let { positions ->
            history.copy(positions = history.positions + positions)
        } ?: history
    }
    val newHistories = positionsByZebra
        .filterKeys { it !in knownZebraIds }
        .map { (zebraID, positions) -> ZebraPositionHistory(zebraID, positions) }
    return updatedHistory + newHistories
}

/**
 * Selects up to [n] closest neighbor entries to the local device and includes local entry.
 *
 * Distance is computed in squared Euclidean space using
 * [it.unibo.collektive.models.DistanceFromPosition.currentPosition].
 *
 * @param originalList Full list of local+neighbor field entries.
 * @param localID Identifier of the local device entry in [originalList].
 * @param n Number of nearest neighbors to keep (excluding local entry).
 * @return A list containing local entry first, followed by up to [n] nearest neighbors.
 */
private fun selectNeighbors(
    originalList: List<FieldEntry<out Any, DistanceFromPosition>>,
    localID: Int,
    n: Int,
): List<FieldEntry<out Any, DistanceFromPosition>> {
    val localEntry = requireNotNull(originalList.find { it.id == localID }) {
        "Local entry with id $localID not found"
    }
    val localPoint = localEntry.value.currentPosition
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
