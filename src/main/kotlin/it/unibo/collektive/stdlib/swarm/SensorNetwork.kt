@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters.

package it.unibo.collektive.stdlib.swarm

import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.CentroidAccumulator
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.centroidOr
import it.unibo.collektive.models.gridColumnIndex
import it.unibo.collektive.models.gridRowIndex
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.election.isBoundedElectionWinner
import it.unibo.collektive.stdlib.spreading.hopGradientCast

/**
 * Computes the centroid of the sensor network and spreads it back to all devices.
 *
 * If [isLeader] is provided, that device acts as the collection sink. Otherwise a temporary
 * information leader is elected through [boundedElection] using [bound].
 *
 * @param isLeader Optional externally computed collection sink flag.
 * @param bound Election bound used when [isLeader] is not provided.
 * @return The current network centroid known by the local device.
 */
context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkCentroid(
    isLeader: Boolean? = null,
    bound: Int? = null,
): Point {
    val isInformationLeader = when {
        isLeader == null -> {
            requireNotNull(bound)
            boundedElection(bound = bound, strength = localId) == localId
        }
        else -> isLeader
    }
    val currentPosition = position.selfPosition()
    val accumulated = convergeCast(
        CentroidAccumulator(currentPosition, 1),
        isInformationLeader,
    ) { acc, next -> acc + next }
    val centroid = accumulated.centroidOr(currentPosition)
    return hopGradientCast(isInformationLeader, centroid)
}

/**
 * Collects the latest sensor positions at the information leader.
 *
 * Non-leader devices receive the local result of [convergeCast], while the leader receives
 * the full map of sensor identifiers to positions.
 *
 * @param isInformationLeader Whether the local device is the converge-cast sink.
 * @return Map of sensor identifiers to positions visible at this point of the aggregate computation.
 */
context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.sensorsLocation(isInformationLeader: Boolean): Map<ID, Point> =
    convergeCast(
        local = mapOf(localId to position.selfPosition()),
        sink = isInformationLeader,
    ) { acc, next -> acc + next }

/**
 * Computes the local device index in the distributed grid ordering.
 *
 * The information leader is elected from the local id and [bound], then the overload accepting
 * [isInformationLeader] performs the ordering and dissemination.
 *
 * @param bound Election bound used to choose the device that orders the grid.
 * @param gridFormationValues Grid dimensions and spacing.
 * @return Zero-based grid index for the local device, or `-1` while no stable full ordering is available.
 */
context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkGridIndex(
    bound: Int,
    gridFormationValues: GridFormationValues,
): Int {
    val isInformationLeader = isBoundedElectionWinner(localId, bound)
    return networkGridIndex(isInformationLeader, gridFormationValues)
}

/**
 * Computes the local device index in the distributed grid ordering using an existing information leader.
 *
 * The leader orders all sensed devices by their projected grid row and column, keeps a previous
 * ordering when it still contains the same ids, and spreads the ordering to the rest of the network.
 *
 * @param isInformationLeader Whether the local device orders and disseminates the grid.
 * @param gridFormationValues Grid dimensions and spacing.
 * @return Zero-based grid index for the local device, or `-1` while no stable full ordering is available.
 */
context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkGridIndex(
    isInformationLeader: Boolean,
    gridFormationValues: GridFormationValues,
): Int {
    val sensedSensors = sensorsLocation(isInformationLeader)
    return evolving(emptyList()) { previousOrdering ->
        val currentOrdering = when {
            isInformationLeader -> {
                val sensedIds = sensedSensors.map { it.key }.toSet()
                when {
                    sensedIds.size != gridFormationValues.rows * gridFormationValues.cols -> emptyList()
                    previousOrdering.isNotEmpty() && previousOrdering.toSet() == sensedIds -> previousOrdering
                    else -> sensedSensors.toGridOrdering(gridFormationValues)
                }
            }
            else -> previousOrdering
        }
        hopGradientCast(isInformationLeader, currentOrdering).yielding {
            when {
                isEmpty() -> -1
                else -> indexOf(localId).takeIf { it >= 0 } ?: -1
            }
        }
    }
}

/**
 * Orders sensor ids by the grid slot that best matches their current position.
 *
 * Positions are projected onto a grid centered on their centroid. Ties are broken by y coordinate,
 * x coordinate, and finally id to keep the result deterministic.
 *
 * @param gridFormationValues Grid dimensions and spacing used for projection.
 * @return Sensor identifiers ordered by their assigned grid slot.
 */
fun <ID : Comparable<ID>> Map<ID, Point>.toGridOrdering(gridFormationValues: GridFormationValues): List<ID> {
    if (isEmpty()) return emptyList()
    val centroid = map { it.value }.centroidOr(Point(0.0, 0.0))
    val rows = gridFormationValues.rows.coerceAtLeast(1)
    val cols = gridFormationValues.cols.coerceAtLeast(1)
    val spacing = gridFormationValues.spacing.takeIf { it > 0.0 } ?: 1.0
    return toList().sortedWith(
        compareBy<Pair<ID, Point>>(
            { pair -> pair.second.gridRowIndex(centroid, rows, spacing) },
            { pair -> pair.second.gridColumnIndex(centroid, cols, spacing) },
            { pair -> pair.second.y },
            { pair -> pair.second.x },
            { it.first },
        ),
    ).map { it.first }
}
