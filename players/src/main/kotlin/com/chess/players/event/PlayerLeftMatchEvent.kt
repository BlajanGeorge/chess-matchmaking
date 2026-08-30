package com.chess.players.event

/** A player left (ended) a running match. Published by the API layer; handled by the players module. */
data class PlayerLeftMatchEvent(val playerId: String, val matchId: String)
