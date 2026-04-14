@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive.stdlib.election

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.centralityWeight
import it.unibo.collektive.distanceFromNetworkCentroid
import it.unibo.collektive.stdlib.consensus.boundedElection

/**
 * Elects a leader by favoring devices closer to the network centroid.
 *
 * This function:
 * 1. Computes this device's distance from the centroid of filter nodes.
 * 2. Converts that distance into an election strength via [centralityWeight].
 * 3. Runs [boundedElection] and checks whether the elected id is the local one.
 *
 * @receiver Aggregate program context with integer ids.
 * @param bound Election bound (maximum id-space/window used by [boundedElection]).
 * @return `true` if this device is elected as leader, `false` otherwise.
 */
context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<Int>.isClosestToCentroid(bound: Int): Boolean {
    val dist = device.environment.distanceFromNetworkCentroid(position.coordinates())
    val weight = centralityWeight(dist, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}

/**
 * Elects a leader by favoring devices closer to a target point.
 *
 * The caller provides [distanceToTarget]; smaller distances produce larger
 * [centralityWeight], which increases election strength in [boundedElection].
 *
 * @receiver Aggregate program context with integer ids.
 * @param distanceToTarget Distance from this device to the target.
 * @param bound Election bound (maximum id-space/window used by [boundedElection]).
 * @return `true` if this device is elected as leader, `false` otherwise.
 */
fun Aggregate<Int>.isClosestToTarget(distanceToTarget: Double, bound: Int): Boolean {
    val weight = centralityWeight(distanceToTarget, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}
