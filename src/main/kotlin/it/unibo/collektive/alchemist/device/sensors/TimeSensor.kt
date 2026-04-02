package it.unibo.collektive.alchemist.device.sensors

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A sensor that provides information about the simulation time.
 */
interface TimeSensor {
    /**
     * Retrieves the current simulation time as a Double.
     *
     * @return the simulation time, represented as a Double value
     */
    fun getTimeAsDouble(): Double

    /**
     * Retrieves the current simulation time as an Instant.
     *
     * @return the simulation time, represented as an Instant
     */
    @OptIn(ExperimentalTime::class)
    fun getTimeAsInstant(): Instant
}
