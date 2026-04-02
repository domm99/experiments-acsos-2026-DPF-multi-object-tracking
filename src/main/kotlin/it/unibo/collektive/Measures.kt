package it.unibo.collektive

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.distanceTo
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

const val P_0 = -40
const val PATH_LOSS = 2
const val MEASURE_STDEV = 0.5

fun fromPositionToMeasure(selfPosition: Point, sensedPosition: Point, random: RandomGenerator): Double {
    val distance = hypot(sensedPosition.x - selfPosition.x, sensedPosition.y - selfPosition.y)
    val measure = P_0 - 10 * PATH_LOSS * log10(distance) + random.nextGaussian() * MEASURE_STDEV
    return measure
}

fun <T, P : Position<P>> Environment<T, P>.distanceFromNetworkCentroid(position: Point): Double {
    val filtersNode = this.nodes.filter { it.contains(SimpleMolecule("Filter")) }
    val sum = filtersNode.fold(0.0 to 0.0) { acc, next ->
        val nextNodePos = this.getPosition(next).coordinates // Add 10 to avoid negative positions .map { it + 10 }
        acc.first + nextNodePos[0] to acc.second + nextNodePos[1]
    }
    val filtersCount = filtersNode.size
    val center = Point(sum.first / filtersCount, sum.second / filtersCount)
    return center.distanceTo(position) // the smallest, the closest
}

fun centralityWeight(distanceFromCentroid: Double, sigma: Double): Double =
    exp(-(distanceFromCentroid * distanceFromCentroid) / (2 * sigma * sigma))
