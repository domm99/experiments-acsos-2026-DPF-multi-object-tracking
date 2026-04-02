package it.unibo.collektive.stdlib.election

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.centralityWeight
import it.unibo.collektive.distanceFromNetworkCentroid
import it.unibo.collektive.stdlib.consensus.boundedElection

context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.isClosestToCentroid(bound: Int): Boolean {
    val dist = device.environment.distanceFromNetworkCentroid(position.coordinates())
    val weight = centralityWeight(dist, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}

fun Aggregate<Int>.isClosestToTarget(distanceToTarget: Double, bound: Int): Boolean {
    val weight = centralityWeight(distanceToTarget, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}
