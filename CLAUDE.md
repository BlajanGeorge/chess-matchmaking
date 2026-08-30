# CLAUDE.md

Guidance for AI assistants working in this repository. Read `README.md` first for what the system does.

## Build & test

No Maven wrapper; use the `mvn` on PATH. Java 17, Kotlin 2.1, Spring Boot 3.5.

```bash
mvn install -DskipTests                 # build everything (needed before -pl on a downstream module)
mvn test                                # all tests
mvn test -pl players -am                # one module plus its upstream modules
mvn test -pl matching -Dtest=RatingMatcherTest -Dsurefire.failIfNoSpecifiedTests=false
mvn spring-boot:run -pl app             # run on :8080
```

`-pl <module>` without `-am` fails to resolve sibling modules unless they were `install`ed — always add `-am`.

## Layout

Multi-module Maven, one-way dependencies `app → gateway → players → matching`. Package root is `com.chess`.
Sources under `src/main/kotlin`, tests under `src/test/kotlin`.

- `matching` — lobby repository, the sort+DP `RatingMatcher`, `MatcherJob`, `MatchFoundEvent`. Framework-light.
- `players` — per-player state machine and the services that drive it. Each user action / event has its own small
  service returning an `Outcome` enum (`LobbyJoinService`, `LobbyLeaveService`, `MatchProposalService`,
  `MatchAcceptService`, `MatchDeclineService`, `MatchTimeoutService`, `MatchStartService`, `MatchEndService`).
- `gateway` — WebSocket handler, session registry, notifier. Message DTOs in `ws/Messages.kt`.
- `app` — Spring Boot main, `application.yml`, `static/index.html`, end-to-end test.

Wiring is explicit `@Bean` methods in each module's `config/*Configuration.kt`; there is no component scanning of
services. New collaborators are added there.

## Conventions

- Tests: JUnit 5 + `kotlin.test` asserts, mockito-kotlin only where a real in-memory implementation is awkward.
  Names are backticked `given … when … then …`. Prefer the in-memory repositories over mocks.
- Every state transition goes through a repository method that is atomic for that transition (`compareAndSet`,
  `saveIfAbsent`, `removeIf`, `claim`, `markAccepted`, `remove`). Don't read-then-write.
- Services return `Outcome` enums; listeners log them. Don't throw for expected outcomes.
- Events between modules are Spring `ApplicationEvent`s published via `ApplicationEventPublisher`. State-changing
  listeners are synchronous; only the gateway's `PlayerNotifier` is `@Async` (executor `notificationExecutor`).
- Time comes from the `Clock` bean; `joinedAt`/`since` are server time, never client-supplied.
- When a player is "returned to the lobby", always use their original `LobbyPlayer` (keeps `joinedAt` and rating).

## Invariants to preserve (see README "Concurrency model")

1. In the lobby ⇒ `WAITING`. Join: state then lobby. Leave: lobby then state.
2. Exactly one actor resolves a pending match — whoever wins `PendingMatchRepository.remove(matchId)` — and exactly
   one ends an active one — whoever wins `ActiveMatchRepository.remove(matchId)`. Losers must not modify either
   player's state.
3. An accept is final: `decline`/`leave` must refuse after `accepted` contains the player.
4. Never start a match unless both `PENDING → IN_MATCH` CAS succeed (`MatchStartService`).
5. The matcher acts only on what it snapshotted: `LobbyRepository.claim(a, b)` removes the exact entries (same
   `joinedAt`), and `WAITING → PENDING` uses the exact-status `compareAndSet(expected: PlayerStatus, …)`. A player who
   left and re-joined in between is a *different* wait and is left for the next round.

If a change touches these, add a test for the interleaving it protects against — the existing ones in
`MatchAcceptServiceTest`, `MatchDeclineServiceTest`, `MatchProposalServiceTest`, `MatchTimeoutServiceTest` show the style.
`players/…/stress/ConcurrencyStressTest` hammers everything from many threads and checks the invariants at rest; it
takes ~15s and is the first thing to run after touching any state transition.

## Working style for this repo

- The owner is building this **step by step** and wants to discuss each step before code appears. Do exactly the step
  asked; don't pre-build the next one from the roadmap.
- Don't commit or push unless explicitly asked.
- The in-memory repositories are stand-ins for Redis (`LobbyRepository` ≈ sorted set + Lua claim,
  `PlayerStateRepository` ≈ hash + Lua CAS, `PendingMatchRepository` ≈ `pending:{matchId}`) and the Spring events for
  Kafka (one event per pair, keyed by `matchId`). Keep their interfaces shaped so that swap stays possible.

## Roadmap (not started)

Heartbeat + client reconnect + disconnect grace; richer `STATUS` for `PENDING`; match results / rating updates /
durable history; orphan sweeper; real `RatingProvider`; Redis/Kafka implementations; load test with fake WebSocket clients.
