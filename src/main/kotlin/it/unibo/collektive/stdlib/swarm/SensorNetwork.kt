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

context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.sensorsLocation(isInformationLeader: Boolean): Map<ID, Point> =
    convergeCast(
        local = mapOf(localId to position.selfPosition()),
        sink = isInformationLeader,
    ) { acc, next -> acc + next }

context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkGridIndex(
    bound: Int,
    gridFormationValues: GridFormationValues,
): Int {
    val isInformationLeader = isBoundedElectionWinner(localId, bound)
    return networkGridIndex(isInformationLeader, gridFormationValues)
}

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
