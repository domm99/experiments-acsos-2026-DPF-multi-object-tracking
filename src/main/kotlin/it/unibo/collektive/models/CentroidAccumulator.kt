package it.unibo.collektive.models

import kotlin.math.roundToInt

/**
 * Partial aggregate used to compute a centroid through distributed accumulation.
 *
 * @property sumPoint Sum of all contributed positions.
 * @property count Number of positions included in [sumPoint].
 */
@PublishedApi
internal data class CentroidAccumulator(val sumPoint: Point, val count: Int) {
    /**
     * Combines two partial centroid accumulators.
     */
    operator fun plus(other: CentroidAccumulator): CentroidAccumulator =
        CentroidAccumulator(Point(sumPoint.x + other.sumPoint.x, sumPoint.y + other.sumPoint.y), count + other.count)

    /**
     * Computes the centroid of the accumulated positions, or [default] when no positions were accumulated.
     */
    fun centroidOr(default: Point): Point = when (count) {
        0 -> default
        else -> Point(sumPoint.x / count, sumPoint.y / count)
    }
}

/**
 * Computes the centroid of the known filter positions, or [default] if none are available yet.
 *
 * @param default Position returned when the list is empty.
 * @return Arithmetic mean of the points, or [default].
 */
internal fun List<Point>.centroidOr(default: Point): Point = when {
    isEmpty() -> default
    else -> Point(x = sumOf { it.x } / size, y = sumOf { it.y } / size)
}

/**
 * Maps this point to the nearest grid row around [centroid].
 */
internal fun Point.gridRowIndex(centroid: Point, rows: Int, spacing: Double): Int =
    (((y - centroid.y) / spacing) + (rows - 1) / 2.0).roundToInt().coerceIn(0, rows - 1)

/**
 * Maps this point to the nearest grid column around [centroid].
 */
internal fun Point.gridColumnIndex(centroid: Point, cols: Int, spacing: Double): Int =
    (((x - centroid.x) / spacing) + (cols - 1) / 2.0).roundToInt().coerceIn(0, cols - 1)
