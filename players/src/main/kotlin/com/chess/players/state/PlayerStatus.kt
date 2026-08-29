package com.chess.players.state

import java.time.Instant

enum class PlayerState {
    /** In the lobby, available to the matcher. */
    WAITING,
    /** Claimed by the matcher; a match has been proposed and awaits both accepts. */
    PENDING,
    /** Both accepted; the game is on. */
    IN_MATCH,
}

/**
 * Where a player is in the matchmaking flow. No entry at all means idle.
 *
 * @property matchId set while [PlayerState.PENDING] or [PlayerState.IN_MATCH].
 * @property since when the player entered this state.
 */
data class PlayerStatus(
    val playerId: String,
    val state: PlayerState,
    val matchId: String? = null,
    val since: Instant,
) {
    init {
        when (state) {
            PlayerState.WAITING -> require(matchId == null) { "$playerId: WAITING must not carry a matchId" }
            PlayerState.PENDING, PlayerState.IN_MATCH -> requireNotNull(matchId) { "$playerId: $state requires a matchId" }
        }
    }
}
