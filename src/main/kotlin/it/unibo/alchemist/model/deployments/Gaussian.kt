package it.unibo.alchemist.model.deployments

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import org.apache.commons.math3.random.RandomGenerator

/**
 * A deployment strategy that places nodes according to a Gaussian (normal) distribution
 * around a specified center point. The distribution is defined by the center coordinates
 * and standard deviation (stddev).
 *
 * @param P the type of position supported by the deployment.
 * @param environment the environment in which the nodes are deployed.
 * @param randomGenerator the random number generator used to produce Gaussian values.
 * @param nodes the number of nodes to be deployed.
 * @param centerX the x-coordinate of the center of the Gaussian distribution.
 * @param centerY the y-coordinate of the center of the Gaussian distribution.
 * @param stddev the standard deviation used for the Gaussian distribution.
 */
class Gaussian<P : Position<P>>(
    environment: Environment<Any, P>,
    randomGenerator: RandomGenerator,
    nodes: Int,
    val centerX: Double,
    val centerY: Double,
    val stddev: Double,
) : AbstractRandomDeployment<P>(environment, randomGenerator, nodes) {

    override fun indexToPosition(i: Int): P {
        val gaussianX = randomGenerator.nextGaussian()
        val gaussianY = randomGenerator.nextGaussian()
        val finalX = centerX + (gaussianX * stddev)
        val finalY = centerY + (gaussianY * stddev)
        return makePosition(finalX, finalY)
    }
}
