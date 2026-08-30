package com.chess.players.config

import com.chess.matching.lobby.LobbyRepository
import com.chess.players.event.PlayerEventListener
import com.chess.players.lobby.LobbyJoinService
import com.chess.players.lobby.LobbyLeaveService
import com.chess.players.match.ActiveMatchRepository
import com.chess.players.match.InMemoryActiveMatchRepository
import com.chess.players.match.InMemoryPendingMatchRepository
import com.chess.players.match.MatchAcceptService
import com.chess.players.match.MatchDeclineService
import com.chess.players.match.MatchEndService
import com.chess.players.match.MatchProposalService
import com.chess.players.match.MatchStartService
import com.chess.players.match.MatchTimeoutJob
import com.chess.players.match.MatchTimeoutService
import com.chess.players.match.PendingMatchRepository
import com.chess.players.rating.RandomRatingProvider
import com.chess.players.rating.RatingProvider
import com.chess.players.state.InMemoryPlayerStateRepository
import com.chess.players.state.PlayerStateRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(PlayersProperties::class)
class PlayersConfiguration {

    @Bean
    fun playerStateRepository(): PlayerStateRepository = InMemoryPlayerStateRepository()

    @Bean
    fun pendingMatchRepository(): PendingMatchRepository = InMemoryPendingMatchRepository()

    @Bean
    fun activeMatchRepository(): ActiveMatchRepository = InMemoryActiveMatchRepository()

    @Bean
    fun ratingProvider(): RatingProvider = RandomRatingProvider()

    @Bean
    fun lobbyJoinService(states: PlayerStateRepository, lobby: LobbyRepository, ratings: RatingProvider, clock: Clock) =
        LobbyJoinService(states, lobby, ratings, clock)

    @Bean
    fun matchDeclineService(
        states: PlayerStateRepository,
        pending: PendingMatchRepository,
        lobby: LobbyRepository,
        publisher: ApplicationEventPublisher,
    ) = MatchDeclineService(states, pending, lobby, publisher)

    @Bean
    fun matchStartService(
        states: PlayerStateRepository,
        lobby: LobbyRepository,
        active: ActiveMatchRepository,
        publisher: ApplicationEventPublisher,
        clock: Clock,
    ) = MatchStartService(states, lobby, active, publisher, clock)

    @Bean
    fun matchEndService(
        states: PlayerStateRepository,
        active: ActiveMatchRepository,
        publisher: ApplicationEventPublisher,
        clock: Clock,
    ) = MatchEndService(states, active, publisher, clock)

    @Bean
    fun matchAcceptService(states: PlayerStateRepository, pending: PendingMatchRepository, starter: MatchStartService) =
        MatchAcceptService(states, pending, starter)

    @Bean
    fun lobbyLeaveService(states: PlayerStateRepository, lobby: LobbyRepository, decline: MatchDeclineService) =
        LobbyLeaveService(states, lobby, decline)

    @Bean
    fun matchProposalService(
        states: PlayerStateRepository,
        pending: PendingMatchRepository,
        lobby: LobbyRepository,
        publisher: ApplicationEventPublisher,
    ) = MatchProposalService(states, pending, lobby, publisher)

    @Bean
    fun matchTimeoutService(
        states: PlayerStateRepository,
        pending: PendingMatchRepository,
        lobby: LobbyRepository,
        props: PlayersProperties,
        clock: Clock,
        publisher: ApplicationEventPublisher,
        starter: MatchStartService,
    ) = MatchTimeoutService(states, pending, lobby, props.acceptTimeout, clock, publisher, starter)

    @Bean
    fun matchTimeoutJob(service: MatchTimeoutService, props: PlayersProperties) =
        MatchTimeoutJob(service, props.timeoutSweepInterval)

    @Bean
    fun matchTimeoutLifecycle(job: MatchTimeoutJob): SmartLifecycle = object : SmartLifecycle {
        @Volatile private var running = false
        override fun start() { job.start(); running = true }
        override fun stop() { job.stop(); running = false }
        override fun isRunning() = running
    }

    @Bean
    fun playerEventListener(
        joinService: LobbyJoinService,
        leaveService: LobbyLeaveService,
        proposalService: MatchProposalService,
        declineService: MatchDeclineService,
        acceptService: MatchAcceptService,
        endService: MatchEndService,
    ) = PlayerEventListener(joinService, leaveService, proposalService, declineService, acceptService, endService)
}
