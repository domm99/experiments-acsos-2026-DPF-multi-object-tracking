package it.unibo.collektive.alchemist.device.sensors

import it.unibo.alchemist.collektive.device.CollektiveDevice
import it.unibo.collektive.stdlib.swarm.GridFormationValues

/**
 * Reads swarm grid and movement parameters from the device environment.
 */
val CollektiveDevice<*>.gridFormationValues: GridFormationValues
    get() = GridFormationValues(
        getOrDefault("FormationRows", 0),
        getOrDefault("FormationColumns", 0),
        getOrDefault("FormationSpacing", 0.0),
        getOrDefault("SwarmStepSize", DEFAULT_SWARM_STEP_SIZE),
        getOrDefault("SwarmWarmupRounds", DEFAULT_SWARM_WARMUP_ROUNDS),
        getOrDefault("ErrorOnDesiredPosition", 0.0),
    )

/**
 * Computes the election bound from the configured grid size.
 *
 * @param fallback Value returned when the configured grid has no positive area.
 * @return Product of formation rows and columns, or [fallback].
 */
fun CollektiveDevice<*>.gridElectionBound(fallback: Int): Int {
    val rows = getOrDefault("FormationRows", 0)
    val columns = getOrDefault("FormationColumns", 0)
    return (rows * columns).takeIf { it > 0 } ?: fallback
}

/**
 * Reads the leader-switch margin from the device environment.
 *
 * @param spacing Formation spacing used to derive the default margin.
 * @return Configured `LeaderElectionSwitchMargin`, or a spacing-proportional default.
 */
fun CollektiveDevice<*>.leaderSwitchMargin(spacing: Double): Double =
    getOrDefault("LeaderElectionSwitchMargin", spacing * DEFAULT_LEADER_SWITCH_MARGIN_RATIO)

private const val DEFAULT_LEADER_SWITCH_MARGIN_RATIO = 0.25

/**
 * Default movement step used when no swarm-specific step size is provided.
 */
private const val DEFAULT_SWARM_STEP_SIZE = 2.0

/**
 * Default number of filter rounds required before the swarm starts moving.
 */
private const val DEFAULT_SWARM_WARMUP_ROUNDS = 5
