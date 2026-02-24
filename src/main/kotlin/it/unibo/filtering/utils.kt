package it.unibo.filtering

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition

/**
 * Converts a generic position in the environment to a [Euclidean2DPosition].
 */
fun Position<*>.toEuclidean2DPosition(): Euclidean2DPosition = Euclidean2DPosition(coordinates[0], coordinates[1])

/**
 * Gets the current position of the device in the environment as a [Euclidean2DPosition].
 */
fun CollektiveDevice<*>.coordinates(): Euclidean2DPosition = environment.getPosition(node).toEuclidean2DPosition()

/**
 * Gets the current position of the target device in the environment as a [Euclidean2DPosition].
 */
fun CollektiveDevice<*>.getTargetPosition(): Euclidean2DPosition {
    val node = environment.nodes.first { it.contains(SimpleMolecule("Movable")) }
    return environment.getPosition(node).toEuclidean2DPosition()
}
