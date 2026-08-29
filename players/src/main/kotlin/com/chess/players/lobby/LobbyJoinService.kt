package com.chess.players.lobby

import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import com.chess.players.rating.RatingProvider
import com.chess.players.state.PlayerState
import com.chess.players.state.PlayerStateRepository
import com.chess.players.state.PlayerStatus
import java.time.Clock

class LobbyJoinService(
    private val states: PlayerStateRepository,
    private val lobby: LobbyRepository,
    private val ratings: RatingProvider,
    private val clock: Clock,
) {
    enum class Outcome {
        /** Entered the lobby. */
        JOINED,
        /** Was already waiting; original wait time kept. If the lobby entry was missing it has been restored. */
        ALREADY_WAITING,
        /** Has a proposed or running match; cannot re-enter the lobby until that resolves. */
        REJECTED_BUSY,
    }

    /**
     * idle → WAITING, then into the lobby. `joinedAt` is server time, so wait-based penalties can't be gamed by
     * the client. State is written first so that "in the lobby" always implies WAITING; the reverse gap
     * (WAITING but not in the lobby, if we die in between) is what the sweeper repairs.
     */
    fun join(playerId: String): Outcome {
        val now = clock.instant()
        if (!states.saveIfAbsent(PlayerStatus(playerId, PlayerState.WAITING, since = now))) {
            val current = states.find(playerId)
            return when (current?.state) {
                PlayerState.WAITING -> {
                    // idempotent repair: WAITING without a lobby entry (crash between the two writes) would never be matched
                    if (lobby.find(playerId) == null) lobby.join(LobbyPlayer(playerId, ratings.ratingOf(playerId), current.since))
                    Outcome.ALREADY_WAITING
                }
                null -> Outcome.ALREADY_WAITING
                PlayerState.PENDING, PlayerState.IN_MATCH -> Outcome.REJECTED_BUSY
            }
        }
        lobby.join(LobbyPlayer(playerId, ratings.ratingOf(playerId), now))
        return Outcome.JOINED
    }
}
