package com.chess.gateway.config

import com.chess.gateway.ws.PlayerNotifier
import com.chess.gateway.ws.PlayerSessionRegistry
import com.chess.gateway.ws.PlayerWebSocketHandler
import com.chess.players.config.PlayersProperties
import com.chess.players.state.PlayerStateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
@EnableAsync
class GatewayConfiguration(private val handler: PlayerWebSocketHandler) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*")
    }

    companion object {
        @Bean
        fun playerSessionRegistry(mapper: ObjectMapper) = PlayerSessionRegistry(mapper)

        @Bean
        fun playerWebSocketHandler(
            registry: PlayerSessionRegistry,
            states: PlayerStateRepository,
            publisher: ApplicationEventPublisher,
            mapper: ObjectMapper,
        ) = PlayerWebSocketHandler(registry, states, publisher, mapper)

        @Bean
        fun playerNotifier(registry: PlayerSessionRegistry, props: PlayersProperties) =
            PlayerNotifier(registry, props.acceptTimeout)

        /** Small pool for outbound pushes. Each send is bounded (2s timeout in the session decorator), so a handful of threads is plenty. */
        @Bean(PlayerNotifier.NOTIFICATION_EXECUTOR)
        fun notificationExecutor(): Executor = ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 10_000
            setThreadNamePrefix("notify-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(5)
            initialize()
        }
    }
}
