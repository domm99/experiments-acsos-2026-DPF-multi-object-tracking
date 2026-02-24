package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.filtering.Point
import org.apache.commons.math3.random.RandomGenerator

/**
 * An implementation of a location sensor property for nodes in an Alchemist environment.
 *
 * @param T the concentration type managed by the node
 * @param P the position type used in the environment
 * @property environment the simulation environment
 * @property node the node associated with this sensor property
 * @property random an instance of a random number generator for simulating measurement noise
 * @property stdDev the standard deviation for Gaussian noise added to target positions
 */
class LocationSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
    private val random: RandomGenerator,
) : LocationSensor,
    NodeProperty<T> {

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> =
        LocationSensorProperty(environment, node, random)

    override fun coordinates(): Point {
        val position = environment.getPosition(node).coordinates
        return Point(position[0], position[1])
    }

    override fun surroundings(): List<Point> = environment.getNeighborhood(node).map { node ->
        environment.getPosition(node).coordinates.let { Point(it[0], it[1]) }
    }

    override fun targetsPosition(): List<Point> = environment.nodes
        .filter { node ->
            node.contains(SimpleMolecule("Movable"))
        }.map { target ->
            val position = environment.getPosition(target)
            val newX = position.coordinates[0]
            val newY = position.coordinates[1]
            Point(newX, newY)
        }

    override fun selfPosition(): Point {
        val selfPos = environment.getPosition(node)
        return Point(selfPos.coordinates[0], selfPos.coordinates[1])
    }

}

