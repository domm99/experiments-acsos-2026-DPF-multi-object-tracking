package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.alchemist.device.sensors.PresenceSensor

/**
 * A property representing a presence sensor for a node in an Alchemist environment.
 * The sensor detects the presence of targets that fulfill specific conditions, such as
 * containing a specified molecule, within a certain distance from the node.
 *
 * @param T the concentration type managed by the node
 * @param P the position type used in the environment
 * @property environment the simulation environment
 * @property node the node associated with this sensor property
 * @property blindSpotDistance the maximum distance at which the sensor can detect targets,
 *                              with no restrictions if set to Double.MAX_VALUE
 */
class PresenceSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
    private val blindSpotDistance: Double = Double.MAX_VALUE,
) : PresenceSensor,
    NodeProperty<T> {

    override fun isSensing(): Boolean = environment.nodes.filter { node ->
        node.contains(SimpleMolecule("Movable"))
    }.map { target ->
        val position = environment.getPosition(target)
        position.distanceTo(environment.getPosition(node)) <= blindSpotDistance
    }.firstOrNull { it } ?: false

    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> =
        PresenceSensorProperty(environment, node, blindSpotDistance)
}
