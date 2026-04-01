package it.unibo.alchemist

import java.io.File
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Action
import it.unibo.alchemist.model.Reaction
import it.unibo.alchemist.model.Environment
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import it.unibo.alchemist.model.TerminationPredicate
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.actions.AbstractMoveNode
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.alchemist.model.terminators.AfterTime
import java.io.FileNotFoundException

/**
 * Represents a movement action that updates the position of a node within a Euclidean 2D environment.
 * This class defines the motion dynamics, including velocity, boundaries, and trajectory behavior.
 *
 * @param T the concentration type managed by the node
 * @param environment the simulation environment in which the node resides
 * @param node the node under movement
 * @param movementCsvPath the path of the CSV file containing the movement trajectory
 */
class MoveNode<T>(
    environment: Environment<T, Euclidean2DPosition>,
    node: Node<T>,
    val movementCsvPath: String,
) : AbstractMoveNode<T, Euclidean2DPosition>(environment, node, true) {

    private var step: Int = 0

    override fun getNextPosition(): Euclidean2DPosition? {
        val file = this::class.java.classLoader.getResourceAsStream(movementCsvPath)
            ?: throw FileNotFoundException("Cannot find movement csv file $movementCsvPath")
        return csvReader().open(file) {
            val regex = Regex("\\d{3,}")
            val row = readAllWithHeaderAsSequence().elementAtOrNull(step)
            if (row != null) {
                val x = row["x"]
                val y = row["y"]
                if (x != null && y != null) {
                    // take the ID from the filename
                    val zebraID = regex.find(movementCsvPath)?.groupValues?.first()
                    node.setConcentration(SimpleMolecule("ZebraID"), zebraID as T?)
                    node.setConcentration(SimpleMolecule("PositionX"), x.toDouble() as T?)
                    node.setConcentration(SimpleMolecule("PositionY"), y.toDouble() as T?)
                    step += 1
                    Euclidean2DPosition(x.toDouble(), y.toDouble())
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override fun cloneAction(
        p0: Node<T?>?,
        p1: Reaction<T?>?
    ): Action<T?> =
        MoveNode(environment, node, movementCsvPath)

}
