package it.unibo.collektive.alchemist.device.sensors

import it.unibo.filtering.Point

/**
 * A sensor that provides location-related information within the environment.
 */
interface LocationSensor {
    /**
     * Returns the coordinates of the node's position inside the environment.
     */
    fun coordinates(): Point

    /**
     * Returns the coordinates of the neighborhood.
     */
    fun surroundings(): List<Point>

    /**
     * Returns position(s) of the targets in the environment.
     */
    fun targetsPosition(): List<Point>

    /**
     * Returns position of the device in the environment.
     */
    fun selfPosition(): Point
}
