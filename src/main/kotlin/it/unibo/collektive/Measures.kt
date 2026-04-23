package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.molecules.SimpleMolecule
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.distanceTo
import it.unibo.collektive.stdlib.swarm.GridFormationValues
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import org.apache.commons.math3.random.RandomGenerator

/**
 * Reference RSS value (dBm) at 1 meter used by the path-loss model.
 */
const val P_0 = -40

/**
 * Path-loss exponent used in the log-distance propagation model.
 */
const val PATH_LOSS = 2

/**
 * Standard deviation of Gaussian measurement noise added to simulated RSS values.
 */
const val MEASURE_STDEV = 0.5

/**
 * Converts two positions into a noisy RSS-like measurement using a log-distance path-loss model.
 *
 * Formula:
 * `P(d) = P_0 - 10 * PATH_LOSS * log10(d) + N(0, MEASURE_STDEV^2)`
 *
 * @param selfPosition Position of the measuring node/sensor.
 * @param sensedPosition Position of the target node being measured.
 * @param random Random generator used to sample Gaussian noise.
 * @return Simulated noisy measurement value.
 */
fun fromPositionToMeasure(selfPosition: Point, sensedPosition: Point, random: RandomGenerator): Double {
    val distance = hypot(sensedPosition.x - selfPosition.x, sensedPosition.y - selfPosition.y)
    val measure = P_0 - 10 * PATH_LOSS * log10(distance) + random.nextGaussian() * MEASURE_STDEV
    return measure
}

/**
 * Computes the distance between a point and the centroid of all nodes marked as `"Filter"`.
 *
 * The centroid is computed as the arithmetic mean of filter node coordinates in the environment.
 *
 * @param position The point whose distance from the filter-network centroid is required.
 * @return Euclidean distance from [position] to the centroid of filter nodes.
 */
fun <T, P : Position<P>> Environment<T, P>.distanceFromNetworkCentroid(position: Point): Double {
    val filtersNode = this.nodes.filter { it.contains(SimpleMolecule("Filter")) }
    val sum = filtersNode.fold(0.0 to 0.0) { acc, next ->
        val nextNodePos = this.getPosition(next).coordinates
        acc.first + nextNodePos[0] to acc.second + nextNodePos[1]
    }
    val filtersCount = filtersNode.size
    val center = Point(sum.first / filtersCount, sum.second / filtersCount)
    return center.distanceTo(position) // the smallest, the closest
}

/**
 * Computes a Gaussian centrality weight from distance to centroid.
 *
 * Weight decreases as distance grows:
 * `w = exp(-d^2 / (2 * sigma^2))`
 *
 * @param distanceFromCentroid Distance from the centroid.
 * @param sigma Spread parameter of the Gaussian kernel.
 * @return Centrality weight in the range `(0, 1]` for `sigma > 0`.
 */
fun centralityWeight(distanceFromCentroid: Double, sigma: Double): Double =
    exp(-(distanceFromCentroid * distanceFromCentroid) / (2 * sigma * sigma))

/**
 * Computes the grid slot assigned to a sensor around a target point.
 *
 * @param target Center point of the grid formation.
 * @param gridIndex Zero-based index of the sensor in the grid ordering.
 * @param gridFormationValues Grid dimensions and spacing used to place the sensor.
 * @return The destination point corresponding to the sensor grid slot.
 */
fun gridDestination(target: Point, gridIndex: Int, gridFormationValues: GridFormationValues): Point {
    val column = gridIndex % gridFormationValues.cols
    val row = gridIndex / gridFormationValues.cols
    val offsetX = (column - (gridFormationValues.cols - 1) / 2.0) * gridFormationValues.spacing
    val offsetY = (row - (gridFormationValues.rows - 1) / 2.0) * gridFormationValues.spacing
    return Point(target.x + offsetX, target.y + offsetY)
}

/**
 * Advances a point toward a destination by at most a fixed step size.
 *
 * @param current Starting point.
 * @param destination Target point to approach.
 * @param stepSize Maximum movement length for this step.
 * @return The destination if it is within range, otherwise the intermediate point on the path.
 */
fun moveTowards(current: Point, destination: Point, stepSize: Double): Point {
    val distance = current.distanceTo(destination)
    if (distance <= stepSize) {
        return destination
    }
    val ratio = stepSize / distance
    return Point(
        x = current.x + (destination.x - current.x) * ratio,
        y = current.y + (destination.y - current.y) * ratio,
    )
}
