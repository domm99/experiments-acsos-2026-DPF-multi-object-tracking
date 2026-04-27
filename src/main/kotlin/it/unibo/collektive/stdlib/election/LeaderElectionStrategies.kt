@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive.stdlib.election

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.share
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.centralityWeight
import it.unibo.collektive.models.Point
import it.unibo.collektive.models.distanceTo
import it.unibo.collektive.stdlib.consensus.boundedElection
import it.unibo.collektive.stdlib.swarm.networkCentroid

private const val LEADER_TIE_BREAK_SCALE = 1e-9

/**
 * Elects a leader by favoring devices closer to the network centroid.
 *
 * This function:
 * 1. Computes the network centroid through aggregate collection and spreading.
 * 2. Converts distance from that centroid into a scalar election strength.
 * 3. Runs [boundedElection] with the provided grid-size bound.
 *
 * @receiver Aggregate program context with integer ids.
 * @param bound Election bound, expected to cover the filter grid diameter.
 * @return `true` if this device is elected as leader, `false` otherwise.
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.isClosestToCentroid(bound: Int): Boolean = leaderClosestToCentroid(bound) == localId

/**
 * Elects a leader close to the network centroid, keeping the previous leader
 * while alternative candidates are only marginally closer.
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.isClosestToCentroidWithHysteresis(bound: Int, switchMargin: Double): Boolean =
    leaderClosestToCentroidWithHysteresis(bound, switchMargin) == localId

/**
 * Elects a leader by favoring devices closer to the network centroid and returns its identifier.
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.leaderClosestToCentroid(bound: Int): Int = leaderClosestToPoint(networkCentroid(bound), bound)

/**
 * Elects a leader close to the network centroid and returns the temporally filtered identifier.
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.leaderClosestToCentroidWithHysteresis(bound: Int, switchMargin: Double): Int =
    leaderClosestToPointWithHysteresis(networkCentroid(bound), bound, switchMargin)

/**
 * Elects a leader by favoring devices closer to a target point and returns its identifier.
 *
 * An id-based tie-break keeps the result deterministic when multiple devices are at the
 * same distance from the target, which happens often in symmetric fixed grids.
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.leaderClosestToPoint(target: Point, bound: Int): Int {
    val dist = position.coordinates().distanceTo(target).also { device["dist"] = it }
    val strength = (-dist + localId * LEADER_TIE_BREAK_SCALE).also { device["strength"] = it }
    return boundedElection(strength = strength, bound = bound).also { device["closest?"] = it }
}

/**
 * Elects a leader by favoring nodes closer to [target], with a low-pass effect on leader changes.
 *
 * The previously accepted leader receives a distance-equivalent bonus, so another node takes over
 * only if it is at least [switchMargin] closer to [target].
 */
context(position: LocationSensor, device: CollektiveDevice<*>)
fun Aggregate<Int>.leaderClosestToPointWithHysteresis(target: Point, bound: Int, switchMargin: Double): Int =
    share(localId) { previousLeader ->
        val acceptedLeader = previousLeader.local.value
        val dist = position.coordinates().distanceTo(target).also { device["dist"] = it }
        val margin = switchMargin.coerceAtLeast(0.0).also { device["leaderSwitchMargin"] = it }
        val stickyBonus = if (localId == acceptedLeader) margin else 0.0
        val strength = (-dist + stickyBonus + localId * LEADER_TIE_BREAK_SCALE).also { device["strength"] = it }
        val electedLeader = boundedElection(strength = strength, bound = bound).also { device["closest?"] = it }
        device["previousLeader"] = acceptedLeader
        electedLeader
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
inline fun <reified ID : Comparable<ID>> Aggregate<ID>.isClosestToTarget(
    distanceToTarget: Double,
    bound: Int,
): Boolean {
    val weight = centralityWeight(distanceToTarget, bound / 2.0) // the highest, the closest to the center
    return boundedElection(strength = weight, bound = bound) == localId
}
