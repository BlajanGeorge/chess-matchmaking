package com.chess.matching.matcher

import com.chess.matching.lobby.LobbyPlayer

/** Receives pairs the matcher has successfully claimed from the lobby. Later: publishes a MATCH_FOUND event per pair. */
fun interface MatchHandler {
    fun onMatched(a: LobbyPlayer, b: LobbyPlayer)
}
