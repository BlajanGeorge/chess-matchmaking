package com.chess.players.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `players.*`
 * @property acceptTimeout how long both players have to accept a proposed match.
 * @property timeoutSweepInterval how often overdue proposals are expired.
 */
@ConfigurationProperties(prefix = "players")
data class PlayersProperties(
    val acceptTimeout: Duration = Duration.ofSeconds(15),
    val timeoutSweepInterval: Duration = Duration.ofSeconds(1),
)
