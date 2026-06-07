# trading-api-service

Spring Boot WebFlux service providing **near real-time SSE streaming** and **historical REST APIs** for the OKX HFT demo dashboard.

- **Port**: `8443` (HTTPS/H2 — mkcert ECDSA P-256)
- **Role**: Pure read layer — all data sourced from QuestDB via R2DBC
- **Data source**: QuestDB via R2DBC (PostgreSQL wire, port `8812`)
- **Architecture**: The feed handler (`okx-feed-handler`) is the single ingestion point from OKX WebSocket → QuestDB. This service **never connects to OKX directly**.

---

## API Overview

Full specification: [`openapi.json`](./openapi.json) (OpenAPI 3.1)

### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/trades` | Recent trades from QuestDB (last 1h) |
| `GET` | `/api/candles` | OHLCV candles — materialized views or dynamic `SAMPLE BY` |
| `GET` | `/api/orderbook` | Latest top-5 order book via QuestDB `LATEST ON` |
| `GET` | `/api/orderbook/spread` | Spread history aggregated via `SAMPLE BY` |
| `GET` | `/api/indicators/vwap` | VWAP — typed `VwapPoint[]` with `limit` param |
| `GET` | `/api/indicators/bollinger` | Bollinger Bands — typed `BollingerBandPoint[]` with `limit` param |
| `GET` | `/api/indicators/rsi` | RSI (14-period) — typed `RsiPoint[]` with `limit` param |
| `GET` | `/api/indicators/stats` | 24h aggregate stats |
| `GET` | `/api/indicators/symbols` | Available symbols |

All `symbol` parameters default to `BTC-USDT`. Supported: `BTC-USDT`, `ETH-USDT`, `SOL-USDT`.

### SSE Streaming Endpoints

All SSE streams are served over **HTTPS/H2**. Every stream emits **individual objects** (not arrays) using delta-polling against QuestDB.

| Path | SSE Event Name | Data Type | Poll Interval |
|------|----------------|-----------|---------------|
| `/api/stream/trades` | `trade` | Single `Trade` | 500ms |
| `/api/stream/orderbook` | `orderbook` | Single `OrderBook` | 500ms |
| `/api/stream/spread` | `spread` | Single `Spread` | 500ms |
| `/api/stream/candles` | `candle` | Single `Candle` | 500ms |
| `/api/stream/rsi` | `rsi` | Single `RsiPoint` | 3s |
| `/api/stream/bollinger` | `bollinger` | Single `BollingerBandPoint` | 3s |
| `/api/stream/vwap` | `vwap` | Single `VwapPoint` | 3s |

**Key behaviors:**

- **Immediate first poll** — `Duration.ZERO` start delay; data arrives within milliseconds of subscribing.
- **Heartbeat keepalive** — `:heartbeat` SSE comment every 15s keeps the connection alive across browsers, proxies, and load-balancers.
- **`:connected` comment** — sent immediately on subscribe to flush HTTP response headers (prevents "hanging request" appearance).
- **`concatMap`** — max 1 outstanding QuestDB query per stream, preventing R2DBC pool exhaustion.
- **Graceful shutdown** — all streams complete immediately on `@PreDestroy` via a shared `Sinks.Empty` signal.

---

## HTTP/2 + SSE

The server runs on **HTTPS/H2** (port 8443). This is critical for SSE:

| | HTTP/1.1 | HTTP/2 |
|---|----------|--------|
| **Max SSE streams per host** | 6 (browser limit) | **Unlimited** (multiplexed) |
| **TCP connections** | 1 per stream | 1 total (shared) |
| **Head-of-line blocking** | Yes | No |
| **TLS required** | No | Yes (browsers enforce) |

With HTTP/2, all 7 SSE streams (trades, orderbook, spread, candles, RSI, Bollinger, VWAP) multiplex over a **single TCP connection**. Without H2, browsers would be capped at 6 concurrent SSE connections per origin.

### curl Testing

