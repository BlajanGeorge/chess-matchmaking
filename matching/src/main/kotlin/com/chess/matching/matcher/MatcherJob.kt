package com.chess.matching.matcher

import com.chess.matching.lobby.LobbyRepository
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

/**
 * Periodically takes a snapshot of the lobby, runs the matcher, and claims each resulting pair.
 *
 * The snapshot may be stale (players leave, other rounds still in flight), so the claim is what makes a pair real:
 * a pair whose claim fails is simply dropped — the survivor is still in the lobby and gets picked up next round.
 */
class MatcherJob(
    private val lobby: LobbyRepository,
    private val handler: MatchHandler,
    private val config: MatcherConfig = MatcherConfig(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * @property handlerFailed pairs that were claimed but whose handler threw. They are no longer in the lobby and
     * nobody moved their state — an orphan for the sweeper; they must not take the rest of the round down with them.
     */
    data class RoundStats(val candidates: Int, val paired: Int, val claimFailed: Int, val unmatched: Int, val handlerFailed: Int = 0)

    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = AtomicReference<ScheduledExecutorService?>()

    fun start() {
        val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "matcher").apply { isDaemon = true } }
        if (!scheduler.compareAndSet(null, exec)) {
            exec.shutdown()
            return
        }
        exec.scheduleWithFixedDelay(::safeRunOnce, 0, config.interval.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun stop() {
        scheduler.getAndSet(null)?.shutdownNow()
    }

    fun runOnce(): RoundStats {
        val now = clock.instant()
        val snapshot = lobby.snapshot()
        val matcher = RatingMatcher { p ->
            config.alonePenalty(Duration.between(p.joinedAt, now).toMillis() / 1000.0)
        }
        val result = matcher.match(snapshot)

        var paired = 0
        var claimFailed = 0
        var handlerFailed = 0
        for ((a, b) in result.pairs) {
            if (!lobby.claim(a.id, b.id)) {
                claimFailed++
                continue
            }
            try {
                handler.onMatched(a, b)
                paired++
            } catch (e: Exception) {
                handlerFailed++
                log.error("handler failed for pair {} / {}; both are claimed out of the lobby", a.id, b.id, e)
            }
        }
        return RoundStats(snapshot.size, paired, claimFailed, result.unmatched.size, handlerFailed)
    }

    private fun safeRunOnce() {
        try {
            val stats = runOnce()
            if (stats.candidates > 0) log.debug("matcher round: {}", stats)
        } catch (e: Exception) {
            // a failing round must not kill the scheduler; next round retries from a fresh snapshot
            log.warn("matcher round failed", e)
        }
    }
}
