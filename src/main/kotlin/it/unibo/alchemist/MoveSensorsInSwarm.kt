package it.unibo.alchemist

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.actions.AbstractMoveNode
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.models.Point

/**
 * Moves sensor nodes according to the next position computed by the collective program.
 *
 * Grid parameters are kept in the constructor only for backward compatibility with the YAML action signature.
 *
 * @property gridRows Number of formation rows passed by the YAML action signature.
 * @property gridColumns Number of formation columns passed by the YAML action signature.
 * @property spacing Formation spacing passed by the YAML action signature.
 */
class MoveSensorsInSwarm<T>(
    environment: Environment<T, Euclidean2DPosition>,
    node: Node<T>,
    val gridRows: Int,
    val gridColumns: Int,
    val spacing: Double,
) : AbstractMoveNode<T, Euclidean2DPosition>(environment, node, true) {

    private fun nextPositionFromMolecule(): Euclidean2DPosition? = when {
        node.contains(SimpleMolecule("NextPosition")) -> {
            @Suppress("UNCHECKED_CAST")
            val nextPoint = node.getConcentration(SimpleMolecule("NextPosition")) as? Point
            nextPoint?.let { Euclidean2DPosition(it.x, it.y) }
        }
        else -> null
    }

    override fun getNextPosition(): Euclidean2DPosition = nextPositionFromMolecule() ?: environment.getPosition(node)

    override fun cloneAction(p0: Node<T?>?, p1: Reaction<T?>?): Action<T?> =
        MoveSensorsInSwarm(environment, node, gridRows, gridColumns, spacing)
}
