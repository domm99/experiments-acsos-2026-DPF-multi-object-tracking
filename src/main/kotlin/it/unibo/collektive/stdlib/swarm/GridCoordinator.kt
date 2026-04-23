@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive.stdlib.swarm

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.currentFiltersCentroid
import it.unibo.collektive.alchemist.device.filterIndexOf
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.gridDestination
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.models.latestContribution
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
context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.computeDistributedSwarmMovement(
    gridFormationValues: GridFormationValues,
    bound: Double,
    estimationsHistory: List<ZebraPositionHistory>,
): Point = evolving(device.currentFiltersCentroid()) { fallbackTarget ->
    val currentPosition = position.selfPosition()
    var sharedTarget: Point = fallbackTarget
    val nextPosition: Point = when {
        (
            gridFormationValues.rows <= 0 ||
                gridFormationValues.cols <= 0 ||
                gridFormationValues.spacing <= 0.0
            ) -> currentPosition
        else -> {
            val localContribution = estimationsHistory.latestContribution()
            val isCoordinator =
                isClosestToCentroid(bound.toInt()) // in the leader based scenario, this should be the leader
            val totalX = convergeSum(localContribution.sumX, isCoordinator)
            val totalY = convergeSum(localContribution.sumY, isCoordinator)
            val totalCount = convergeSum(localContribution.count, isCoordinator)
            val coordinatorTarget = when {
                totalCount > 0 -> Point(totalX / totalCount, totalY / totalCount)
                else -> fallbackTarget
            }
            sharedTarget = hopGradientCast(isCoordinator, if (isCoordinator) coordinatorTarget else fallbackTarget)
            val gridIndex = device.filterIndexOf(localId)
            val desiredPosition = gridDestination(sharedTarget, gridIndex, gridFormationValues)
            moveTowards(currentPosition, desiredPosition, gridFormationValues.stepSize)
        }
    }
    sharedTarget.yielding { nextPosition }
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
)

/**
 * Default movement step used when no swarm-specific step size is provided.
 */
const val DEFAULT_SWARM_STEP_SIZE = 2.0
