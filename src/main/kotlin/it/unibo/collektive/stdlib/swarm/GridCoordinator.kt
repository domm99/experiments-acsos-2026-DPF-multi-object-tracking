@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters.

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
 * @param isLeader Optional leader flag for the leader-based scenario. When omitted, the coordinator
 * is elected near the computed network centroid.
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

/**
 * Computes the next position once the grid configuration and warmup checks have passed.
 *
 * The method first selects the coordinator, then computes the target shared through the
 * coordinator-centered gradient, and finally maps the local device to a grid slot.
 *
 * @param currentPosition Current local device position.
 * @param gridFormationValues Grid layout and movement settings for the swarm.
 * @param bound Election bound used when the coordinator is elected locally.
 * @param estimationsHistory Local history of zebra position estimations.
 * @param isLeader Optional externally elected leader flag for the leader-based scenario.
 * @return The next position for the local device.
 */
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

/**
 * Checks whether the configured grid can produce valid destinations.
 */
private fun GridFormationValues.hasInvalidGridDimensions(): Boolean = rows <= 0 || cols <= 0 || spacing <= 0.0

/**
 * Checks whether neighbor-based movement should wait for more estimation history.
 *
 * Leader-based movement is allowed to start before this warmup because the leader can fall back to
 * the network centroid until enough target history is available.
 *
 * @param leaderBased Whether movement is using the leader-based scenario.
 * @param warmupRounds Minimum number of history samples required in the neighbor-based scenario.
 * @return `true` when movement should stay still for this round.
 */
private fun List<ZebraPositionHistory>.needsWarmup(leaderBased: Boolean, warmupRounds: Int): Boolean =
    !leaderBased && !hasEnoughHistory(warmupRounds)

/**
 * Chooses the coordinator for the current round.
 *
 * In leader-based mode the provided [isLeader] flag is reused. In neighbor-based mode the coordinator
 * is elected as the device closest to the already computed [networkCentroid].
 *
 * @param isLeader Optional externally elected leader flag.
 * @param networkCentroid Centroid used as the election target in neighbor-based mode.
 * @param bound Election bound used in neighbor-based mode.
 * @return `true` when the local device is the coordinator.
 */
context(position: LocationSensor)
private fun Aggregate<Int>.coordinatorFor(isLeader: Boolean?, networkCentroid: Point, bound: Int): Boolean =
    isLeader ?: (leaderClosestToPoint(networkCentroid, bound) == localId)

/**
 * Computes the network centroid using the correct sink for the active coordination mode.
 *
 * In neighbor-based mode this elects a temporary information leader. In leader-based mode the
 * provided leader flag is used as the collection sink.
 *
 * @param isLeader Optional externally elected leader flag.
 * @param bound Election bound used when [isLeader] is not provided.
 * @return Network centroid distributed back to the local device.
 */
context(position: LocationSensor)
private fun Aggregate<Int>.networkCentroidFor(isLeader: Boolean?, bound: Int): Point = when {
    isLeader == null -> networkCentroid(bound = bound)
    else -> networkCentroid(isLeader)
}

/**
 * Computes the target that the coordinator should spread to the swarm.
 *
 * Leader-based mode uses the leader's current target estimate when available, while neighbor-based
 * mode aggregates the latest estimate contributions through converge-cast.
 *
 * @param leaderBased Whether movement is using the leader-based scenario.
 * @param isCoordinator Whether the local device coordinates this round.
 * @param networkCentroid Fallback target when no zebra estimate is available.
 * @param estimationsHistory Local history of zebra position estimations.
 * @param warmupRounds Minimum history length required before leader target estimates are used.
 * @return Coordinator target for this round.
 */
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

/**
 * Returns the leader's target estimate, or [networkCentroid] when the estimate is not ready.
 *
 * @param isCoordinator Whether the local device is the elected leader/coordinator.
 * @param networkCentroid Fallback target.
 * @param warmupRounds Minimum history length required before estimates are used.
 * @return Leader target estimate or fallback centroid.
 */
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

/**
 * Aggregates neighbor-based target contributions at the coordinator.
 *
 * Each device contributes the latest position estimate it knows. The coordinator averages the
 * converged sums and falls back to [networkCentroid] when no estimate is available.
 *
 * @param estimationsHistory Local history of zebra position estimations.
 * @param isCoordinator Whether the local device is the converge-cast sink.
 * @param networkCentroid Fallback target when no contributions are available.
 * @return Averaged neighbor-based target or fallback centroid.
 */
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

/**
 * Computes the local device grid slot for the active coordination mode.
 *
 * Neighbor-based mode elects an information leader from [bound]. Leader-based mode reuses the
 * externally elected coordinator flag.
 *
 * @param isLeader Optional externally elected leader flag.
 * @param isCoordinator Whether the local device coordinates this round.
 * @param bound Election bound used in neighbor-based mode.
 * @param gridFormationValues Grid dimensions and spacing.
 * @return Zero-based grid index for the local device, or `-1` if no complete ordering is available.
 */
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

/**
 * Moves the local device toward its assigned grid destination.
 *
 * @param currentPosition Current local device position.
 * @param sharedTarget Target point spread by the coordinator.
 * @param gridIndex Local device slot in the grid ordering.
 * @param gridFormationValues Grid layout and movement settings.
 * @return Position reached after at most one movement step.
 */
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

/**
 * Computes a target point from the latest estimation history once enough history is available.
 *
 * @param warmupRounds Minimum history length required before a target is produced.
 * @return Average of the latest zebra estimates, or `null` when no target is ready.
 */
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
