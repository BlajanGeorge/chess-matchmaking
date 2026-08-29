package com.chess.players.state

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryPlayerStateRepositoryTest {

    private val repo = InMemoryPlayerStateRepository()
    private val now = Instant.parse("2026-08-29T10:00:00Z")

    private fun waiting(id: String) = PlayerStatus(id, PlayerState.WAITING, since = now)
    private fun pending(id: String, matchId: String = "m1") = PlayerStatus(id, PlayerState.PENDING, matchId, now)

    @Test
    fun `given no entry when find then null`() {
        assertNull(repo.find("a"))
    }

    @Test
    fun `given saved status when find then returned`() {
        repo.save(waiting("a"))
        assertEquals(waiting("a"), repo.find("a"))
    }

    @Test
    fun `given expected state matches when compareAndSet then swapped`() {
        repo.save(waiting("a"))

        assertTrue(repo.compareAndSet("a", PlayerState.WAITING, pending("a")))
        assertEquals(pending("a"), repo.find("a"))
    }

    @Test
    fun `given expected state differs when compareAndSet then unchanged`() {
        repo.save(pending("a"))

        assertFalse(repo.compareAndSet("a", PlayerState.WAITING, PlayerStatus("a", PlayerState.IN_MATCH, "m1", now)))
        assertEquals(pending("a"), repo.find("a"))
    }

    @Test
    fun `given no entry when compareAndSet then false`() {
        assertFalse(repo.compareAndSet("a", PlayerState.WAITING, pending("a")))
        assertNull(repo.find("a"))
    }

    @Test
    fun `given status for another player when compareAndSet then rejected`() {
        repo.save(waiting("a"))
        assertThrows<IllegalArgumentException> { repo.compareAndSet("a", PlayerState.WAITING, pending("b")) }
    }

    @Test
    fun `given exact status matches when compareAndSet by status then swapped`() {
        repo.save(waiting("a"))

        assertTrue(repo.compareAndSet(waiting("a"), pending("a")))
        assertEquals(pending("a"), repo.find("a"))
    }

    @Test
    fun `given same state but different since when compareAndSet by status then unchanged`() {
        repo.save(waiting("a"))
        val otherWaiting = PlayerStatus("a", PlayerState.WAITING, since = now.plusSeconds(1))

        assertFalse(repo.compareAndSet(otherWaiting, pending("a")))
        assertEquals(waiting("a"), repo.find("a"))
    }

    @Test
    fun `given expected state when removeIf then removed else kept`() {
        repo.save(waiting("a"))

        assertFalse(repo.removeIf("a", PlayerState.PENDING))
        assertEquals(waiting("a"), repo.find("a"))
        assertTrue(repo.removeIf("a", PlayerState.WAITING))
        assertNull(repo.find("a"))
    }

    @Test
    fun `given pending without matchId when construct then rejected`() {
        assertThrows<IllegalArgumentException> { PlayerStatus("a", PlayerState.PENDING, since = now) }
        assertThrows<IllegalArgumentException> { PlayerStatus("a", PlayerState.WAITING, matchId = "m1", since = now) }
    }

    @Test
    fun `given concurrent compareAndSet from WAITING then exactly one wins`() {
        repo.save(waiting("a"))
        val pool = Executors.newFixedThreadPool(8)
        val wins = AtomicInteger()
        repeat(8) { i ->
            pool.submit { if (repo.compareAndSet("a", PlayerState.WAITING, pending("a", "m$i"))) wins.incrementAndGet() }
        }
        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(1, wins.get())
        assertEquals(PlayerState.PENDING, repo.find("a")?.state)
    }
}
