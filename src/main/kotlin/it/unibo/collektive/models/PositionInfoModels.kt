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
