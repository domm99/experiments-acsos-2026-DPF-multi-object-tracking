package it.unibo.alchemist.model.implementations.reactions

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Condition
import it.unibo.alchemist.model.Dependency
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GlobalReaction
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.TimeDistribution
import it.unibo.alchemist.model.molecules.SimpleMolecule
import org.danilopianini.util.ListSet
import org.danilopianini.util.ListSets
import kotlin.collections.List

class KillLeader<T, P: Position<P>> (
    val environment: Environment<T, P>,
    val distribution: TimeDistribution<T>
) : GlobalReaction<T> {

    private var executed = false

    override var actions: List<Action<T>> = mutableListOf()
        set(value) {
            field = listOf(*value.toTypedArray())
        }

    override var conditions: List<Condition<T>> = mutableListOf()
        set(value) {
            field = listOf(*value.toTypedArray())
        }

    override val rate: Double
        get() = distribution.getRate()

    override val tau: Time
        get() = distribution.nextOccurence

    override val inboundDependencies: ListSet<out Dependency> = ListSets.emptyListSet()

    override val outboundDependencies: ListSet<out Dependency> = ListSets.emptyListSet()

    override val timeDistribution: TimeDistribution<T> = distribution

    override fun execute() {
        executeBeforeUpdateDistribution()
        distribution.update(timeDistribution.getNextOccurence(), true, rate, environment)
    }

    fun executeBeforeUpdateDistribution() {
        if (!executed) {
            executed = true
            val leader = nodes
                .first { it.getConcentration(SimpleMolecule("isLeader")) as Boolean }
            leader.setConcentration(SimpleMolecule("isDown"), true as T)
            leader.setConcentration(SimpleMolecule("isLeader"), false as T)
        }
    }

    override fun canExecute(): Boolean = true

    override fun initializationComplete(
        atTime: Time,
        environment: Environment<T, *>,
    ) = Unit

    override fun update(
        currentTime: Time,
        hasBeenExecuted: Boolean,
        environment: Environment<T, *>,
    ) = Unit

    override fun compareTo(other: Actionable<T>): Int = tau.compareTo(other.tau)

    // Utility methods
    private val nodes: List<Node<T>>
        get() =
            environment.nodes
                .iterator()
                .asSequence()
                .toList()
}
