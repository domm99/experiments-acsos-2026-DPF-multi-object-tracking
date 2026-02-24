package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Node
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.filtering.Point
import it.unibo.filtering.Particle
import java.io.File
import java.util.Locale

class Line(vararg val values: Any)

fun <T> hasEstimations(node: Node<T>): Boolean {
    val e = node.getConcentration(SimpleMolecule("Estimations")) as? MutableList<Point>
        ?: mutableListOf()
    return e.isNotEmpty()
}

/**
 * Exports the estimations made by filter nodes to CSV files upon simulation completion.
 */
class ExportEstimations<T>(val seed: Double, val numberOfNeighbors: Int, val dataPath: String) : OutputMonitor<T, Euclidean2DPosition> {

    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        try{
            val filters = environment.nodes
                .filter { it.contains(SimpleMolecule("Filter")) }
                .filter { hasEstimations(it) }

            filters.forEach { filter ->
                val estimations = filter.getConcentration(SimpleMolecule("Estimations")) as MutableList<Point>
                val id = filter.id
                exportToCsv(
                    "$dataPath/estimations_node-${id}_n-${numberOfNeighbors}_seed-$seed.csv",
                    "estimatedX,estimatedY",
                    "%.4f,%.4f",
                    estimations.map { Line(it.x, it.y) }
                )

//                val numberOfParticles = filter.getConcentration(SimpleMolecule("NumberOfParticles")) as Int
//                val particles =
//                    filter.getConcentration(SimpleMolecule("Particles")) as MutableList<MutableList<Particle>>
//                val header =
//                    (0 until numberOfParticles).joinToString(",") { "p_$it-X,p_$it-Y,p_$it-vX,p_$it-vY,p_$it-W" }
//                val format = (0 until numberOfParticles).joinToString(",") { "%.4f,%.4f,%.4f,%.4f,%.4f" }
//                val hist = particles.map { particleList ->
//                    val coordinates =
//                        particleList.flatMap { listOf(it.x, it.y, it.vx, it.vy, it.weight) }.toTypedArray()
//                    Line(*coordinates)
//                }
//                exportToCsv(
//                    "$dataPath/particles_node-${id}_n-${numberOfNeighbors}_seed-$seed.csv",
//                    header,
//                    format,
//                    hist
//                )
            }
        }catch (e: Exception) {
            println(e.message)
        }
    }

    /**
     * Exports the given history of points to a CSV file.
     * @param filename the name of the file to export to
     * @param history the list of points representing the history of estimations
     */
    fun exportToCsv(filename: String, header: String, format: String, history: List<Line>) {
        File(filename).printWriter().use { out ->
            // Header
            out.println(header)

            // Data
            history.forEach { step ->
                val line = String.format(
                    Locale.US,
                    format,
                    *step.values
                )
                out.println(line)
            }
        }
    }
}
