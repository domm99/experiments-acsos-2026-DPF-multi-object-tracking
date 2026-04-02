package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.io.File
import java.util.Locale

/**
 * Exports the estimations made by filter nodes to CSV files upon simulation completion.
 */
class ExportSensorsDeployment<T>(val seed: Double, val numberOfNeighbors: Int, val dataPath: String) :
    OutputMonitor<T, Euclidean2DPosition> {

    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        val filters = environment.nodes
            .filter { it.contains(SimpleMolecule("Filter")) }
            .filter { hasEstimations(it) }

        val positions = filters
            .map {
                val position = environment.getPosition(it)
                val numberOfNeighbors = environment.getNeighborhood(it).size()
                val id = it.id
                Triple(id, position, numberOfNeighbors)
            }

        exportToCsv(
            "$dataPath/sensors-positions_n-${numberOfNeighbors}_seed-$seed.csv",
            "id,X,Y,NumberOfNeighbors",
            "%d,%.4f,%.4f,%d",
            positions.map { Line(it.first, it.second[0], it.second[1], it.third) },
        )
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
                    *step.values,
                )
                out.println(line)
            }
        }
    }
}