```bash
# HTTP/2 SSE (recommended)
curl -N --http2 "https://localhost:8443/api/stream/trades?symbol=BTC-USDT"

# Verify H2 negotiation
curl -vso /dev/null --http2 "https://localhost:8443/api/trades?symbol=BTC-USDT&limit=1" 2>&1 | grep -i 'ALPN\|HTTP/2'
```

### Browser Testing

Navigate directly to `https://localhost:8443/api/stream/trades?symbol=BTC-USDT` in Firefox/Chrome. You should see the SSE text stream render inline:

```
:connected

id:1
event:trade
data:{"ts":"2026-03-28T10:15:30.289Z","symbol":"BTC-USDT","side":"sell","price":69144.9,"amount":0.0013705}

id:2
event:trade
data:{"ts":"2026-03-28T10:15:30.492Z","symbol":"BTC-USDT","side":"buy","price":69145.0,"amount":0.000144}

:heartbeat
```

---

## SSE Wire Format

Each SSE event has: `id` (sequence number), `event` (type name), `data` (single JSON object).

```
:connected

id:1
event:trade
data:{"ts":"2026-03-28T10:15:30.289Z","symbol":"BTC-USDT","side":"sell","price":69144.9,"amount":0.0013705}

id:42
event:candle
data:{"ts":"2026-03-28T10:15:30Z","symbol":"BTC-USDT","open":69100,"high":69150,"low":69090,"close":69140,"volume":1.23}

id:1
event:rsi
data:{"ts":"2026-03-28T10:15:30Z","close":69144.9,"rsi":45.23}

id:1
event:bollinger
data:{"ts":"2026-03-28T10:15:30Z","close":69144.9,"sma20":68994.38,"upperBand":69069.5,"lowerBand":68919.25}

id:1
event:vwap
data:{"ts":"2026-03-28T10:15:30Z","close":69144.9,"vwap":69034.2}

:heartbeat
```

> **Note:** All SSE streams emit **individual objects per event** (not arrays). The event name for candles is `candle` (singular).

---

## Integrating with Next.js

### Setup

Add to your Next.js project's `.env.local`:

```env
# Spring Boot API base URL
NEXT_PUBLIC_API_URL=https://localhost:8443

# Trust the mkcert CA for server-side fetch (Server Components, Route Handlers)
# Find your path with: mkcert -CAROOT
NODE_EXTRA_CA_CERTS=/home/ui/.local/share/mkcert/rootCA.pem
```

> - `NEXT_PUBLIC_` prefix exposes the variable to the browser bundle.
> - `NODE_EXTRA_CA_CERTS` is read by Node.js at startup — required for any server-side `fetch()` to `https://localhost:8443`.

### Browser-side — SSE streams (client components)

