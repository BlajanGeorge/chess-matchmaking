package com.chess.players.match

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Runs [MatchTimeoutService.expireOverdue] every [interval]. */
class MatchTimeoutJob(
    private val service: MatchTimeoutService,
    private val interval: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scheduler = AtomicReference<ScheduledExecutorService?>()

    fun start() {
        val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "match-timeout").apply { isDaemon = true } }
        if (!scheduler.compareAndSet(null, exec)) {
            exec.shutdown()
            return
        }
        exec.scheduleWithFixedDelay(::safeRun, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun stop() {
        scheduler.getAndSet(null)?.shutdownNow()
    }

    private fun safeRun() {
        try {
            val expired = service.expireOverdue()
            if (expired.isNotEmpty()) log.debug("expired {} pending matches: {}", expired.size, expired)
        } catch (e: Exception) {
            log.warn("match timeout sweep failed", e)
        }
    }
}
