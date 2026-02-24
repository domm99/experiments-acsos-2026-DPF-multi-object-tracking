package it.unibo.collektive
//
//import it.unibo.alchemist.collektive.device.CollektiveDevice
//import it.unibo.collektive.aggregate.api.Aggregate
//import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
//import it.unibo.collektive.alchemist.device.sensors.PresenceSensor
//import it.unibo.filtering.ParticleFilter
//import it.unibo.filtering.Point
//import org.apache.commons.math3.random.RandomGenerator
//
///**
// * todo.
// */
//fun Aggregate<Int>.boolSensingEntrypoint(
//    collektiveDevice: CollektiveDevice<*>,
//    env: EnvironmentVariables,
//    presenceSensor: PresenceSensor,
//) = context(env, collektiveDevice.randomGenerator, presenceSensor) {
//    val estimations = env.getOrDefault("Estimations", listOf<Point>())
//    booleanLocalFiltering(
//        estimations,
//        env["NumberOfParticles"],
//        env["MaxInitialSpeed"],
//        env["SideLength"],
//    ).also { history ->
//        env["Estimations"] = history
//    }
//}
//
//context(random: RandomGenerator, presenceSensor: PresenceSensor)
//private fun Aggregate<Int>.booleanLocalFiltering(
//    estimationHistory: List<Point>,
//    numOfParticles: Int,
//    maxInitialSpeed: Double,
//    sideLength: Double,
//): List<Point> = evolving(ParticleFilter(numOfParticles, maxInitialSpeed, sideLength, random)) { filter ->
//    val sampledParticles = filter.resample()
//    val newParticles = filter.predictParticles(sampledParticles)
//    filter.updateWeights(newParticles, TODO("neighborhoodSensing"))
//    val estimation = filter.estimatePosition()
//    val history: List<Point> = estimationHistory + estimation
//    filter.yielding { history }
//}
//
//context(presence: PresenceSensor)
//fun Aggregate<*>.neighborhoodSensing(): List<Boolean?> {
//    val isTargetInRange = presence.isSensing()
//    TODO("not yet implemented")
//}
