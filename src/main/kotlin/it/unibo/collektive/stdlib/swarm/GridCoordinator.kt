@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive.stdlib.swarm

import it.unibo.alchemist.collektive.device.CollektiveDevice
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
import it.unibo.collektive.stdlib.election.isClosestToCentroid
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
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.computeDistributedSwarmMovement(
    gridFormationValues: GridFormationValues,
    bound: Int,
    estimationsHistory: List<ZebraPositionHistory>,
    isLeader: Boolean? = null, // if null, it is the neighboring-based scenario
): Point {
    val currentPosition = position.selfPosition()
    if (
        gridFormationValues.rows <= 0 ||
        gridFormationValues.cols <= 0 ||
        gridFormationValues.spacing <= 0.0
    ) {
        return currentPosition
    }
    val leaderBased = isLeader != null
    if (!leaderBased && !estimationsHistory.hasEnoughHistory(gridFormationValues.warmupRounds)) {
        return currentPosition
    }
    val isCoordinator = when {
        isLeader == null -> isClosestToCentroid(bound)
        else -> isLeader
    }
    val networkCentroid = when {
        isLeader == null -> networkCentroid(bound)
        else -> networkCentroid(isCoordinator)
    }
    val coordinatorTarget = when {
        leaderBased -> {
            val leaderTarget = estimationsHistory.targetOrNull(gridFormationValues.warmupRounds)
            if (isCoordinator && leaderTarget != null) leaderTarget else networkCentroid
        }
        else -> {
            val localContribution = estimationsHistory.latestContribution()
            val totalX = convergeSum(localContribution.sumX, isCoordinator)
            val totalY = convergeSum(localContribution.sumY, isCoordinator)
            val totalCount = convergeSum(localContribution.count, isCoordinator)
            when {
                totalCount > 0 -> Point(totalX / totalCount, totalY / totalCount)
                else -> networkCentroid
            }
        }
    }
    val sharedTarget = hopGradientCast(isCoordinator, if (isCoordinator) coordinatorTarget else networkCentroid)
    val gridIndex = when {
        isLeader == null -> networkGridIndex(bound, gridFormationValues)
        else -> networkGridIndex(isCoordinator, gridFormationValues)
    }
    if (gridIndex < 0) return currentPosition
    val desiredPosition = gridDestination(sharedTarget, gridIndex, gridFormationValues)
    val desiredPositionWithError = desiredPosition
        .plus(Point(gridFormationValues.errorOnDesiredPosition, gridFormationValues.errorOnDesiredPosition))
    return moveTowards(currentPosition, desiredPositionWithError, gridFormationValues.stepSize)
}

private fun List<ZebraPositionHistory>.targetOrNull(warmupRounds: Int): Point? {
    if (!hasEnoughHistory(warmupRounds)) {
        return null
    }
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
 */
data class GridFormationValues(
    val rows: Int,
    val cols: Int,
    val spacing: Double,
    val stepSize: Double,
    val warmupRounds: Int,
    val errorOnDesiredPosition: Double,
)
