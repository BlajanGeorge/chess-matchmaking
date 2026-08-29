package com.chess.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.chess"])
class ChessMatchmakingApplication

fun main(args: Array<String>) {
    runApplication<ChessMatchmakingApplication>(*args)
}
