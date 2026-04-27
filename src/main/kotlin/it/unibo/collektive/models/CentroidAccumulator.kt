package it.unibo.collektive.models

import kotlin.math.roundToInt

data class CentroidAccumulator(val sumPoint: Point, val count: Int) {
    operator fun plus(other: CentroidAccumulator): CentroidAccumulator =
        CentroidAccumulator(Point(sumPoint.x + other.sumPoint.x, sumPoint.y + other.sumPoint.y), count + other.count)

    fun centroidOr(default: Point): Point = when (count) {
        0 -> default
        else -> Point(sumPoint.x / count, sumPoint.y / count)
    }
}

/**
 * Computes the centroid of the known filter positions, or [default] if none are available yet.
 */
fun List<Point>.centroidOr(default: Point): Point = when {
    isEmpty() -> default
    else -> Point(x = sumOf { it.x } / size, y = sumOf { it.y } / size)
}

fun Point.gridRowIndex(centroid: Point, rows: Int, spacing: Double): Int =
    (((y - centroid.y) / spacing) + (rows - 1) / 2.0).roundToInt().coerceIn(0, rows - 1)

fun Point.gridColumnIndex(centroid: Point, cols: Int, spacing: Double): Int =
    (((x - centroid.x) / spacing) + (cols - 1) / 2.0).roundToInt().coerceIn(0, cols - 1)
