package com.chess.players.event

/** A player accepted a proposed match. Published by the API layer; handled by the players module. */
data class PlayerAcceptedMatchEvent(val playerId: String, val matchId: String)
