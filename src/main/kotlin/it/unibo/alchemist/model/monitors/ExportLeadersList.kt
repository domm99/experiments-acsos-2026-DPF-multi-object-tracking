package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.io.File
import java.util.Locale

class ExportLeadersList<T>(val seed: Double, val numberOfNeighbors: Int, val dataPath: String) :
    OutputMonitor<T, Euclidean2DPosition> {

    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        val leader = environment.nodes
            .first { it.getConcentration(SimpleMolecule("isLeader")) as Boolean }

        val leaderId = leader.id

        exportToCsv(
            "$dataPath/leader_n-${numberOfNeighbors}_seed-$seed.csv",
            "leader",
            "%d",
            listOf(Line(12), Line(leaderId)),
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
