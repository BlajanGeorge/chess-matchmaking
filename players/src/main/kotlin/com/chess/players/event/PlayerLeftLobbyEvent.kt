package com.chess.players.event

/** A player no longer wants to be matched. Published by the API layer; handled by the players module. */
data class PlayerLeftLobbyEvent(val playerId: String)
