package it.unibo.collektive.stdlib.swarm

import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.sharing
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.Point
import it.unibo.collektive.stdlib.accumulation.convergeCast
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.spreading.hopGradientCast
import kotlin.math.roundToInt

@PublishedApi
internal data class CentroidAccumulator(val sumPoint: Point, val count: Int) {
    operator fun plus(other: CentroidAccumulator): CentroidAccumulator =
        CentroidAccumulator( Point(sumPoint.x + other.sumPoint.x, sumPoint.y + other.sumPoint.y), count + other.count)

    fun centroidOr(default: Point): Point = when (count) {
        0 -> default
        else -> Point(sumPoint.x / count, sumPoint.y / count)
    }
}

context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkCentroid(isLeader: Boolean? = null, bound: Int? = null): Point {
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
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.sensorEntries(
    isInformationLeader: Boolean,
): List<FieldEntry<ID, Point>> = convergeCast(
    local = listOf(FieldEntry(localId, position.selfPosition())),
    sink = isInformationLeader,
) { acc, next -> acc + next }

context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkGridIndex(
    bound: Int,
    gridFormationValues: GridFormationValues,
): Int {
    val isInformationLeader = boundedElection(bound = bound, strength = localId) == localId
    return networkGridIndex(isInformationLeader, gridFormationValues)
}

context(position: LocationSensor)
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.networkGridIndex(
    isInformationLeader: Boolean,
    gridFormationValues: GridFormationValues,
): Int {
    val sensedSensors = sensorEntries(isInformationLeader)
    return sharing(emptyList()) { previousOrdering ->
        val localStoredOrdering = previousOrdering.local.value
        val leaderOrdering = when {
            isInformationLeader -> {
                val sensedIds = sensedSensors.map { it.id }.toSet()
                when {
                    sensedIds.size != gridFormationValues.rows * gridFormationValues.cols -> emptyList()
                    localStoredOrdering.isNotEmpty() && localStoredOrdering.toSet() == sensedIds -> localStoredOrdering
                    else -> sensedSensors.toGridOrdering(gridFormationValues)
                }
            }
            else -> localStoredOrdering
        }
        val distributedOrdering = hopGradientCast(isInformationLeader, leaderOrdering)
        distributedOrdering.yielding {
            if (isEmpty()) {
                -1
            } else {
                indexOf(localId).takeIf { it >= 0 } ?: -1
            }
        }
    }
}

fun <ID : Comparable<ID>> List<FieldEntry<ID, Point>>.toGridOrdering(
    gridFormationValues: GridFormationValues,
): List<ID> {
    val uniqueSensors = distinctBy { it.id }
    if (uniqueSensors.isEmpty()) return emptyList()
    val centroid = uniqueSensors.map { it.value }.centroidOr(Point(0.0, 0.0))
    val rows = gridFormationValues.rows.coerceAtLeast(1)
    val cols = gridFormationValues.cols.coerceAtLeast(1)
    val spacing = gridFormationValues.spacing.takeIf { it > 0.0 } ?: 1.0
    return uniqueSensors
        .sortedWith(
            compareBy<FieldEntry<ID, Point>>(
                { sensor -> sensor.value.gridRowIndex(centroid, rows, spacing) },
                { sensor -> sensor.value.gridColumnIndex(centroid, cols, spacing) },
                { sensor -> sensor.value.y },
                { sensor -> sensor.value.x },
                { it.id },
            ),
        )
        .map { it.id }
}

/**
 * Computes the centroid of the known filter positions, or [default] if none are available yet.
 */
fun List<Point>.centroidOr(default: Point): Point = when {
    isEmpty() -> default
    else -> Point(x = sumOf { it.x } / size, y = sumOf { it.y } / size)
}

private fun Point.gridRowIndex(centroid: Point, rows: Int, spacing: Double): Int =
    (((y - centroid.y) / spacing) + (rows - 1) / 2.0).roundToInt().coerceIn(0, rows - 1)

private fun Point.gridColumnIndex(centroid: Point, cols: Int, spacing: Double): Int =
    (((x - centroid.x) / spacing) + (cols - 1) / 2.0).roundToInt().coerceIn(0, cols - 1)
