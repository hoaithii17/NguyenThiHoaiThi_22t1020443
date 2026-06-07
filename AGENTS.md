# AGENTS.md

## Big picture (read this first)
- This is a Gradle multi-module Java 26 system with a strict split: `okx-feed-handler` writes market data, `trading-api-service` only reads/query-streams it.
- Data path is fixed: OKX WebSocket (`trades`, `books5`) -> `okx-feed-handler` -> QuestDB -> `trading-api-service` (REST + SSE).
- Do not add direct OKX connectivity to `trading-api-service`; keep it a pure QuestDB read layer (`trading-api-service/README.md`, `trading-api-service/src/main/java/.../QuestDbQueryClient.java`).
- Shared contracts and schema live in `hft-common` (DTOs, parsers, `QuestDbSchema`, config records).

## Module boundaries
- `hft-common`: shared DTO `record`s, `OkxFeedProperties`, `QuestDbProperties`, OKX parsers, QuestDB DDL (`hft-common/src/main/java/...`).
- `okx-feed-handler`: ingest service on `:8081`; schema init + Reactor Netty WS client + QuestDB ILP sender (`okx-feed-handler/src/main/java/...`).
- `trading-api-service`: HTTPS/H2 API on `:8443`; R2DBC queries and SSE delta-poll streams (`trading-api-service/src/main/java/...`).

## Critical workflows
- Start QuestDB first: `docker compose up -d` (`compose.yaml` exposes `9000` HTTP/ILP-over-HTTP, `9009` ILP TCP, `8812` PG wire, `9003` metrics).
- Start ingestion before API to ensure tables/data exist:
  - `./gradlew :okx-feed-handler:bootRun`
  - `./gradlew :trading-api-service:bootRun`
- Build/test from root:
  - `./gradlew build`
  - `./gradlew test`
- Trading API requires TLS keystore (`trading-api-service/src/main/resources/keystore.p12`); see `trading-api-service/README.md` for `mkcert` steps.

## Project-specific coding patterns
- Java/tooling conventions are centralized in `buildSrc/src/main/groovy/hft.java-conventions.gradle` (Java 26 toolchain, Lombok, JUnit 5, JVM flags for `sun.misc`/native access).
- QuestDB schema source-of-truth is `hft-common/src/main/java/.../schema/QuestDbSchema.java`; startup DDL execution is in `okx-feed-handler/.../SchemaInitializer.java`.
- `Sender` writes are funneled through a dedicated ingestion thread in `OkxWebSocketHandler` because the codebase treats QuestDB `Sender` as non-thread-safe.
- SSE endpoints use a consistent pattern in `StreamController`: `Flux.interval(Duration.ZERO, ...)`, `concatMap(...)`, cursor-based `since` queries, `withHeartbeat(...)`, and graceful shutdown via `Sinks.Empty`.
- Query logic is centralized in `QuestDbQueryClient`; controllers stay thin and mostly delegate.
- Financial number serialization is intentionally normalized (8 dp, no scientific notation) via `trading-api-service/.../config/JacksonConfig.java` and wired into WebFlux codecs in `WebClientConfig`.

## Integration details to preserve
- OKX channels currently assumed by parsers/ingestors: `trades` and `books5` (`OkxWebSocketHandler`, parsers in `hft-common/parser`).
- QuestDB query features used heavily: `LATEST ON`, `SAMPLE BY`, window functions (`LAG`, `ROWS BETWEEN`, `CUMULATIVE`) in `QuestDbQueryClient`.
- Supported symbols come from `okx.symbols` config and are validated in stream endpoints (`StreamController#validateSymbol`).

## Change playbook for agents
- Adding a new metric/indicator usually touches: DTO in `hft-common/dto`, SQL in `QuestDbQueryClient`, REST/SSE endpoint in `trading-api-service/controller`, and `trading-api-service/openapi.json`.
- Adding ingestion fields/channels usually touches: parser (`hft-common/parser`), ingestor (`okx-feed-handler/ingestion`), and potentially `QuestDbSchema` + startup DDL.
- Prefer extending existing reactive patterns rather than introducing blocking DB/web calls in controllers.
