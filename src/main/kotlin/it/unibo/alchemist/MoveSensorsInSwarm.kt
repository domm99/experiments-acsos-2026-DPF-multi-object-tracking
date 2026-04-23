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

    private fun nextPositionFromMolecule(): Euclidean2DPosition? = when {
        node.contains(SimpleMolecule("NextPosition")) -> {
            @Suppress("UNCHECKED_CAST")
            val nextPoint = node.getConcentration(SimpleMolecule("NextPosition")) as? Point
            nextPoint?.let { Euclidean2DPosition(it.x, it.y) }
        }
        else -> null
    }

    private fun getCentroid(positions: List<Euclidean2DPosition>): Euclidean2DPosition {
        if (positions.isEmpty()) return Euclidean2DPosition(0.0, 0.0)
        val totalX = positions.sumOf { it.x }
        val totalY = positions.sumOf { it.y }
        val positionsCount = positions.size
        return Euclidean2DPosition(totalX / positionsCount, totalY / positionsCount)
    }

    override fun getNextPosition(): Euclidean2DPosition = nextPositionFromMolecule() ?: run {
        val zebraNodes = environment.nodes.filter { it.contains(SimpleMolecule("Zebra")) }
        val currentPosition = environment.getPosition(node)
        val swarmCentroid = getCentroid(zebraNodes.map { environment.getPosition(it) })
        val sensorIndexInGrid = environment.nodes
            .filter { it.contains(SimpleMolecule("Filter")) }
            .map { it.id }.sorted().indexOf(node.id).takeIf { it >= 0 } ?: 0
        val movementStepSize = when {
            node.contains(SimpleMolecule("SwarmStepSize")) -> {
                @Suppress("UNCHECKED_CAST")
                node.getConcentration(SimpleMolecule("SwarmStepSize")) as? Double ?: 2.0
            }
            else -> 2.0
        }
        val gridColumnIndex = sensorIndexInGrid % gridColumns
        val gridRowIndex = sensorIndexInGrid / gridColumns
        val horizontalOffset = (gridColumnIndex - (gridColumns - 1) / 2.0) * spacing
        val verticalOffset = (gridRowIndex - (gridRows - 1) / 2.0) * spacing
        val destinationX = swarmCentroid.x + horizontalOffset
        val destinationY = swarmCentroid.y + verticalOffset
        val deltaX = destinationX - currentPosition.x
        val deltaY = destinationY - currentPosition.y
        val distanceToDestination = sqrt(deltaX * deltaX + deltaY * deltaY)
        if (distanceToDestination < movementStepSize) {
            Euclidean2DPosition(destinationX, destinationY)
        } else {
            Euclidean2DPosition(
                currentPosition.x + (deltaX / distanceToDestination) * movementStepSize,
                currentPosition.y + (deltaY / distanceToDestination) * movementStepSize,
            )
        }
    }

    override fun cloneAction(p0: Node<T?>?, p1: Reaction<T?>?): Action<T?> =
        MoveSensorsInSwarm(environment, node, gridRows, gridColumns, spacing)
}
