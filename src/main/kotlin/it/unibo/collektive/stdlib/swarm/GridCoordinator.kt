package it.unibo.collektive.stdlib.swarm

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.gridDestination
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.models.hasEnoughHistory
import it.unibo.collektive.models.latestContribution
import it.unibo.collektive.models.plus
import it.unibo.collektive.moveTowards
import it.unibo.collektive.stdlib.accumulation.convergeSum
import it.unibo.collektive.stdlib.election.leaderClosestToPoint
import it.unibo.collektive.stdlib.spreading.hopGradientCast

/**
 * Computes the next swarm position for the local filter device using distributed zebra estimates.
 *
 * The coordinator aggregates the latest zebra contributions, shares the resulting target,
 * and each filter device moves toward its assigned slot in the swarm grid.
 *
 * @param gridFormationValues Grid layout and movement settings for the swarm.
 * @param bound Neighborhood bound used by the centroid-based coordinator election.
 * @param estimationsHistory Local history of zebra position estimations.
 * @return The next position for the local device.
 */
context(position: LocationSensor)
fun Aggregate<Int>.computeDistributedSwarmMovement(
    gridFormationValues: GridFormationValues,
    bound: Int,
    estimationsHistory: List<ZebraPositionHistory>,
    isLeader: Boolean? = null, // if null, it is the neighboring-based scenario
): Point {
    val currentPosition = position.selfPosition()
    return when {
        gridFormationValues.hasInvalidGridDimensions() -> currentPosition
        estimationsHistory.needsWarmup(isLeader != null, gridFormationValues.warmupRounds) -> currentPosition
        else -> nextSwarmPosition(currentPosition, gridFormationValues, bound, estimationsHistory, isLeader)
    }
}

context(position: LocationSensor)
private fun Aggregate<Int>.nextSwarmPosition(
    currentPosition: Point,
    gridFormationValues: GridFormationValues,
    bound: Int,
    estimationsHistory: List<ZebraPositionHistory>,
    isLeader: Boolean?,
): Point {
    val networkCentroid = networkCentroidFor(isLeader, bound)
    val isCoordinator = coordinatorFor(isLeader, networkCentroid, bound)
    val coordinatorTarget = coordinatorTarget(
        leaderBased = isLeader != null,
        isCoordinator = isCoordinator,
        networkCentroid = networkCentroid,
        estimationsHistory = estimationsHistory,
        warmupRounds = gridFormationValues.warmupRounds,
    )
    val sharedTarget = hopGradientCast(
        isCoordinator,
        if (isCoordinator) coordinatorTarget else networkCentroid,
    )
    val gridIndex = gridIndexFor(isLeader, isCoordinator, bound, gridFormationValues)
    return when {
        gridIndex < 0 -> currentPosition
        else -> moveTowardsGridDestination(currentPosition, sharedTarget, gridIndex, gridFormationValues)
    }
}

private fun GridFormationValues.hasInvalidGridDimensions(): Boolean = rows <= 0 || cols <= 0 || spacing <= 0.0

private fun List<ZebraPositionHistory>.needsWarmup(leaderBased: Boolean, warmupRounds: Int): Boolean =
    !leaderBased && !hasEnoughHistory(warmupRounds)

context(position: LocationSensor)
private fun Aggregate<Int>.coordinatorFor(isLeader: Boolean?, networkCentroid: Point, bound: Int): Boolean =
    isLeader ?: (leaderClosestToPoint(networkCentroid, bound) == localId)

context(position: LocationSensor)
private fun Aggregate<Int>.networkCentroidFor(isLeader: Boolean?, bound: Int): Point = when {
    isLeader == null -> networkCentroid(bound = bound)
    else -> networkCentroid(isLeader)
}

private fun Aggregate<Int>.coordinatorTarget(
    leaderBased: Boolean,
    isCoordinator: Boolean,
    networkCentroid: Point,
    estimationsHistory: List<ZebraPositionHistory>,
    warmupRounds: Int,
): Point = when {
    leaderBased -> estimationsHistory.leaderTargetOr(isCoordinator, networkCentroid, warmupRounds)
    else -> neighborTargetOr(estimationsHistory, isCoordinator, networkCentroid)
}

private fun List<ZebraPositionHistory>.leaderTargetOr(
    isCoordinator: Boolean,
    networkCentroid: Point,
    warmupRounds: Int,
): Point {
    val leaderTarget = targetOrNull(warmupRounds)
    return when {
        isCoordinator && leaderTarget != null -> leaderTarget
        else -> networkCentroid
    }
}

private fun Aggregate<Int>.neighborTargetOr(
    estimationsHistory: List<ZebraPositionHistory>,
    isCoordinator: Boolean,
    networkCentroid: Point,
): Point {
    val localContribution = estimationsHistory.latestContribution()
    val totalX = convergeSum(localContribution.sumX, isCoordinator)
    val totalY = convergeSum(localContribution.sumY, isCoordinator)
    val totalCount = convergeSum(localContribution.count, isCoordinator)
    return when {
        totalCount > 0 -> Point(totalX / totalCount, totalY / totalCount)
        else -> networkCentroid
    }
}

context(position: LocationSensor)
private fun Aggregate<Int>.gridIndexFor(
    isLeader: Boolean?,
    isCoordinator: Boolean,
    bound: Int,
    gridFormationValues: GridFormationValues,
): Int = when {
    isLeader == null -> networkGridIndex(bound, gridFormationValues)
    else -> networkGridIndex(isCoordinator, gridFormationValues)
}

private fun moveTowardsGridDestination(
    currentPosition: Point,
    sharedTarget: Point,
    gridIndex: Int,
    gridFormationValues: GridFormationValues,
): Point {
    val desiredPosition = gridDestination(sharedTarget, gridIndex, gridFormationValues)
    val desiredPositionWithError = desiredPosition.plus(
        Point(gridFormationValues.errorOnDesiredPosition, gridFormationValues.errorOnDesiredPosition),
    )
    return moveTowards(currentPosition, desiredPositionWithError, gridFormationValues.stepSize)
}

private fun List<ZebraPositionHistory>.targetOrNull(warmupRounds: Int): Point? {
    if (!hasEnoughHistory(warmupRounds)) return null
    val contribution = latestContribution()
    return when {
        contribution.count > 0 -> Point(contribution.sumX / contribution.count, contribution.sumY / contribution.count)
        else -> null
    }
}

/**
 * Parameters describing a rectangular swarm grid and its movement step.
 *
 * @property rows Number of rows in the grid formation.
 * @property cols Number of columns in the grid formation.
 * @property spacing Distance between adjacent grid slots.
 * @property stepSize Maximum distance a node can move in a single update.
 * @property warmupRounds Number of history rounds required before neighbor-based movement starts.
 * @property errorOnDesiredPosition Offset added to the assigned grid destination.
 */
data class GridFormationValues(
    val rows: Int,
    val cols: Int,
    val spacing: Double,
    val stepSize: Double,
    val warmupRounds: Int,
    val errorOnDesiredPosition: Double,
)
