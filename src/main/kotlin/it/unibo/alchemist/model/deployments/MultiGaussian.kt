package it.unibo.alchemist.model.deployments

import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Position
import org.apache.commons.math3.random.RandomGenerator

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
