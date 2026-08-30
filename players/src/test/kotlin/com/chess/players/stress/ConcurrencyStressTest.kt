package com.chess.players.stress

import com.chess.matching.event.MatchFoundEvent
import com.chess.matching.event.SpringEventMatchHandler
import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.matcher.MatcherConfig
import com.chess.matching.matcher.MatcherJob
import com.chess.players.event.MatchCancelledEvent
import com.chess.players.event.MatchProposedEvent
import com.chess.players.event.MatchStartedEvent
import com.chess.players.lobby.LobbyJoinService
import com.chess.players.lobby.LobbyLeaveService
import com.chess.players.match.InMemoryPendingMatchRepository
import com.chess.players.match.MatchAcceptService
import com.chess.players.match.MatchDeclineService
import com.chess.players.match.MatchProposalService
import com.chess.players.match.MatchStartService
import com.chess.players.match.MatchTimeoutService
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerState
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Hammers the whole state machine from many threads at once — random join / leave / accept / decline / "match ended"
 * per player, with the matcher and the timeout sweeper spinning as fast as they can — then stops everything and checks
 * the invariants on the final state. Any interleaving that corrupts state shows up here as a broken invariant.
 */
class ConcurrencyStressTest {

    private val players = 300
    private val workers = 8
    private val runFor = Duration.ofSeconds(3)

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = [1, 2, 3])
    fun `given paced random concurrent activity when everything stops then all invariants hold`(seed: Long) = run(seed, paced = true)

    /** Workers at full speed: proposals are resolved within microseconds, which is where the tightest interleavings live. */
    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = [11, 12])
    fun `given unpaced random concurrent activity when everything stops then all invariants hold`(seed: Long) = run(seed, paced = false)

    private fun run(seed: Long, paced: Boolean) {
        val clock = Clock.systemUTC()
        val lobby = InMemoryLobbyRepository()
        val states = InMemoryPlayerStateRepository()
        val pending = InMemoryPendingMatchRepository()

        val started = CopyOnWriteArrayList<MatchStartedEvent>()
        val cancelled = CopyOnWriteArrayList<MatchCancelledEvent>()
        val proposed = CopyOnWriteArrayList<MatchProposedEvent>()
        lateinit var proposalService: MatchProposalService
        // routes events exactly like the synchronous Spring listeners do
        val publisher = ApplicationEventPublisher { e ->
            when (e) {
                is MatchFoundEvent -> proposalService.onMatchFound(e)
                is MatchStartedEvent -> started += e
                is MatchCancelledEvent -> cancelled += e
                is MatchProposedEvent -> proposed += e
            }
        }
        val starter = MatchStartService(states, lobby, publisher, clock)
        proposalService = MatchProposalService(states, pending, lobby, publisher)
        val join = LobbyJoinService(states, lobby, { 1500 + (it.hashCode() and 0xff) }, clock)
        val decline = MatchDeclineService(states, pending, lobby, publisher)
        val leave = LobbyLeaveService(states, lobby, decline)
        val accept = MatchAcceptService(states, pending, starter)
        val timeout = MatchTimeoutService(states, pending, lobby, Duration.ofMillis(40), clock, publisher, starter)
        val matcher = MatcherJob(lobby, SpringEventMatchHandler(publisher, clock), MatcherConfig(basePenalty = 1000.0, maxPenalty = 1000.0), clock)

        val running = AtomicBoolean(true)
        val actions = AtomicInteger()
        val ended = ConcurrentHashMap.newKeySet<Pair<String, String>>() // (playerId, matchId) that "finished" the match
        val ids = List(players) { "p$it" }

        val threads = mutableListOf<Thread>()
        threads += Thread({ while (running.get()) { matcher.runOnce(); Thread.sleep(1) } }, "matcher")
        threads += Thread({ while (running.get()) { timeout.expireOverdue(); Thread.sleep(1) } }, "sweeper")
        repeat(workers) { w ->
            threads += Thread({
                val rnd = Random(seed * 1000 + w)
                while (running.get()) {
                    val id = ids[rnd.nextInt(ids.size)]
                    val status = states.find(id)
                    when (status?.state) {
                        null -> if (rnd.nextInt(100) < 70) join.join(id)
                        PlayerState.WAITING -> when (rnd.nextInt(100)) {
                            in 0..9 -> leave.leave(id)
                            in 10..14 -> join.join(id) // idempotent re-join
                        }
                        PlayerState.PENDING -> when (rnd.nextInt(100)) {
                            in 0..24 -> accept.accept(id, status.matchId!!)
                            in 25..31 -> decline.decline(id, status.matchId!!)
                            in 32..35 -> leave.leave(id)
                            in 36..38 -> accept.accept(id, "stale-match") // wrong matchId, must be a no-op
                            // otherwise: think about it — some proposals must time out
                        }
                        PlayerState.IN_MATCH -> if (rnd.nextInt(100) < 30) {
                            // no "match ended" flow exists yet: simulate it by leaving the flow
                            if (states.removeIf(id, PlayerState.IN_MATCH)) ended += id to status.matchId!!
                        }
                    }
                    actions.incrementAndGet()
                    if (paced) Thread.sleep(0, 200_000) // ~0.2ms: slower than the sweeper, so some proposals time out
                }
            }, "worker-$w")
        }
        threads.forEach { it.start() }
        Thread.sleep(runFor.toMillis())
        running.set(false)
        threads.forEach { it.join(5_000) }
        threads.forEach { assertTrue(!it.isAlive, "${it.name} did not stop") }

        val byReason = cancelled.groupingBy { it.reason }.eachCount()
        println("seed $seed: actions=${actions.get()} proposed=${proposed.size} started=${started.size} cancelled=$byReason ended=${ended.size} " +
            "final: lobby=${lobby.size()} pending=${pending.findProposedBefore(clock.instant().plusSeconds(1)).size}")
        assertTrue(proposed.size > 100, "not enough activity to mean anything: ${proposed.size} proposals")
        assertTrue(started.size > 10, "no matches started")
        assertTrue(cancelled.any { it.reason == MatchCancelledEvent.Reason.DECLINED }, "no declines happened")
        if (paced) assertTrue(cancelled.any { it.reason == MatchCancelledEvent.Reason.TIMEOUT }, "no timeouts happened")

        // I1: in the lobby ⇒ WAITING, same joinedAt
        for (p in lobby.snapshot()) {
            val s = states.find(p.id)
            assertNotNull(s, "${p.id} is in the lobby but has no state")
            assertEquals(PlayerState.WAITING, s.state, "${p.id} is in the lobby but is ${s.state}")
            assertEquals(p.joinedAt, s.since, "${p.id}: lobby joinedAt != state since")
        }
        // I2: WAITING ⇒ in the lobby
        val allStates = ids.mapNotNull { states.find(it) }
        for (s in allStates.filter { it.state == PlayerState.WAITING }) {
            assertNotNull(lobby.find(s.playerId), "${s.playerId} is WAITING but not in the lobby")
        }
        // I3: PENDING ⇔ pending match containing the player; both players of a pending match are PENDING on it
        val pendingMatches = pending.findProposedBefore(clock.instant().plusSeconds(1)).associateBy { it.matchId }
        for (s in allStates.filter { it.state == PlayerState.PENDING }) {
            val m = pendingMatches[s.matchId]
            assertNotNull(m, "${s.playerId} is PENDING on ${s.matchId} but that match does not exist")
            assertTrue(s.playerId == m.playerA.id || s.playerId == m.playerB.id, "${s.playerId} PENDING on a match not containing them")
        }
        for (m in pendingMatches.values) {
            for (p in listOf(m.playerA, m.playerB)) {
                val s = states.find(p.id)
                assertEquals(PlayerState.PENDING, s?.state, "${p.id} is in pending ${m.matchId} but is ${s?.state}")
                assertEquals(m.matchId, s?.matchId, "${p.id} is in pending ${m.matchId} but PENDING on ${s?.matchId}")
            }
        }
        // I4: IN_MATCH ⇒ that match was started with this player; partner is IN_MATCH on it or has ended *that* match
        val startedById = started.associateBy { it.matchId }
        for (s in allStates.filter { it.state == PlayerState.IN_MATCH }) {
            val m = startedById[s.matchId]
            assertNotNull(m, "${s.playerId} is IN_MATCH on ${s.matchId} but no MatchStartedEvent for it")
            val partner = if (m.playerA.id == s.playerId) m.playerB else m.playerA
            assertTrue(m.playerA.id == s.playerId || m.playerB.id == s.playerId, "${s.playerId} IN_MATCH on a match not containing them")
            val ps = states.find(partner.id)
            val partnerStillIn = ps?.state == PlayerState.IN_MATCH && ps.matchId == s.matchId
            val partnerEndedIt = (partner.id to s.matchId) in ended
            assertTrue(partnerStillIn || partnerEndedIt, "${s.playerId} IN_MATCH on ${s.matchId} but partner ${partner.id} is $ps and never ended it")
        }
        // I5: every match is resolved at most once, and never both started and cancelled
        val startedIds = started.map { it.matchId }
        assertEquals(startedIds.size, startedIds.toSet().size, "a match was started twice")
        val cancelledIds = cancelled.map { it.matchId }
        assertEquals(cancelledIds.size, cancelledIds.toSet().size, "a match was cancelled twice")
        assertTrue(startedIds.toSet().intersect(cancelledIds.toSet()).isEmpty(), "a match was both started and cancelled")
        // I6: no player appears in two live pending matches
        val pendingPlayers = pendingMatches.values.flatMap { listOf(it.playerA.id, it.playerB.id) }
        assertEquals(pendingPlayers.size, pendingPlayers.toSet().size, "a player is in two pending matches")
        // I7: nobody is "lost": every player is idle, or in exactly the state the structures say
        val seen = ConcurrentHashMap<String, Int>()
        lobby.snapshot().forEach { seen.merge(it.id, 1, Int::plus) }
        pendingPlayers.forEach { seen.merge(it, 1, Int::plus) }
        for ((id, n) in seen) assertEquals(1, n, "$id appears in $n structures")
    }
}
