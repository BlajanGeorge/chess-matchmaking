package com.chess.players.event

/** A player turned down a proposed match. Published by the API layer; handled by the players module. */
data class PlayerDeclinedMatchEvent(val playerId: String, val matchId: String)
