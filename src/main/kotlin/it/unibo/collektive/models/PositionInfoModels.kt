package it.unibo.collektive.models

/**
 * Ground-truth information about a zebra tracked in the simulator.
 *
 * @property zebraID Unique identifier of the zebra.
 * @property position Current true position of the zebra.
 */
data class TrackedZebra(val zebraID: Int, val position: Point)

/**
 * Couples a reference position with a scalar distance value.
 *
 * Typically used to represent a sensor observation where [distance]
 * is measured from [currentPosition] to a target.
 *
 * @property currentPosition Position of the reference point (e.g., sensor).
 * @property distance Measured distance from [currentPosition] to the target.
 */
data class DistanceFromPosition(val currentPosition: Point, val distance: Double)

/**
 * Ordered position history for a specific zebra.
 *
 * @property zebraID Unique identifier of the zebra whose trajectory is stored.
 * @property positions Recorded trajectory points, in chronological order.
 */
data class ZebraPositionHistory(val zebraID: Int, val positions: List<Point>)

/**
 * Checks whether all tracked zebras have accumulated at least [minimumHistoryLength] estimations.
 *
 * @return `true` only when every zebra history is long enough.
 */
fun List<ZebraPositionHistory>.hasEnoughHistory(minimumHistoryLength: Int): Boolean =
    isNotEmpty() && all { it.positions.size >= minimumHistoryLength }

/**
 * Aggregates the latest position contribution available from each zebra history.
 *
 * @return The summed coordinates and contribution count derived from the latest known positions.
 */
fun List<ZebraPositionHistory>.latestContribution(): EstimationContribution {
    val latestPositions = mapNotNull { it.positions.lastOrNull() }
    return EstimationContribution(
        sumX = latestPositions.sumOf { it.x },
        sumY = latestPositions.sumOf { it.y },
        count = latestPositions.size,
    )
}

/**
 * Partial aggregate used to compute a centroid from distributed zebra estimates.
 *
 * @property sumX Sum of the x coordinates contributed by the latest estimates.
 * @property sumY Sum of the y coordinates contributed by the latest estimates.
 * @property count Number of estimates included in the sums.
 */
data class EstimationContribution(val sumX: Double, val sumY: Double, val count: Int)
