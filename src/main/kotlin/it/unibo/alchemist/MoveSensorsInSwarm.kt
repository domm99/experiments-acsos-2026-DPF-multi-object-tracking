package it.unibo.alchemist

import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.actions.AbstractMoveNode
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.models.Point
import kotlin.math.sqrt

/**
 * Moves sensor nodes as a swarm arranged on a grid centered on the zebra group centroid.
 *
 * @property gridRows number of rows in the sensor grid formation
 * @property gridColumns number of columns in the sensor grid formation
 * @property spacing distance between adjacent grid positions
 */
class MoveSensorsInSwarm<T>(
    environment: Environment<T, Euclidean2DPosition>,
    node: Node<T>,
    val gridRows: Int,
    val gridColumns: Int,
    val spacing: Double,
) : AbstractMoveNode<T, Euclidean2DPosition>(environment, node, true) {

    private fun nextPositionFromMolecule(): Euclidean2DPosition? =
        when {
            node.contains(SimpleMolecule("NextPosition")) -> {
                @Suppress("UNCHECKED_CAST")
                val next = node.getConcentration(SimpleMolecule("NextPosition")) as? Point
                next?.let { Euclidean2DPosition(it.x, it.y) }
            }

            else -> null
        }

    private fun getCentroid(positions: List<Euclidean2DPosition>): Euclidean2DPosition {
        if (positions.isEmpty()) return Euclidean2DPosition(0.0, 0.0)

        val sumX = positions.sumOf { it.x }
        val sumY = positions.sumOf { it.y }
        val n = positions.size

        return Euclidean2DPosition(sumX / n, sumY / n)
    }

    override fun getNextPosition(): Euclidean2DPosition {
        nextPositionFromMolecule()?.let { return it }

        val zebras = environment.nodes.filter { it.contains(SimpleMolecule("Zebra")) }
        val currentPos = environment.getPosition(node)
        val targetPos = getCentroid(zebras.map { environment.getPosition(it) })
        val midInGrid = environment.nodes
            .filter { it.contains(SimpleMolecule("Filter")) }
            .map { it.id }
            .sorted()
            .indexOf(node.id)
            .takeIf { it >= 0 }
            ?: 0
        val stepSize = when {
            node.contains(SimpleMolecule("SwarmStepSize")) -> {
                @Suppress("UNCHECKED_CAST")
                node.getConcentration(SimpleMolecule("SwarmStepSize")) as? Double ?: 2.0
            }

            else -> 2.0
        }

        val c = midInGrid % gridColumns
        val r = midInGrid / gridColumns

        val offsetX = (c - (gridColumns - 1) / 2.0) * spacing
        val offsetY = (r - (gridRows - 1) / 2.0) * spacing

        val finalDestX = targetPos.x + offsetX
        val finalDestY = targetPos.y + offsetY

        val dx = finalDestX - currentPos.x
        val dy = finalDestY - currentPos.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < stepSize) {
            return Euclidean2DPosition(finalDestX, finalDestY)
        }

        return Euclidean2DPosition(
            currentPos.x + (dx / distance) * stepSize,
            currentPos.y + (dy / distance) * stepSize,
        )
    }

    override fun cloneAction(p0: Node<T?>?, p1: Reaction<T?>?): Action<T?> =
        MoveSensorsInSwarm(environment, node, gridRows, gridColumns, spacing)
}
