package com.chess.matching.matcher

import com.chess.matching.lobby.LobbyPlayer
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatingMatcherTest {

    private val now = Instant.parse("2026-08-29T10:00:00Z")
    private fun p(id: String, rating: Int) = LobbyPlayer(id, rating, now)
    private fun ids(pairs: List<Pair<LobbyPlayer, LobbyPlayer>>) = pairs.map { setOf(it.first.id, it.second.id) }.toSet()

    @Test
    fun `given empty lobby when match then nothing`() {
        val result = RatingMatcher { 30.0 }.match(emptyList())
        assertTrue(result.pairs.isEmpty() && result.unmatched.isEmpty())
        assertEquals(0.0, result.cost)
    }

    @Test
    fun `given walkthrough example when match then pairs close neighbours and leaves outliers`() {
        // [1000, 1010, 1011, 1100], P=30 -> pair 1010-1011, leave 1000 and 1100 (cost 1 + 30 + 30)
        val result = RatingMatcher { 30.0 }.match(listOf(p("a", 1000), p("b", 1010), p("c", 1011), p("d", 1100)))
        assertEquals(setOf(setOf("b", "c")), ids(result.pairs))
        assertEquals(setOf("a", "d"), result.unmatched.map { it.id }.toSet())
        assertEquals(61.0, result.cost)
    }

    @Test
    fun `given gap equal to sum of penalties when match then leaves both alone`() {
        val result = RatingMatcher { 30.0 }.match(listOf(p("a", 1000), p("b", 1060)))
        assertTrue(result.pairs.isEmpty())
    }

    @Test
    fun `given long waiters when match then they accept an opponent a fresh player would not`() {
        val players = listOf(p("a", 1000), p("b", 1200))
        assertTrue(RatingMatcher { 20.0 }.match(players).pairs.isEmpty())
        assertEquals(1, RatingMatcher { 150.0 }.match(players).pairs.size)
    }

    @Test
    fun `given cheap middle player when match then outer pair may skip over them`() {
        // b is cheap to leave alone, a and c are expensive -> optimum is a-c, not a neighbour pair
        val penalties = mapOf("a" to 1000.0, "b" to 0.0, "c" to 1000.0)
        val result = RatingMatcher { penalties.getValue(it.id) }.match(listOf(p("a", 0), p("b", 1), p("c", 100)))
        assertEquals(setOf(setOf("a", "c")), ids(result.pairs))
        assertEquals(100.0, result.cost)
    }

    @RepeatedTest(300)
    fun `given random small lobby when match then every player placed once and cost is the brute-force optimum`() {
        val rnd = Random.Default
        val players = List(rnd.nextInt(0, 9)) { p("p$it", rnd.nextInt(800, 1300)) }
        val penalties = players.associate { it.id to rnd.nextInt(0, 120).toDouble() }

        val result = RatingMatcher { penalties.getValue(it.id) }.match(players)

        val placed = result.pairs.flatMap { listOf(it.first.id, it.second.id) } + result.unmatched.map { it.id }
        assertEquals(players.map { it.id }.sorted(), placed.sorted())
        val recomputed = result.pairs.sumOf { abs(it.first.rating - it.second.rating).toDouble() } +
            result.unmatched.sumOf { penalties.getValue(it.id) }
        assertEquals(recomputed, result.cost, 1e-9)
        assertEquals(bruteForce(players, penalties), result.cost, 1e-9)
    }

    @Test
    fun `given 100k players when match then fast and almost everyone paired`() {
        val rnd = Random(42)
        val players = List(100_000) { p("p$it", rnd.nextInt(600, 2800)) }
        val start = System.nanoTime()
        val result = RatingMatcher { 30.0 }.match(players)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(result.pairs.size > 45_000, "paired ${result.pairs.size}")
        assertTrue(ms < 2_000, "took ${ms}ms")
    }

    /** Min cost over every matching — each player alone or paired with any other. Exponential, small n only. */
    private fun bruteForce(players: List<LobbyPlayer>, penalties: Map<String, Double>): Double {
        fun go(rest: List<LobbyPlayer>): Double {
            if (rest.isEmpty()) return 0.0
            val first = rest[0]
            var best = penalties.getValue(first.id) + go(rest.drop(1))
            for (k in 1 until rest.size) {
                val remaining = rest.filterIndexed { idx, _ -> idx != 0 && idx != k }
                best = minOf(best, abs(first.rating - rest[k].rating) + go(remaining))
            }
            return best
        }
        return go(players)
    }
}
