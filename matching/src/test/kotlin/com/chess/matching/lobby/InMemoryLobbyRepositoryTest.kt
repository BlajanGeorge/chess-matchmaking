package com.chess.matching.lobby

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryLobbyRepositoryTest {

    private val repo = InMemoryLobbyRepository()
    private val now = Instant.parse("2026-08-29T10:00:00Z")

    private fun player(id: String, rating: Int) = LobbyPlayer(id, rating, now)

    @Test
    fun `given players joined when snapshot then sorted by rating`() {
        repo.join(player("c", 1700))
        repo.join(player("a", 1500))
        repo.join(player("b", 1600))

        assertEquals(listOf("a", "b", "c"), repo.snapshot().map { it.id })
    }

    @Test
    fun `given player already waiting when join again then entry is replaced not duplicated`() {
        repo.join(player("a", 1500))
        repo.join(player("a", 1550))

        assertEquals(1, repo.size())
        assertEquals(1550, repo.find("a")?.rating)
    }

    @Test
    fun `given player waiting when leave then removed`() {
        repo.join(player("a", 1500))

        assertTrue(repo.leave("a"))
        assertFalse(repo.leave("a"))
        assertNull(repo.find("a"))
    }

    @Test
    fun `given both waiting when claim then both removed`() {
        repo.join(player("a", 1500))
        repo.join(player("b", 1510))

        assertTrue(repo.claim("a", "b"))
        assertEquals(0, repo.size())
    }

    @Test
    fun `given one already gone when claim then nothing removed`() {
        repo.join(player("a", 1500))

        assertFalse(repo.claim("a", "b"))
        assertEquals(1, repo.size())
    }

    @Test
    fun `given same id twice when claim then rejected`() {
        repo.join(player("a", 1500))

        assertFalse(repo.claim("a", "a"))
        assertEquals(1, repo.size())
    }

    @Test
    fun `given concurrent claims on the same player then exactly one succeeds`() {
        repo.join(player("a", 1500))
        repo.join(player("b", 1510))
        repo.join(player("c", 1520))

        val pool = Executors.newFixedThreadPool(2)
        val successes = AtomicInteger()
        repeat(2) { i ->
            pool.submit { if (repo.claim("a", if (i == 0) "b" else "c")) successes.incrementAndGet() }
        }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(1, successes.get())
        assertEquals(1, repo.size())
    }
}
