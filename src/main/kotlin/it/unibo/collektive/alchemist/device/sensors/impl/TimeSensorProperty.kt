package it.unibo.collektive.alchemist.device.sensors.impl

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.NodeProperty
import it.unibo.alchemist.model.Position
import it.unibo.collektive.alchemist.device.sensors.TimeSensor
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A property representing a time sensor for a node in an Alchemist environment.
 *
 * @param T the concentration type managed by the node
 * @param P the position type used in the environment
 * @property environment the simulation environment
 * @property node the node associated with this sensor property
 */
class TimeSensorProperty<T : Any, P : Position<P>>(
    private val environment: Environment<T, P>,
    override val node: Node<T>,
) : TimeSensor,
    NodeProperty<T> {
    override fun cloneOnNewNode(node: Node<T>): NodeProperty<T> = TimeSensorProperty(environment, node)

    override fun getTimeAsDouble(): Double = environment.simulation.time.toDouble()

    @OptIn(ExperimentalTime::class)
    override fun getTimeAsInstant(): Instant {
        val time: Double = environment.simulation.time.toDouble()
        return Instant.DISTANT_PAST + time.seconds
    }
}