```tsx
'use client';

import { useEffect, useState, useCallback } from 'react';

// ─── Generic SSE hook ───────────────────────────────────────────────────────
function useSseStream<T>(path: string, eventName: string) {
  const [data, setData] = useState<T | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const es = new EventSource(`${process.env.NEXT_PUBLIC_API_URL}${path}`);
    es.onopen = () => setConnected(true);
    es.addEventListener(eventName, (e) => setData(JSON.parse(e.data)));
    es.onerror = () => setConnected(false);
    return () => es.close();
  }, [path, eventName]);

  return { data, connected };
}

// ─── Typed hooks ────────────────────────────────────────────────────────────

// Near real-time trades — delta-polled from QuestDB every 500ms
export function useTradeStream(symbol = 'BTC-USDT') {
  return useSseStream<Trade>(
    `/api/stream/trades?symbol=${symbol}`, 'trade');
}

// Real-time order book snapshot
export function useOrderBookStream(symbol = 'BTC-USDT') {
  return useSseStream<OrderBook>(
    `/api/stream/orderbook?symbol=${symbol}`, 'orderbook');
}

// Real-time spread
export function useSpreadStream(symbol = 'BTC-USDT') {
  return useSseStream<Spread>(
    `/api/stream/spread?symbol=${symbol}`, 'spread');
}

// ─── Accumulating hook (for chart data) ─────────────────────────────────────
function useSseAccumulator<T>(path: string, eventName: string, maxItems = 500) {
  const [items, setItems] = useState<T[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const es = new EventSource(`${process.env.NEXT_PUBLIC_API_URL}${path}`);
    es.onopen = () => setConnected(true);
    es.addEventListener(eventName, (e) => {
      const item: T = JSON.parse(e.data);
      setItems(prev => [...prev.slice(-(maxItems - 1)), item]);
    });
    es.onerror = () => setConnected(false);
    return () => { es.close(); setItems([]); };
  }, [path, eventName, maxItems]);

  return { items, connected };
}

// Candle chart — accumulates individual candles into an array
export function useCandleStream(symbol = 'BTC-USDT', interval = '5s') {
  return useSseAccumulator<Candle>(
    `/api/stream/candles?symbol=${symbol}&interval=${interval}`, 'candle');
}

// RSI chart
export function useRsiStream(symbol = 'BTC-USDT', limit = 100) {
  return useSseAccumulator<RsiPoint>(
    `/api/stream/rsi?symbol=${symbol}&limit=${limit}`, 'rsi');
}

// Bollinger Bands chart
export function useBollingerStream(symbol = 'BTC-USDT', limit = 100) {
  return useSseAccumulator<BollingerBandPoint>(
    `/api/stream/bollinger?symbol=${symbol}&limit=${limit}`, 'bollinger');
}

// VWAP chart
export function useVwapStream(symbol = 'BTC-USDT', limit = 100) {
  return useSseAccumulator<VwapPoint>(
    `/api/stream/vwap?symbol=${symbol}&limit=${limit}`, 'vwap');
}
```

### Server-side — REST fetch (Server Components / Route Handlers)

```tsx
// app/dashboard/page.tsx — Server Component (runs on Node.js, needs NODE_EXTRA_CA_CERTS)
export default async function DashboardPage() {
  const [stats, symbols] = await Promise.all([
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/indicators/stats`).then(r => r.json()),
    fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/indicators/symbols`).then(r => r.json()),
  ]);

  return (
    <div>
      <p>Total trades: {stats[0]?.total_trades}</p>
      <p>Symbols: {symbols.join(', ')}</p>
    </div>
  );
}
```

> **Browser `EventSource` / `fetch()`** uses the OS + browser trust store — `mkcert -install` already handled this.
> **Node.js `fetch()` in Server Components** uses Node's own CA bundle — that's what `NODE_EXTRA_CA_CERTS` solves.

---

## Module Structure

```
trading-api-service/src/main/java/vn/com/baothi/tradingapi/
├── TradingApiServiceApplication.java    # Entry point
├── config/
│   ├── JacksonConfig.java               # Financial-grade double serialization
│   └── WebClientConfig.java             # CORS, buffer limits, Jackson codec wiring
├── client/
│   └── QuestDbQueryClient.java          # R2DBC DatabaseClient — all SQL queries
└── controller/
    ├── TradeController.java             # GET /api/trades
    ├── CandleController.java            # GET /api/candles
    ├── OrderBookController.java         # GET /api/orderbook, /api/orderbook/spread
    ├── IndicatorController.java         # GET /api/indicators/*
    └── StreamController.java            # GET /api/stream/* (SSE — all from QuestDB R2DBC)
```

---

## Configuration (`application.yaml`)

```yaml
server:
  port: 8443
  http2:
    enabled: true          # HTTP/2 multiplexing for unlimited SSE streams
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: 1           # mkcert default alias
  shutdown: graceful       # Allows SSE streams to complete on @PreDestroy

spring:
  lifecycle:
    timeout-per-shutdown-phase: 5s   # Max wait for SSE stream cleanup

  r2dbc:
    url: r2dbc:postgresql://localhost:8812/qdb
    username: admin
    password: quest
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 5m
      max-life-time: 30m
      max-acquire-time: 5s
      max-create-connection-time: 5s
      # QuestDB PG wire doesn't support the default R2DBC validate() — use simple query
      validation-query: "SELECT 1"

# Supported symbols (for request validation; ingestion is in okx-feed-handler)
okx:
  symbols: [BTC-USDT, ETH-USDT, SOL-USDT]
```

