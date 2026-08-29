package com.chess.matching.config

import com.chess.matching.matcher.MatcherConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** `matching.*` — see [MatcherConfig] for what each knob means. */
@ConfigurationProperties(prefix = "matching")
data class MatchingProperties(
    val interval: Duration = Duration.ofSeconds(1),
    val basePenalty: Double = 25.0,
    val penaltyPerSecond: Double = 5.0,
    val maxPenalty: Double = 300.0,
) {
    fun toMatcherConfig() = MatcherConfig(interval, basePenalty, penaltyPerSecond, maxPenalty)
}
