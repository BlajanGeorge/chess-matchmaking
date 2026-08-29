package com.chess.players.rating

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class RandomRatingProviderTest {
    @Test
    fun `given default range when rating then within 1000 and 3000 inclusive`() {
        val provider = RandomRatingProvider()
        repeat(10_000) { assertTrue(provider.ratingOf("p$it") in 1000..3000) }
    }
}
