package com.chess.matching.matcher

import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyPlayer
import com.chess.matching.lobby.LobbyRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatcherJobTest {

    private val now = Instant.parse("2026-08-29T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val lobby = InMemoryLobbyRepository()
    private val matched = mutableListOf<Set<String>>()
    private val handler = MatchHandler { a, b -> matched += setOf(a.id, b.id) }

    private fun join(id: String, rating: Int, waited: Duration = Duration.ZERO) =
        lobby.join(LobbyPlayer(id, rating, now.minus(waited)))

    @Test
    fun `given matchable players when run once then claimed from lobby and handed over`() {
        join("a", 1500); join("b", 1510); join("c", 2400)
        val job = MatcherJob(lobby, handler, MatcherConfig(basePenalty = 25.0), clock)

        val stats = job.runOnce()

        assertEquals(listOf(setOf("a", "b")), matched)
        assertEquals(listOf("c"), lobby.snapshot().map { it.id })
        assertEquals(MatcherJob.RoundStats(candidates = 3, paired = 1, claimFailed = 0, unmatched = 1), stats)
    }

    @Test
    fun `given player waited long when run once then window has widened`() {
        join("a", 1500); join("b", 1700)
        val config = MatcherConfig(basePenalty = 25.0, penaltyPerSecond = 5.0, maxPenalty = 300.0)

        assertEquals(0, MatcherJob(lobby, handler, config, clock).runOnce().paired)

        lobby.leave("a"); lobby.leave("b")
        join("a", 1500, waited = Duration.ofSeconds(20)); join("b", 1700, waited = Duration.ofSeconds(20))
        assertEquals(1, MatcherJob(lobby, handler, config, clock).runOnce().paired)
    }

    @Test
    fun `given player left after snapshot when run once then pair is dropped and partner stays in lobby`() {
        join("a", 1500); join("b", 1510)
        val leavingLobby = object : LobbyRepository by lobby {
            override fun snapshot() = lobby.snapshot().also { lobby.leave("b") }
        }

        val stats = MatcherJob(leavingLobby, handler, MatcherConfig(), clock).runOnce()

        assertTrue(matched.isEmpty())
        assertEquals(1, stats.claimFailed)
        assertEquals(listOf("a"), lobby.snapshot().map { it.id })
    }

    @Test
    fun `given handler throws for one pair when run once then other pairs are still handled`() {
        join("a", 1500); join("b", 1505); join("c", 2000); join("d", 2005)
        val throwing = MatchHandler { x, y -> if ("a" in setOf(x.id, y.id)) throw IllegalStateException("boom") else matched += setOf(x.id, y.id) }

        val stats = MatcherJob(lobby, throwing, MatcherConfig(), clock).runOnce()

        assertEquals(listOf(setOf("c", "d")), matched)
        assertEquals(1, stats.paired)
        assertEquals(1, stats.handlerFailed)
        assertEquals(0, lobby.size())
    }

    @Test
    fun `given started job when players join then matched without calling run once`() {
        join("a", 1500); join("b", 1505)
        val job = MatcherJob(lobby, handler, MatcherConfig(interval = Duration.ofMillis(50)))

        job.start()
        try {
            val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
            while (matched.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
        } finally {
            job.stop()
        }

        assertEquals(listOf(setOf("a", "b")), matched)
    }
}
