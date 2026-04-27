package it.unibo.collektive.stdlib.election

import it.unibo.collektive.aggregate.api.Aggregate
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
context(position: LocationSensor)
fun Aggregate<Int>.isClosestToCentroid(bound: Int): Boolean =
    leaderClosestToPoint(networkCentroid(bound = bound), bound) == localId

/**
 * Elects a leader close to the network centroid, keeping the previous leader
 * while alternative candidates are only marginally closer.
 */
context(position: LocationSensor)
fun Aggregate<Int>.isClosestToCentroidWithHysteresis(bound: Int, switchMargin: Double): Boolean =
    leaderClosestToPointWithHysteresis(networkCentroid(bound = bound), bound, switchMargin) == localId

/**
 * Elects a leader by favoring devices closer to a target point and returns its identifier.
 *
 * An id-based tie-break keeps the result deterministic when multiple devices are at the
 * same distance from the target, which happens often in symmetric fixed grids.
 */
context(position: LocationSensor)
fun Aggregate<Int>.leaderClosestToPoint(target: Point, electionBound: Int): Int {
    val distance = position.coordinates().distanceTo(target)
    val strength = (-distance + localId * LEADER_TIE_BREAK_SCALE)
    return boundedElection(strength = strength, bound = electionBound)
}

/**
 * Elects a leader by favoring nodes closer to [target], with a low-pass effect on leader changes.
 *
 * The previously accepted leader receives a distance-equivalent bonus, so another node takes over
 * only if it is at least [switchMargin] closer to [target].
 */
context(position: LocationSensor)
fun Aggregate<Int>.leaderClosestToPointWithHysteresis(target: Point, bound: Int, switchMargin: Double): Int =
    evolve(localId) { previousLeader ->
        val distance = position.coordinates().distanceTo(target)
        val stickyBonus = if (localId == previousLeader) switchMargin.coerceAtLeast(0.0) else 0.0
        val strength = (-distance + stickyBonus + localId * LEADER_TIE_BREAK_SCALE)
        boundedElection(strength = strength, bound = bound)
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
): Boolean = isBoundedElectionWinner(centralityWeight(distanceToTarget, bound / 2.0), bound)

inline fun <reified ID : Comparable<ID>, reified Type : Comparable<Type>> Aggregate<ID>.isBoundedElectionWinner(
    weight: Type,
    bound: Int,
): Boolean = boundedElection(strength = weight, bound = bound) == localId
