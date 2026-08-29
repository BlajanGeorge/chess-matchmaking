package com.chess.players.rating

import kotlin.random.Random

/** Placeholder until ratings live somewhere real. */
class RandomRatingProvider(
    private val range: IntRange = 1000..3000,
    private val random: Random = Random.Default,
) : RatingProvider {
    override fun ratingOf(playerId: String): Int = random.nextInt(range.first, range.last + 1)
}
