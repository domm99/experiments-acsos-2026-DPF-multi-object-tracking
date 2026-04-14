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
import kotlin.collections.List
import org.danilopianini.util.ListSet
import org.danilopianini.util.ListSets

/**
 * Global reaction that simulates a one-time leader failure in the environment.
 *
 * On first execution, it searches for the node whose `"isLeader"` concentration is `true`,
 * then marks it as down (`"isDown" = true`) and no longer leader (`"isLeader" = false`).
 * Subsequent executions do not repeat this state change.
 *
 * @param T Concentration type used by the simulation.
 * @param P Position type used by the environment.
 * @property environment Simulation environment containing all nodes.
 * @property distribution Time distribution driving this reaction scheduling.
 */
class KillLeader<T, P : Position<P>>(val environment: Environment<T, P>, val distribution: TimeDistribution<T>) :
    GlobalReaction<T> {

    /**
     * Guard flag ensuring leader shutdown is performed once.
     */
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

    /**
     * Performs the one-shot leader failure transition.
     *
     * First matching node with `"isLeader" == true` is updated:
     * - `"isDown"` set to `true`
     * - `"isLeader"` set to `false`
     *
     * If already executed once, this method is a no-op.
     */
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

    override fun initializationComplete(atTime: Time, environment: Environment<T, *>) = Unit

    override fun update(currentTime: Time, hasBeenExecuted: Boolean, environment: Environment<T, *>) = Unit

    override fun compareTo(other: Actionable<T>): Int = tau.compareTo(other.tau)

    /**
     * Utility accessor returning all environment nodes as a list.
     */
    private val nodes: List<Node<T>>
        get() =
            environment.nodes
                .iterator()
                .asSequence()
                .toList()
}
