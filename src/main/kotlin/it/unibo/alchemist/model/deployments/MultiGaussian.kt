package it.unibo.alchemist.model.deployments

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import org.apache.commons.math3.random.RandomGenerator

/**
 * Deployment that samples node positions from multiple 2D Gaussian clusters.
 *
 * For each node, one center is chosen uniformly at random from [centers], then
 * a Gaussian offset with standard deviation [stddev] is applied on both axes.
 *
 * @param P the position type supported by the target environment
 * @param environment the environment where nodes are deployed
 * @param randomGenerator random source used for center selection and Gaussian sampling
 * @param nodes number of nodes to place
 * @param centers list of 2D centers, each encoded as `[x, y]`
 * @param stddev standard deviation of the Gaussian noise on x and y
 */
class MultiGaussian<P : Position<P>>(
    environment: Environment<Any, P>,
    randomGenerator: RandomGenerator,
    nodes: Int,
    val centers: List<List<Double>>,
    val stddev: Double,
) : AbstractRandomDeployment<P>(environment, randomGenerator, nodes) {

    override fun indexToPosition(i: Int): P {
        val centerIndex = randomGenerator.nextInt(centers.size)
        val centerX = centers[centerIndex][0]
        val centerY = centers[centerIndex][1]

        val gaussianX = randomGenerator.nextGaussian()
        val gaussianY = randomGenerator.nextGaussian()

        val finalX = centerX + (gaussianX * stddev)
        val finalY = centerY + (gaussianY * stddev)

        return makePosition(finalX, finalY)
    }
}