---

## How It Works

### Architecture: Pure QuestDB Read Layer

```
┌──────────────┐     ILP/HTTP     ┌───────────┐    R2DBC/PgWire    ┌──────────────────────┐  SSE/HTTPS/H2  ┌───────────┐
│ OKX WebSocket│ ──────────────▶  │  QuestDB  │  ◀─────────────── │  trading-api-service  │ ─────────────▶ │ Dashboard │
│  (v5 public) │                  │  (port    │                    │  (port 8443, H2)      │                │ (Next.js) │
└──────────────┘                  │  9000/    │                    └──────────────────────┘                └───────────┘
       ▲                          │  8812)    │
       │                          └───────────┘
 ┌─────┴──────────┐
 │ okx-feed-handler│
 │ (single ingestor)│
 └────────────────┘
```

### SSE Streaming: Delta-Polling from QuestDB

**All streams** poll QuestDB, emit individual objects, and include heartbeat keepalive:

```java
// Trade streaming: delta-poll — only fetch rows newer than last-seen timestamp
AtomicReference<Instant> cursor = new AtomicReference<>(Instant.now().minusSeconds(5));

Flux<ServerSentEvent<Trade>> data = Flux.interval(Duration.ZERO, Duration.ofMillis(500))  // ← immediate first poll
    .concatMap(_ -> queryClient.queryTradesSince(symbol, cursor.get())
        .flatMapMany(trades -> {
            if (!trades.isEmpty()) cursor.set(trades.getLast().ts());
            return Flux.fromIterable(trades);
        })
        .map(trade -> ServerSentEvent.<Trade>builder()
            .event("trade").data(trade).build()));

return withHeartbeat(data);  // ← adds :connected + :heartbeat every 15s + graceful shutdown
```

QuestDB features used:

| Feature | Endpoint |
|---------|----------|
| `SAMPLE BY 5s` | `/api/candles`, all indicators |
| `LATEST ON ts PARTITION BY symbol` | `/api/orderbook`, `/api/stream/orderbook` |
| `CUMULATIVE` window | `/api/indicators/vwap`, `/api/stream/vwap` |
| `ROWS BETWEEN N PRECEDING` | `/api/indicators/bollinger`, `/api/stream/bollinger` |
| `LAG()` window | `/api/indicators/rsi`, `/api/stream/rsi` |
| Materialized views | `/api/candles`, `/api/stream/candles` |

---

## TLS / HTTPS Setup

