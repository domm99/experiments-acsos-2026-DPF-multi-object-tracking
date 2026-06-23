@file:Suppress("MagicNumber")

package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import java.io.IOException

/**
 * Exports the elected leader id to a CSV file at simulation end.
 *
 * @property seed simulation seed used in output filenames
 * @property numberOfNeighbors neighborhood size used in output filenames
 * @property dataPath destination directory for generated CSV files
 */
class ExportLeadersList<T>(val seed: Double, val dataPath: String) : OutputMonitor<T, Euclidean2DPosition> {

    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        try {
            val leaders = environment.nodes
                .filter { it.contains(SimpleMolecule("isLeader")) }

            exportToCsv(
                "$dataPath/leader_seed-$seed.csv",
                "leader",
                "%d",
                leaders.map { Line(it.id) },
            )
        } catch (e: IOException) {
            print(e.toString())
        }
    }
}
