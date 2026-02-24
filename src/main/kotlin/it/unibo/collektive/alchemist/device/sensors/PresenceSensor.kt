package it.unibo.collektive.alchemist.device.sensors

/**
 * A sensor that detects the presence of specific targets or signals in the environment.
 */
interface PresenceSensor {
    /**
     * Determines whether the sensor is currently detecting the presence of a target or signal.
     *
     * @return true if the sensor detects the presence of a target or signal, false otherwise
     */
    fun isSensing(): Boolean
}