The service runs over **HTTPS/H2** using an **ECDSA P-256** certificate issued by a local [mkcert](https://github.com/FiloSottile/mkcert) CA.

### Prerequisites

| Tool | Arch Linux | Ubuntu / Debian | Windows | macOS |
|------|-----------|----------------|---------|-------|
| **mkcert** | `sudo pacman -S mkcert` | `sudo apt install mkcert` | `choco install mkcert` | `brew install mkcert` |
| **NSS tools** (Linux only) | `sudo pacman -S nss` | `sudo apt install libnss3-tools` | — | — |

> NSS tools are needed on Linux so `mkcert -install` can inject the CA into Chromium and Firefox trust stores.

### Step 1 — Install the mkcert root CA (once per machine)

```bash
mkcert -install
```

This injects the CA into all trust stores (system, Chromium, Firefox).

### Step 2 — Generate the ECDSA PKCS#12 keystore

```bash
cd trading-api-service/src/main/resources
mkcert -ecdsa -pkcs12 -p12-file keystore.p12 localhost 127.0.0.1 ::1
cd ../../../..
```

### Step 3 — Verify

```bash
keytool -list -keystore trading-api-service/src/main/resources/keystore.p12 \
  -storepass changeit -storetype PKCS12 -v \
  | grep -E "Alias|Signature algorithm|Subject Public Key|chain length"
```

---

## Troubleshooting

### `WARN ApplicationProtocolNegotiationHandler: Failed to select the application-level protocol` (ECONNRESET -104)

```
WARN .s.ApplicationProtocolNegotiationHandler : Failed to select the application-level protocol:
io.netty.channel.unix.Errors$NativeIoException: recvAddress(..) failed with error(-104): Connection reset by peer
```

**Completely harmless.** Error `-104` is the Linux `ECONNRESET` code. It fires when a client TCP-connects to port `8443` (TLS) and then resets the connection before completing the TLS/ALPN handshake. Netty expected a TLS `ClientHello` frame but received either plain HTTP bytes or nothing at all.

Common triggers:

| Trigger | Why it happens |
|---------|---------------|
| **Spring DevTools restart probe** | DevTools sends `GET http://localhost:8443/` (plain HTTP) after each restart to check if the server is alive |
| **Browser address bar** | Typing `localhost:8443` defaults to `http://`; browser resets instantly when it receives a TLS record back |
| **curl with wrong scheme** | `curl http://localhost:8443/api/...` instead of `https://` |
| **OS / IDE port scanners** | IntelliJ, `nmap`, system health checks performing TCP-only probing |
| **DevTools LiveReload port confusion** | Some tooling briefly probes port 8443 expecting HTTP |

The server **silently drops the connection** — no data is exposed, no security impact. `application.yaml` suppresses this specific logger to `ERROR` so it never appears during normal operation:

```yaml
logging:
  level:
    io.netty.handler.ssl.ApplicationProtocolNegotiationHandler: ERROR
```

**To avoid seeing it at all** — always connect with `https://`:

```bash
# ✅ correct
curl -sk --http2 "https://localhost:8443/api/trades?symbol=BTC-USDT&limit=1"

# ❌ triggers WARN
curl "http://localhost:8443/api/trades"
```



1. **Trust the mkcert CA**: Run `mkcert -install` (needs NSS tools on Linux)
2. **Accept cert manually**: Navigate to `https://localhost:8443/api/trades?symbol=BTC-USDT&limit=1` first — if Firefox shows a cert warning, accept it, then retry the SSE URL
3. **Check QuestDB is running**: `curl http://localhost:9000/exec?query=SELECT+1`
4. **Check R2DBC connection**: Look for `R2dbcNonTransientResourceException` in logs — if present, the `validation-query` config may need adjustment
5. **Check feed handler is running**: SSE streams return no data if QuestDB tables are empty

### SSE connection drops after ~30s

The server sends `:heartbeat` comments every 15s. If you're behind a reverse proxy, ensure it allows long-lived connections and doesn't buffer SSE responses.

### curl shows nothing

```bash
# Use -N to disable output buffering + --http2 for H2
curl -Nk --http2 "https://localhost:8443/api/stream/trades?symbol=BTC-USDT"
```

The `-k` flag skips cert verification (for testing). The `-N` flag disables curl's output buffering which would hide SSE events.

---

## Running

```bash
# From project root
./gradlew :trading-api-service:bootRun

# Test SSE in terminal
curl -Nk --http2 "https://localhost:8443/api/stream/trades?symbol=BTC-USDT"
curl -Nk --http2 "https://localhost:8443/api/stream/candles?symbol=BTC-USDT&interval=5s"
curl -Nk --http2 "https://localhost:8443/api/stream/rsi?symbol=BTC-USDT&limit=50"
curl -Nk --http2 "https://localhost:8443/api/stream/bollinger?symbol=BTC-USDT&limit=50"
curl -Nk --http2 "https://localhost:8443/api/stream/vwap?symbol=BTC-USDT&limit=50"

# Test REST
curl -sk "https://localhost:8443/api/indicators/rsi?symbol=BTC-USDT&limit=10" | python3 -m json.tool
curl -sk "https://localhost:8443/api/indicators/vwap?symbol=BTC-USDT&limit=10" | python3 -m json.tool
```

---

## Dependencies

```groovy
implementation project(':hft-common')
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
implementation 'org.postgresql:r2dbc-postgresql'
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```
