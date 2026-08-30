package com.chess.gateway.ws

import com.chess.matching.lobby.LobbyPlayer
import com.chess.players.state.PlayerState

/** Client → server. `type` selects the command; `matchId` is required for ACCEPT_MATCH / DECLINE_MATCH / LEAVE_MATCH. */
data class Command(val type: Type, val matchId: String? = null) {
    enum class Type { JOIN_LOBBY, LEAVE_LOBBY, ACCEPT_MATCH, DECLINE_MATCH, LEAVE_MATCH, STATUS }
}

/** Server → client. Every message carries a `type` so the client can switch on it. */
sealed interface ServerMessage {
    val type: String
}

data class Status(val state: PlayerState?, val matchId: String?) : ServerMessage {
    override val type = "STATUS"
}

data class Opponent(val id: String, val rating: Int) {
    companion object {
        fun of(p: LobbyPlayer) = Opponent(p.id, p.rating)
    }
}

data class MatchProposed(val matchId: String, val opponent: Opponent, val expiresInSeconds: Long) : ServerMessage {
    override val type = "MATCH_PROPOSED"
}

data class MatchStarted(val matchId: String, val opponent: Opponent) : ServerMessage {
    override val type = "MATCH_STARTED"
}

data class MatchCancelled(val matchId: String, val reason: String, val backInLobby: Boolean) : ServerMessage {
    override val type = "MATCH_CANCELLED"
}

data class MatchEnded(val matchId: String, val endedBy: String) : ServerMessage {
    override val type = "MATCH_ENDED"
}

data class Error(val message: String) : ServerMessage {
    override val type = "ERROR"
}
