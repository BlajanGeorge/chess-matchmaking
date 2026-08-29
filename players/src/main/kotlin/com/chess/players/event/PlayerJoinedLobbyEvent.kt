package com.chess.players.event

/** A player asked to be matched. Published by the API layer; handled by the players module. */
data class PlayerJoinedLobbyEvent(val playerId: String)
