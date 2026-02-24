package it.unibo.alchemist
//
//import it.unibo.alchemist.model.Action
//import it.unibo.alchemist.model.Context
//import it.unibo.alchemist.model.Environment
//import it.unibo.alchemist.model.Node
//import it.unibo.alchemist.model.Reaction
//import it.unibo.alchemist.model.actions.AbstractAction
//import it.unibo.alchemist.model.molecules.SimpleMolecule
//import it.unibo.alchemist.model.positions.Euclidean2DPosition
//import it.unibo.filtering.Particle
//import it.unibo.filtering.ParticleFilter
//import it.unibo.filtering.Point
//import kotlin.math.hypot
//import kotlin.math.pow
//import org.apache.commons.math3.random.RandomGenerator
//
///**
// * A centralized particle filter implementation specifically designed for estimating the position of a movable node
// * within a two-dimensional Euclidean environment. This class leverages a particle filter to track and estimate the
// * position of the node based on noisy measurements and prediction steps. It operates within the context of a
// * simulated environment and is aware of motion dynamics, measurement errors, and uniform resampling techniques.
// *
// * @param T the concentration type managed by the node
// * @param environment the simulation environment in which the particle filter operates
// * @param random an instance of a random number generator for reproducibility and stochastic processes
// * @param node the node on which this action is executed
// * @param sideLength the side length of the square area where particles are distributed
// * @param numberOfParticles the number of particles used in the filter
// * @param maxInitialSpeed the maximum initial speed of the particles; defaults to 2.0
// * @param blindSpotDistance the max distance at which the sensor can sense.
// */
//class CentralizedParticleFilter<T>(
//    val environment: Environment<T, Euclidean2DPosition>,
//    val random: RandomGenerator,
//    node: Node<T>,
//    val sideLength: Double,
//    val numberOfParticles: Int,
//    val maxInitialSpeed: Double = 2.0,
//    val blindSpotDistance: Double = Double.MAX_VALUE,
//) : AbstractAction<T>(node) {
//
//    private val estimations: MutableList<Point> = mutableListOf()
//    private val allHistory = mutableListOf<List<Particle>>()
//    private val filter = ParticleFilter(numberOfParticles, maxInitialSpeed, sideLength, random)
//
//    private fun measurePosition(stdDev: Double = 0.5): Double {
//        val movingNode = environment.nodes.first { it.contains(SimpleMolecule("Movable")) }
//        val targetPosition = environment.getPosition(movingNode)
//        val sensorPosition = environment.getPosition(node)
//        val z = 10 / hypot(targetPosition.x - sensorPosition.x, targetPosition.y - sensorPosition.y).pow(2)
//        return z + (random.nextGaussian() * stdDev)
//    }
//
//    override fun execute() {
//        allHistory.add(filter.getAll())
//        val t = environment.simulation.time.toDouble()
//
//        val p = filter.getAll().map { it.weight }
//
//        println("------------------------------------------------------------------------------------------------------")
//        println("Max -> ${p.max()}")
//        println("Min -> ${p.min()}")
//        println("Avg -> ${p.average()}")
//
////        if (t > 1.0 && t < 3.0) {
////            println(filter.getAll())
////        }
//        node.setConcentration(SimpleMolecule("Particles"), allHistory as T)
//        val measure = measurePosition()
//        val sampledParticles = filter.resample()
//        val newParticles = filter.predictParticles(sampledParticles)
//        val sensorPosition = environment.getPosition(node)
//        filter.updateWeights(newParticles, measure,Point(sensorPosition.x, sensorPosition.y))
//        val estimation = filter.estimatePosition()
//        estimations.add(estimation)
//        node.setConcentration(SimpleMolecule("Estimations"), estimations as T)
//        node.setConcentration(SimpleMolecule("NumberOfParticles"), filter.numberOfParticles as T)
//    }
//
//    override fun getContext(): Context = Context.LOCAL
//
//    override fun cloneAction(node: Node<T>, reaction: Reaction<T>): Action<T> = CentralizedParticleFilter(
//        environment,
//        random,
//        node,
//        sideLength,
//        numberOfParticles,
//        maxInitialSpeed,
//    )
//}
