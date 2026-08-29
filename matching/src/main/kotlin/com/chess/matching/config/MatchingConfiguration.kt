package com.chess.matching.config

import com.chess.matching.event.SpringEventMatchHandler
import com.chess.matching.lobby.InMemoryLobbyRepository
import com.chess.matching.lobby.LobbyRepository
import com.chess.matching.matcher.MatchHandler
import com.chess.matching.matcher.MatcherJob
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(MatchingProperties::class)
class MatchingConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun lobbyRepository(): LobbyRepository = InMemoryLobbyRepository()

    @Bean
    fun matchHandler(publisher: ApplicationEventPublisher, clock: Clock): MatchHandler =
        SpringEventMatchHandler(publisher, clock)

    @Bean
    fun matcherJob(lobby: LobbyRepository, handler: MatchHandler, props: MatchingProperties, clock: Clock) =
        MatcherJob(lobby, handler, props.toMatcherConfig(), clock)

    /** Starts the matcher once the context is up and stops it on shutdown. */
    @Bean
    fun matcherLifecycle(job: MatcherJob): SmartLifecycle = object : SmartLifecycle {
        @Volatile private var running = false
        override fun start() { job.start(); running = true }
        override fun stop() { job.stop(); running = false }
        override fun isRunning() = running
    }
}
