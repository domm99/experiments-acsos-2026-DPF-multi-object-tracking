@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.models.distanceTo
import it.unibo.filtering.ParticleFilter
import it.unibo.collektive.stdlib.accumulation.convergeSum
import it.unibo.collektive.stdlib.consensus.globalElection
import it.unibo.collektive.stdlib.spreading.hopGradientCast

private const val DEFAULT_SWARM_STEP_SIZE = 2.0

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypoint(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device, device.randomGenerator, position) {
        val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
        localFiltering(
            estimations,
            device["NumberOfParticles"],
            device["MaxInitialSpeed"],
            device["SideLength"],
        ).also { history ->
            device["Estimations"] = history
        }
    }

/**
 * Runs local filtering and computes the next distributed movement of each sensor in the swarm.
 *
 * The target is built from the latest estimations available on each node, then aggregated
 * and broadcast through neighbor-to-neighbor coordination. Each node finally stores its
 * own next movement step inside the `NextPosition` molecule.
 */
fun Aggregate<Int>.informationFilterAndDistributedMovementEntrypoint(
    device: CollektiveDevice<*>,
    position: LocationSensor,
) = context(device, device.randomGenerator, position) {
    val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
    val history = localFiltering(
        estimations,
        device["NumberOfParticles"],
        device["MaxInitialSpeed"],
        device["SideLength"],
    )
    device["Estimations"] = history
    computeDistributedSwarmMovement(history)
    history
}

/**
 * Performs local filtering using a Particle Filter to estimate the position
 * of a target based on neighborhood information.
 *
 * @param random the random generator for stochastic processes
 * @param position the location sensor providing target position and neighborhood data
 */
context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<*>.localFiltering(
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targets = position.targetsPosition()
    return evolving(
        ParticleFilter(
            numberOfParticles,
            maxInitialSpeed,
            sideLength,
            targets.map { it.zebraID }.toSet(),
            random = device.randomGenerator,
        ),
    ) { filter ->
        device["NumberOfParticles"] = numberOfParticles
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
 */
fun List<ZebraPositionHistory>.updateHistory(
    estimations: MutableList<ZebraPositionHistory>,
): List<ZebraPositionHistory> = when {
    this.isNotEmpty() && estimations.isNotEmpty() -> {
        this.map { history ->
            val zebraPos = estimations.find { zebra -> zebra.zebraID == history.zebraID }?.positions
            when {
                zebraPos != null -> history.copy(positions = history.positions + zebraPos)
                else -> history
            }
        }
    }

    else -> this + estimations
}

context(device: CollektiveDevice<*>, position: LocationSensor)
private fun Aggregate<Int>.computeDistributedSwarmMovement(
    estimationsHistory: List<ZebraPositionHistory>,
): Point {
    val currentPosition = position.selfPosition()
    val rows = device.getOrDefault("FormationRows", 0)
    val columns = device.getOrDefault("FormationColumns", 0)
    val spacing = device.getOrDefault("FormationSpacing", 0.0)
    val stepSize = device.getOrDefault("SwarmStepSize", DEFAULT_SWARM_STEP_SIZE)

    if (rows <= 0 || columns <= 0 || spacing <= 0.0) {
        device["NextPosition"] = currentPosition
        return currentPosition
    }

    val fallbackTarget = device.getOrDefault("SwarmTarget", device.currentFiltersCentroid())
    val localContribution = estimationsHistory.latestContribution()
    val isCoordinator = isSwarmCoordinator()

    val totalX = convergeSum(localContribution.sumX, isCoordinator)
    val totalY = convergeSum(localContribution.sumY, isCoordinator)
    val totalCount = convergeSum(localContribution.count, isCoordinator)

    val coordinatorTarget = when {
        totalCount > 0 -> Point(totalX / totalCount, totalY / totalCount)
        else -> fallbackTarget
    }
    val sharedTarget = hopGradientCast(
        source = isCoordinator,
        local = if (isCoordinator) coordinatorTarget else fallbackTarget,
    )

    val gridIndex = device.filterIndexOf(localId)
    val desiredPosition = gridDestination(sharedTarget, gridIndex, rows, columns, spacing)
    val nextPosition = moveTowards(currentPosition, desiredPosition, stepSize)

    device["IsSwarmCoordinator"] = isCoordinator
    device["SwarmTarget"] = sharedTarget
    device["SwarmDesiredPosition"] = desiredPosition
    device["NextPosition"] = nextPosition
    return nextPosition
}

private data class EstimationContribution(val sumX: Double, val sumY: Double, val count: Int)

private data class SwarmCoordinatorPriority(val centrality: Double, val nodeId: Int) : Comparable<SwarmCoordinatorPriority> {
    override fun compareTo(other: SwarmCoordinatorPriority): Int =
        compareBy<SwarmCoordinatorPriority>({ it.centrality }, { it.nodeId }).compare(this, other)
}

context(device: CollektiveDevice<*>, position: LocationSensor)
private fun Aggregate<Int>.isSwarmCoordinator(): Boolean {
    val sideLength = device.getOrDefault("SideLength", 1)
    val distance = device.environment.distanceFromNetworkCentroid(position.coordinates())
    val priority = SwarmCoordinatorPriority(
        centrality = centralityWeight(distance, sideLength / 2.0),
        nodeId = localId,
    )
    return globalElection(priority) == localId
}

private fun List<ZebraPositionHistory>.latestContribution(): EstimationContribution {
    val latestPositions = mapNotNull { it.positions.lastOrNull() }
    return EstimationContribution(
        sumX = latestPositions.sumOf { it.x },
        sumY = latestPositions.sumOf { it.y },
        count = latestPositions.size,
    )
}

private fun CollektiveDevice<*>.currentFiltersCentroid(): Point {
    val filters = environment.nodes.filter { it.contains(SimpleMolecule("Filter")) }
    if (filters.isEmpty()) {
        return Point(0.0, 0.0)
    }
    val sumX = filters.sumOf { environment.getPosition(it).coordinates[0] }
    val sumY = filters.sumOf { environment.getPosition(it).coordinates[1] }
    return Point(sumX / filters.size, sumY / filters.size)
}

private fun CollektiveDevice<*>.filterIndexOf(nodeId: Int): Int =
    environment.nodes
        .filter { it.contains(SimpleMolecule("Filter")) }
        .map { it.id }
        .sorted()
        .indexOf(nodeId)
        .takeIf { it >= 0 }
        ?: 0

private fun gridDestination(
    target: Point,
    gridIndex: Int,
    rows: Int,
    columns: Int,
    spacing: Double,
): Point {
    val column = gridIndex % columns
    val row = gridIndex / columns
    val offsetX = (column - (columns - 1) / 2.0) * spacing
    val offsetY = (row - (rows - 1) / 2.0) * spacing
    return Point(target.x + offsetX, target.y + offsetY)
}

private fun moveTowards(current: Point, destination: Point, stepSize: Double): Point {
    val distance = current.distanceTo(destination)
    if (distance <= stepSize) {
        return destination
    }
    val ratio = stepSize / distance
    return Point(
        x = current.x + (destination.x - current.x) * ratio,
        y = current.y + (destination.y - current.y) * ratio,
    )
}

/**
 * Selects up to [n] closest neighbor entries to the local device and includes local entry.
 *
 * Distance is computed in squared Euclidean space using [DistanceFromPosition.currentPosition].
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
