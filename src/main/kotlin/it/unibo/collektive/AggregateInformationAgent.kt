package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.filtering.ParticleFilter

/**
 * The entrypoint of the simulation performing local information filtering.
 */
fun Aggregate<Int>.informationFilterEntrypoint(device: CollektiveDevice<*>, position: LocationSensor) =
    context(device, device.randomGenerator, position) {
        val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
        localFiltering(
            estimations,
            device["NumberOfParticles"],
            device["MaxInitialSpeed"],
            device["SideLength"],
        ).also { history ->
            device["Estimations"] = history
        }
    }

context(device: CollektiveDevice<*>, position: LocationSensor)
/**
 * Performs local filtering using a Particle Filter to estimate the position
 * of a target based on neighborhood information.
 *
 * @param random the random generator for stochastic processes
 * @param position the location sensor providing target position and neighborhood data
 */
fun Aggregate<*>.localFiltering(
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targets = position.targetsPosition()
    return evolving(
        ParticleFilter(
            numberOfParticles,
            maxInitialSpeed,
            sideLength,
            targets.map { it.zebraID }.toSet(),
            random = device.randomGenerator,
        ),
    ) { filter ->
        device["NumberOfParticles"] = numberOfParticles
        val numberOfNeighbors = device.getOrDefault("NumberOfNeighbors", 0)
        val estimations = mutableListOf<ZebraPositionHistory>()
        for (zebra in targets) {
            alignedOn(zebra.zebraID) {
                val sampledParticles = filter.resample(zebra.zebraID)
                val newParticles = filter.predictParticles(sampledParticles)
                val selfPosition = position.selfPosition()
                val measure = fromPositionToMeasure(selfPosition, zebra.position, device.randomGenerator)
                val info = neighboring(DistanceFromPosition(selfPosition, measure)).all.list
                val neighborsInfo = selectNeighbors(info, localId as Int, numberOfNeighbors).map { it.value }
                filter.updateWeights(zebra.zebraID, newParticles, neighborsInfo)
                val estimation = filter.estimatePosition(zebra.zebraID)
                val newZebra = estimations.find { it.zebraID == zebra.zebraID }?.let { est ->
                    est.copy(positions = est.positions + estimation)
                } ?: ZebraPositionHistory(zebra.zebraID, listOf(estimation))
                estimations.add(newZebra)
            }
        }
        filter.yielding { estimationsHistory.updateHistory(estimations) }
    }
}

/**
 * Utility function that returns a list with the updated [ZebraPositionHistory] given the current [estimations].
 */
fun List<ZebraPositionHistory>.updateHistory(
    estimations: MutableList<ZebraPositionHistory>,
): List<ZebraPositionHistory> = when {
    this.isNotEmpty() && estimations.isNotEmpty() -> {
        this.map { history ->
            val zebraPos = estimations.find { zebra -> zebra.zebraID == history.zebraID }?.positions
            when {
                zebraPos != null -> history.copy(positions = history.positions + zebraPos)
                else -> history
            }
        }
    }
    else -> this + estimations
}


/**
 * Selects up to [n] closest neighbor entries to the local device and includes local entry.
 *
 * Distance is computed in squared Euclidean space using [DistanceFromPosition.currentPosition].
 *
 * @param originalList Full list of local+neighbor field entries.
 * @param localID Identifier of the local device entry in [originalList].
 * @param n Number of nearest neighbors to keep (excluding local entry).
 * @return A list containing local entry first, followed by up to [n] nearest neighbors.
 */
private fun selectNeighbors(
    originalList: List<FieldEntry<out Any, DistanceFromPosition>>,
    localID: Int,
    n: Int,
): List<FieldEntry<out Any, DistanceFromPosition>> {
    val localEntry = originalList.find { it.id == localID }
    val localPoint = localEntry!!.value.currentPosition
    return originalList
        .filter { it.id != localID }
        .sortedBy { entry ->
            val targetPoint = entry.value.currentPosition
            val dx = targetPoint.x - localPoint.x
            val dy = targetPoint.y - localPoint.y
            (dx * dx) + (dy * dy)
        }
        .take(n)
        .let { listOf(localEntry) + it }
}
