package it.unibo.alchemist.model.monitors

import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.alchemist.model.positions.Euclidean2DPosition
import it.unibo.collektive.models.Point
import kotlin.collections.forEach

class ExportSensorsPositions<T>(val dataPath: String) :
    OutputMonitor<T, Euclidean2DPosition>  {

    override fun finished(environment: Environment<T?, Euclidean2DPosition>, time: Time, step: Long) {
        val filters = environment.nodes
            .filter { it.contains(SimpleMolecule("Filter")) }
            .filter { hasEstimations(it) }

        filters.forEach { filter ->
            val mid = filter.id
            val estimations = filter.getConcentration(
                SimpleMolecule("Positions"),
            ) as MutableList<Point>
            val id = filter.id
            exportToCsv(
                "$dataPath/positions_node-${mid}_node-${id}_errorOnPosition.csv",
                "estimatedX,estimatedY",
                "%.4f,%.4f",
                estimations.map { Line(it.x, it.y) },
            )

        }
    }

}
