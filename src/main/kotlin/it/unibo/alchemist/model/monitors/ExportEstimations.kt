@file:Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")

package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.models.ZebraPositionHistory
import java.io.File

/**
 * Checks whether a node contains at least one stored estimation.
 *
 * @param node node that may carry the `Estimations` molecule
 * @return `true` if the node stores a non-empty estimations list
 */
@Suppress("UNCHECKED_CAST")
fun <T> hasEstimations(node: Node<T>): Boolean {
    val e = node.getConcentration(SimpleMolecule("Estimations")) as? MutableList<ZebraPositionHistory>
        ?: mutableListOf()
    return e.isNotEmpty()
}

/**
 * Exports the estimations made by filter nodes to CSV files upon simulation completion.
 *
 * @property seed simulation seed used in output filenames
 * @property numberOfNeighbors neighborhood size used in output filenames
 * @property path destination directory for generated CSV files
 */
class ExportEstimations<T>(val seed: Double, val numberOfNeighbors: Int, val path: String) :
    OutputMonitor<T, Euclidean2DPosition> {

    @Suppress("UNCHECKED_CAST")
    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        try {
            val outputDir = File(path)
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                error("Cannot create output directory: $path")
            }

            val filters = environment.nodes
                .filter { it.contains(SimpleMolecule("Filter")) }
                .filter { hasEstimations(it) }

            filters.forEach { filter ->
                val estimations = filter.getConcentration(
                    SimpleMolecule("Estimations"),
                ) as MutableList<ZebraPositionHistory>
                val id = filter.id
                estimations.forEach { estimation ->
                    exportToCsv(
                        "$path/estimations_zebra${estimation.zebraID}_node-${id}_n-${numberOfNeighbors}_seed-$seed.csv",
                        "estimatedX,estimatedY",
                        "%.4f,%.4f",
                        estimation.positions.map { Line(it.x, it.y) },
                    )
                }
            }
            println("Export Estimations finished at $path")
        } catch (e: Exception) {
            println(e.message)
        }
    }
}
