# OKX HFT — High-Frequency Trading Demo

A multi-module Spring Boot application that ingests real-time market data from the **OKX v5 WebSocket API**, stores it in **QuestDB** (a high-performance time-series database), and streams tick-by-tick data to a frontend dashboard via **Server-Sent Events**.

---

## Architecture

```
  ┌──────────────────────────────────────┐
  │           OKX v5 WebSocket           │
  │   wss://ws.okx.com:8443/ws/v5/public │
  └──────────────┬───────────────────────┘
                 │  trades + books5
      ┌──────────▼──────────┐
      │   okx-feed-handler  │   (Single ingestor)
      │      port 8081      │
      │                     │
      │  Reactive WS client │
      │  ILP Sender (write) │
      │  Schema init        │
      └──────────┬──────────┘
                 │ ILP/HTTP :9000
      ┌──────────▼──────────┐    R2DBC :8812    ┌──────────────────────┐
      │       QuestDB       │◄──────────────────│  trading-api-service │
      │   port 9000 (HTTP)  │                   │   port 8443 (HTTPS)  │
      │   port 8812 (PG)    │                   │                      │
      │   port 9009 (ILP)   │                   │  Pure read layer     │
      └─────────────────────┘                   │  R2DBC queries       │
                                                │  SSE + REST          │
                                                └──────────┬───────────┘
                                                           │ HTTPS SSE + REST
                                                ┌──────────▼───────────┐
                                                │   Next.js Dashboard  │
                                                │   (HTTPS, mkcert)    │
                                                └──────────────────────┘
```

### Data Flow

| Path | Protocol | Purpose |
|------|----------|---------|
| OKX WS → `okx-feed-handler` → QuestDB | ILP/HTTP `:9000` | **Ingestion**: single writer, stores every tick durably |
| `trading-api-service` → QuestDB → SSE clients | R2DBC `:8812` → **HTTPS/H2** `:8443` | **Streaming**: trades (500ms), orderbook, spread, candles (2s), RSI/Bollinger/VWAP (3s) |
| `trading-api-service` → QuestDB | R2DBC `:8812` | **Historical queries**: candles, indicators, stats |

> The **okx-feed-handler** is the single ingestion point — it connects to OKX WebSocket and writes to QuestDB. The **trading-api-service** is a pure read layer — it queries QuestDB via R2DBC and never connects to OKX directly.
>
> All SSE streams run over **HTTP/2**, multiplexing all 7 streams over a single TCP connection (no browser 6-connection limit). Each stream emits **individual objects** (not arrays) with `:heartbeat` keepalive every 15s.

---

## Modules

| Module | Description |
|--------|-------------|
| [`hft-common`](./hft-common/README.md) | Shared DTOs, config, parsers, QuestDB schema DDL |
| [`okx-feed-handler`](./okx-feed-handler/README.md) | Ingestor: OKX WS → QuestDB via ILP Sender |
| [`trading-api-service`](./trading-api-service/README.md) | API: SSE streaming + historical REST — pure QuestDB R2DBC read layer |

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 26 |
| Gradle | 9.x (via wrapper) |
| Docker + Docker Compose | any recent version |
| mkcert | any (`sudo pacman -S mkcert nss` on Arch) |

---

## Quick Start

### 1. Start QuestDB

```bash
docker compose up -d
```

QuestDB will be available at:
- **Web Console**: http://localhost:9000
- **PostgreSQL wire**: `localhost:8812`
- **ILP (TCP)**: `localhost:9009`
- **Metrics**: http://localhost:9003/metrics

### 2. Generate TLS certificates (first time only)

The trading API runs on HTTPS with an **ECDSA P-256** certificate issued by a local [mkcert](https://github.com/FiloSottile/mkcert) CA.
See [`trading-api-service/README.md` → TLS / HTTPS Setup](./trading-api-service/README.md#tls--https-setup) for full details.

```bash
# Trust the mkcert CA (system + Chromium + Firefox — all at once)
mkcert -install

# Generate the ECDSA PKCS#12 keystore for Spring Boot
cd trading-api-service/src/main/resources
mkcert -ecdsa -pkcs12 -p12-file keystore.p12 localhost 127.0.0.1 ::1
cd ../../../..
```

> **Windows:** Same commands — run `mkcert -install` from an Administrator terminal.

### 3. Start the feed handler (ingestion)

```bash
./gradlew :okx-feed-handler:bootRun
```

On startup it will:
1. Create QuestDB tables (`trades`, `orderbook`) and materialized views (`candles_5s`, `candles_1m`, `candles_1h`)
2. Connect to OKX WebSocket and begin ingesting ticks

### 4. Start the trading API

```bash
./gradlew :trading-api-service:bootRun
```

API is available at https://localhost:8443. See the [OpenAPI spec](./trading-api-service/openapi.json) for full endpoint documentation.

---

## Build

```bash
# Build all modules
./gradlew build

# Build a single module
./gradlew :trading-api-service:build

# Run all tests
./gradlew test
```

---

## Project Structure

```
okx-hft/
├── build.gradle                  # Root build — Spring Boot plugin declarations
├── settings.gradle               # Module includes
├── gradle.properties             # Gradle tuning (parallel, cache, config-cache)
├── compose.yaml                  # QuestDB Docker Compose (ports 9000/8812/9009/9003)
├── buildSrc/
│   └── src/main/groovy/
│       └── hft.java-conventions.gradle   # Shared Java 26, Lombok, JVM args
├── hft-common/                   # Shared library
├── okx-feed-handler/             # Feed handler service
└── trading-api-service/          # REST + SSE API service
```

---

## QuestDB Schema

```sql
-- Raw tick data
CREATE TABLE trades (ts TIMESTAMP, symbol SYMBOL, side SYMBOL, price DOUBLE, amount DOUBLE)
  TIMESTAMP(ts) PARTITION BY DAY WAL DEDUP UPSERT KEYS(ts, symbol);

CREATE TABLE orderbook (ts TIMESTAMP, symbol SYMBOL,
  bid1_price DOUBLE, bid1_size DOUBLE, ... ask5_price DOUBLE, ask5_size DOUBLE,
  mid_price DOUBLE, spread DOUBLE)
  TIMESTAMP(ts) PARTITION BY DAY WAL DEDUP UPSERT KEYS(ts, symbol);

-- Cascading materialized views (auto-updated by QuestDB)
CREATE MATERIALIZED VIEW candles_5s AS (SELECT ts, symbol, first(price) AS open, ... FROM trades SAMPLE BY 5s);
CREATE MATERIALIZED VIEW candles_1m AS (SELECT ts, symbol, first(open) AS open, ... FROM candles_5s SAMPLE BY 1m);
CREATE MATERIALIZED VIEW candles_1h AS (SELECT ts, symbol, first(open) AS open, ... FROM candles_1m SAMPLE BY 1h);
```

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 26 |
| Framework | Spring Boot 4.x, Spring WebFlux |
| Reactive | Project Reactor |
| Database | QuestDB 9.3.3 |
| DB write | `questdb-client` 1.0.1 (ILP/HTTP Sender) |
| DB read | Spring Data R2DBC + `r2dbc-postgresql` |
| WS client | Reactor Netty WebSocket (feed handler only) |
| Build | Gradle 9 multi-module |
| Runtime | Docker Compose |

