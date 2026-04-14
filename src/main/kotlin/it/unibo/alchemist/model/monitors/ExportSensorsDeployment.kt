package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition

/**
 * Exports the estimations made by filter nodes to CSV files upon simulation completion.
 *
 * @property seed simulation seed used in output filenames
 * @property numberOfNeighbors neighborhood size used in output filenames
 * @property dataPath destination directory for generated CSV files
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

}
