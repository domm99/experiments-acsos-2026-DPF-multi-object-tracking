@file:Suppress("UndocumentedPublicFunction") // detekt does not support context parameters,
// the documentation is present

package it.unibo.collektive

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.aggregate.FieldEntry
import it.unibo.collektive.aggregate.api.Aggregate
import it.unibo.collektive.aggregate.api.neighboring
import it.unibo.collektive.alchemist.device.sensors.LocationSensor
import it.unibo.collektive.models.DistanceFromPosition
import it.unibo.collektive.models.ZebraPositionHistory
import it.unibo.collektive.stdlib.swarm.DEFAULT_SWARM_STEP_SIZE
import it.unibo.collektive.stdlib.swarm.DEFAULT_SWARM_WARMUP_ROUNDS
import it.unibo.collektive.stdlib.swarm.GridFormationValues
import it.unibo.collektive.stdlib.swarm.computeDistributedSwarmMovement
import it.unibo.filtering.ParticleFilter

/**
 * The entrypoint of the simulation performing local information filtering, without grid movement.
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

/**
 * Runs local filtering and computes the next distributed movement of each sensor in the swarm.
 *
 * The target is built from the latest estimations available on each node, then aggregated
 * and broadcast through neighbor-to-neighbor coordination. Each node finally stores its
 * own next movement step inside the `NextPosition` molecule.
 */
fun Aggregate<Int>.informationFilterAndDistributedMovementEntrypoint(
    device: CollektiveDevice<*>,
    position: LocationSensor,
) = context(device, device.randomGenerator, position) {
    val estimations = device.getOrDefault("Estimations", emptyList<ZebraPositionHistory>())
    val sideLength = device["SideLength"] as Int
    val history = localFiltering(
        estimations,
        device["NumberOfParticles"],
        device["MaxInitialSpeed"],
        sideLength.toDouble(),
    )
    device["Estimations"] = history
    val gridValues = GridFormationValues(
        device.getOrDefault("FormationRows", 0),
        device.getOrDefault("FormationColumns", 0),
        device.getOrDefault("FormationSpacing", 0.0),
        device.getOrDefault("SwarmStepSize", DEFAULT_SWARM_STEP_SIZE),
        device.getOrDefault("SwarmWarmupRounds", DEFAULT_SWARM_WARMUP_ROUNDS),
        device.getOrDefault("ErrorOnDesiredPosition", 0.0)
    )
    val electionBound = (gridValues.rows * gridValues.cols).takeIf { it > 0 } ?: sideLength
    computeDistributedSwarmMovement(gridValues, electionBound, history).also {
        device["NextPosition"] = it
    }
    history
}

/**
 * Performs local filtering using a Particle Filter to estimate the position
 * of a target based on neighborhood information.
 *
 * @param random the random generator for stochastic processes
 * @param position the location sensor providing target position and neighborhood data
 */
context(device: CollektiveDevice<*>, position: LocationSensor)
fun Aggregate<*>.localFiltering(
    estimationsHistory: List<ZebraPositionHistory>,
    numberOfParticles: Int,
    maxInitialSpeed: Double,
    sideLength: Double,
): List<ZebraPositionHistory> {
    val targets = position.targetsPosition()
    val initializationArea = device.particleInitializationArea(sideLength)
    return evolving(
        ParticleFilter(
            numberOfParticles = numberOfParticles,
            maxInitialSpeed = maxInitialSpeed,
            sideLength = sideLength,
            initialMinX = initializationArea.minX,
            initialMaxX = initializationArea.maxX,
            initialMinY = initializationArea.minY,
            initialMaxY = initializationArea.maxY,
            clampParticlesToInitializationArea = initializationArea.clampParticles,
            targetsIDs = targets.map { it.zebraID }.toSet(),
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
 * Distance is computed in squared Euclidean space using
 * [it.unibo.collektive.models.DistanceFromPosition.currentPosition].
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
    val localEntry = requireNotNull(originalList.find { it.id == localID }) {
        "Local entry with id $localID not found"
    }
    val localPoint = localEntry.value.currentPosition
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
