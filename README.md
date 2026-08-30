# chess-matchmaking

Experiment: rating-based matchmaking at scale, built as a single multi-module Maven project (Kotlin 2.1, Java 17,
Spring Boot 3.5). One process, in-memory state, Spring `ApplicationEvent`s between modules — the module boundaries
are drawn where service boundaries (Redis, Kafka, gateway tier) would go later.

## Run it

```bash
mvn install -DskipTests
mvn spring-boot:run -pl app
```

Open <http://localhost:8080> in two tabs, enter two different player ids, connect, **Enter lobby** in both. Within a
second you get a match proposal with a 15s countdown; accept in both to "start" the game.

Ratings are random (1000–3000) for now, and `application.yml` uses a wide-open rating window so any two players pair
immediately. Production-like values are in the comments there.

## What it does

```
 client ──ws──▶ gateway ──events──▶ players ──state──▶ matching
   ▲              │                    │                  │
   └──────push────┘◀───match events────┘◀──MatchFound────┘
```

1. A player connects over WebSocket (`/ws?playerId=…`) and sends `JOIN_LOBBY`.
2. `players` puts them in `WAITING` state and into the lobby with a server-side `joinedAt` and their rating.
3. Every second the **matcher** takes a snapshot of the lobby, runs the matching algorithm, and for each resulting pair
   atomically **claims** both players out of the lobby, then publishes one `MatchFoundEvent` per pair.
4. `players` moves both to `PENDING` and records a pending match; the gateway pushes `MATCH_PROPOSED` to both.
5. Each player has `players.accept-timeout` (15s) to `ACCEPT_MATCH` or `DECLINE_MATCH`. An accept is final.
   - both accept → both `IN_MATCH`, `MATCH_STARTED` pushed
   - one declines / leaves → the other goes **back to the lobby with their original wait time**
   - nobody resolves it → a server-side sweeper expires it: whoever accepted returns to the lobby, the rest are dropped

## The matching algorithm

Cost model: pairing two players costs their rating gap; leaving a player unmatched for a round costs a per-player
penalty that **grows with wait time** and is capped. Two players can only pair when their gap is below the sum of their
penalties — so a fresh player only accepts close opponents, a long-waiting one accepts a wider range, and the widest
window is `2 × maxPenalty`.

With a one-dimensional cost like this, the optimal matching never has crossing pairs, so it is solved exactly by
**sort by rating + dynamic programming** over the sorted list (each player pairs with an earlier one, everyone strictly
between them left alone, or stays alone), bounded by the `2 × maxPenalty` window. That is `O(n log n)`; 100k players
match in well under a second, and no graph matching (blossom) is needed. The random tests check the result against a
brute-force optimum over *all* matchings.

Knobs (`matching.*`): `interval`, `base-penalty`, `penalty-per-second`, `max-penalty`.

## Modules

| module | package | what |
|---|---|---|
| `matching` | `com.chess.matching` | `LobbyRepository` (in-memory stand-in for a Redis sorted set, with an atomic pair `claim`), `RatingMatcher` (sort + DP), `MatcherJob` (scheduled rounds), `MatchFoundEvent` |
| `players` | `com.chess.players` | per-player state (`WAITING / PENDING / IN_MATCH`, CAS-guarded), pending matches with accepts, the join / leave / propose / accept / decline / timeout services, outbound `MatchProposed / MatchStarted / MatchCancelled` events |
| `gateway` | `com.chess.gateway` | one WebSocket per player, `playerId → session` registry, client commands → player events, async push of match events |
| `app` | `com.chess.app` | Spring Boot main, `application.yml`, the test page, end-to-end test with real WebSocket clients |

Dependencies flow one way: `app → gateway → players → matching`.

## WebSocket protocol

Client → server (JSON): `{"type": "JOIN_LOBBY" | "LEAVE_LOBBY" | "ACCEPT_MATCH" | "DECLINE_MATCH" | "STATUS", "matchId"?: "…"}`

Server → client, all carry `type`:

| type | fields | when |
|---|---|---|
| `STATUS` | `state` (`WAITING`/`PENDING`/`IN_MATCH`/`null`), `matchId` | on connect and after every command — the client never depends on a push it may have missed |
| `MATCH_PROPOSED` | `matchId`, `opponent{id, rating}`, `expiresInSeconds` | both players claimed |
| `MATCH_STARTED` | `matchId`, `opponent` | both accepted |
| `MATCH_CANCELLED` | `matchId`, `reason` (`DECLINED`/`TIMEOUT`/`PARTNER_UNAVAILABLE`), `backInLobby` | proposal fell through |
| `ERROR` | `message` | bad command |

## Concurrency model

Three kinds of threads touch shared state: request threads (client commands), the matcher thread, the timeout sweeper.
State-changing listeners run synchronously on the publishing thread; only outbound WebSocket pushes are async (a
dedicated executor), so a slow client never stalls matchmaking.

Invariants and how they are kept:

- **in the lobby ⇒ `WAITING`.** Join writes state first, then the lobby; leave does the reverse. The matcher only acts
  on what it snapshotted: `claim` removes the *exact* lobby entries it saw and the `WAITING → PENDING` step is guarded
  on the *exact* `WAITING` (same `joinedAt`), so a player who left and re-joined in between is neither claimed nor moved.
- **a pending match is resolved by exactly one actor.** Decline, the second accept and the timeout sweeper all race on
  an atomic `remove(matchId)`; the winner owns both players' state, the losers touch nothing. A fully-accepted match
  that the sweeper happens to win is started, not cancelled.
- **an accept is final.** After accepting, decline and leave are refused; this is what makes the second accept safe.
- **a match never starts with one player.** `MatchStartService` checks both CAS results and reverts if either failed.
- **a failing pair doesn't take the round down.** Handler exceptions are isolated per pair in `MatcherJob`.

## Tests

```bash
mvn test                       # everything
mvn test -pl players -am       # one module (+ what it depends on)
```

Naming: `given … when … then …`. The matcher is verified against brute force; accept/claim have concurrent tests; the
`app` module has an end-to-end test driving two real WebSocket clients through join → proposal → accept → start.
`ConcurrencyStressTest` (in `players`) runs 8 threads of random join/leave/accept/decline against the live matcher and
sweeper for 3s per seed, then checks every invariant above on the final state.

## Known gaps / next steps

- **No heartbeat, no reconnect, no disconnect grace.** A vanished client stays `WAITING` until a proposal times out.
- `STATUS` in `PENDING` doesn't carry the opponent, so a reconnecting client can't rebuild the match card.
- **`IN_MATCH` has no exit** — there is no "match ended" flow yet; a player who played once can't re-join.
- Pair atomicity is two CAS operations plus reverts, not one transaction; the orphan sweeper from the design is not
  built (with in-process synchronous events the window is negligible; it becomes real with Redis/Kafka).
- Ratings are random; `RatingProvider` is the seam for a real source.
- Everything is in-memory and single-instance. The Redis / Kafka / multi-gateway shape this was designed for is
  described in the module docs and comments; nothing is committed to it yet.
