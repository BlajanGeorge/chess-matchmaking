package com.chess.matching.matcher

import java.time.Duration

/**
 * @property interval how often the matcher runs.
 * @property basePenalty cost of leaving a freshly-joined player unmatched for one round. Two players can only be
 * paired when their rating gap is below the sum of their penalties, so with the defaults a new player accepts
 * opponents within ±50 rating points.
 * @property penaltyPerSecond how much the penalty grows per second of waiting — the window widens over time.
 * @property maxPenalty cap on the penalty, i.e. the widest window a player ever gets (±maxPenalty).
 */
data class MatcherConfig(
    val interval: Duration = Duration.ofSeconds(1),
    val basePenalty: Double = 25.0,
    val penaltyPerSecond: Double = 5.0,
    val maxPenalty: Double = 300.0,
) {
    init {
        require(!interval.isNegative && !interval.isZero) { "interval must be positive" }
        require(basePenalty >= 0 && penaltyPerSecond >= 0) { "penalties must be >= 0" }
        require(maxPenalty >= basePenalty) { "maxPenalty must be >= basePenalty" }
    }

    fun alonePenalty(waitedSeconds: Double): Double =
        minOf(basePenalty + penaltyPerSecond * waitedSeconds, maxPenalty)
}
