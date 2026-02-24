package it.unibo.collektive
//
//import it.unibo.alchemist.collektive.device.CollektiveDevice
//import it.unibo.collektive.aggregate.api.Aggregate
//import it.unibo.collektive.alchemist.device.sensors.EnvironmentVariables
//import it.unibo.collektive.alchemist.device.sensors.LocationSensor
//import it.unibo.collektive.stdlib.accumulation.convergeCast
//import it.unibo.collektive.stdlib.consensus.globalElection
//import it.unibo.collektive.stdlib.processes.timeReplicated
//import kotlin.time.Duration.Companion.seconds
//import kotlin.time.ExperimentalTime
//import kotlin.time.Instant
//
///**
// * todo.
// */
//@OptIn(ExperimentalTime::class)
//fun Aggregate<Int>.replicatedConvergeCast(
//    dev: CollektiveDevice<*>,
//    env: EnvironmentVariables,
//    position: LocationSensor,
//) {
//    val currentInstant: Instant = Instant.fromEpochSeconds(dev.currentTime.toDouble().toLong())
//    val leader = globalElection(localId)
//    val replicaList = timeReplicated(
//        currentTime = currentInstant,
//        maxReplicas = 4,
//        timeToSpawn = 3.seconds,
//        process = {
//            convergeAllHistory(
//                sink = leader == localId,
//                startingData = position.targetsPosition().first(),
//                historySize = dev.environment.nodeCount,
//            )
//        },
//    )
//    env["replicas"] = replicaList
//}
//
///**
// * todo.
// */
//fun Aggregate<Int>.convergeHistoryEntrypoint(
//    collektiveDevice: CollektiveDevice<*>,
//    env: EnvironmentVariables,
//    position: LocationSensor,
//): List<*> = with(collektiveDevice) {
//    val leader = globalElection(localId)
//    val history = convergeHistory(
//        sink = leader == localId,
//        startingData = position.targetsPosition().first(),
//        historySize = 5,
//    )
//    env["source"] = leader == localId
//    env["history"] = history
//    return history
//}
//
///**
// * Manages the evolution of shared data in an aggregate system, accumulating historical data from all nodes.
// * The [sink] accumulate all the lastest data of the system.
// * Allows limiting the size of the history.
// * @param sink A boolean indicating whether the current device acts as a sink for the data.
// * @param startingData The initial data to start the accumulation process.
// * @param historySize The maximum size of the historical data to retain. If null, the history is unbounded.
// * @return A FIFO list of accumulated historical data after convergence and evaluation
// *      (first element is the oldest, last element is the most recent).
// */
//fun <SharingData> Aggregate<Int>.convergeAllHistory(
//    sink: Boolean,
//    startingData: SharingData,
//    historySize: Int = Int.MAX_VALUE, // default means unbounded, keep all history -- careful with memory!
//): List<SharingData> = evolve(listOf(startingData)) { data ->
//    convergeCast(
//        local = data,
//        sink = sink,
//        accumulateData = { acc, value -> (acc + value).takeLast(historySize) },
//    )
//}
//
///**
// * Accumulates the snapshots of the system's data over time.
// * The [sink] retains all the system snapshots (possibly limited by [historySize]).
// * If [historyOnlyAtSink] is false, all nodes retain their **local** history as well.
// *
// * @param sink a boolean indicating whether the current node is a sink in the network
// * @param startingData the initial data to start the convergence process
// * @param historySize the maximum size of the history to keep; if null, the history is unbounded
// * @param historyOnlyAtSink when true, retains the history only at the sink node,
// * otherwise retains the local history at all nodes
// * @return a FIFO list of neighborhood history objects containing the data of neighboring nodes during convergence
// *  (first element is the oldest, last element is the most recent).
// */
//inline fun <reified SharingData> Aggregate<Int>.convergeHistory(
//    sink: Boolean,
//    startingData: SharingData,
//    historySize: Int = Int.MAX_VALUE, // default means unbounded, keep all history -- careful with memory!
//    historyOnlyAtSink: Boolean = true,
//): List<NeighborhoodHistory<SharingData>> =
//    evolve(listOf(NeighborhoodHistory(startingData))) { previousData ->
//        val systemSnapshot = convergeCast(
//            local = listOf(startingData),
//            sink = sink,
//            accumulateData = { acc, value ->
//                acc + value
//            },
//        )
//        when {
//            !historyOnlyAtSink || sink -> (previousData + NeighborhoodHistory(systemSnapshot))
//                .takeLast(historySize)
//
//            else -> previousData
//        }
//    }
//
///**
// * Representation of the history of [neighborsData] shared among nodes in a system.
// */
//data class NeighborhoodHistory<SharingData>(val neighborsData: List<SharingData> = emptyList()) {
//
//    constructor(data: SharingData) : this(listOf(data))
//
//    override fun toString(): String =
//        "History of #neighborhood=${neighborsData.size} { ${neighborsData.joinToString(", ")} }"
//}
