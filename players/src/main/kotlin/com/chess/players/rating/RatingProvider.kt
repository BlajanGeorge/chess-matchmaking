package com.chess.players.rating

/** Where a player's rating comes from. Eventually a DB lookup. */
fun interface RatingProvider {
    fun ratingOf(playerId: String): Int
}
