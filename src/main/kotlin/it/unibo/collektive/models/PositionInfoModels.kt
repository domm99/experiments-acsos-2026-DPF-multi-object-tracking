package it.unibo.collektive.models

/**
 * Ground-truth information about a zebra tracked in the simulator.
 */
data class TrackedZebra(val zebraID: Int, val position: Point)

data class DistanceFromPosition(val currentPosition: Point, val distance: Double)

data class ZebraPositionHistory(val zebraID: Int, val positions: List<Point>)
