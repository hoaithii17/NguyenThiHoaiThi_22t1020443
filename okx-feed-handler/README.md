# okx-feed-handler

Spring Boot service that connects to the **OKX v5 public WebSocket**, ingests real-time market data, and writes it to **QuestDB** using the official `questdb-client` ILP Sender.

- **Port**: `8081`
- **Role**: Persistence — every tick written durably to QuestDB
- **Protocol to QuestDB**: ILP-over-HTTP (port `9000`)

---

## Responsibilities

1. **Schema initialization** — creates `trades`, `orderbook` tables and `candles_5s/1m/1h` materialized views on startup
2. **OKX WebSocket** — subscribes to `trades` + `books5` channels for configured symbols
3. **Parsing** — converts raw OKX JSON messages to typed DTOs
4. **Ingestion** — writes `Trade` and `OrderBook` rows to QuestDB via ILP Sender

---

## Module Structure

```
okx-feed-handler/src/main/java/vn/com/baothi/okxfeed/
├── OkxFeedHandlerApplication.java   # Entry point
├── config/
│   └── QuestDbSenderConfig.java     # @Bean Sender (ILP/HTTP, auto-flush, retry)
├── schema/
│   └── SchemaInitializer.java       # @PostConstruct — runs all DDL on startup
├── ws/
│   └── OkxWebSocketHandler.java     # Reactive WS client — subscribe, reconnect, metrics
└── ingestion/
    ├── TradeIngestor.java            # Trade → sender.table("trades").symbol().double().at()
    └── OrderBookIngestor.java        # OrderBook → sender.table("orderbook")...
```

Shared from `hft-common`:
- `OkxFeedProperties` — `okx.*` config
- `QuestDbProperties` — `questdb.*` config
- `TradeMessageParser` / `OrderBookMessageParser` — OKX JSON → DTOs
- `QuestDbSchema` — DDL constants

---

## Configuration (`application.yaml`)

```yaml
server:
  port: 8081

questdb:
  http-host: localhost
  http-port: 9000
  auto-flush-rows: 5000          # flush every 5000 rows
  auto-flush-interval-ms: 1000  # or every 1 second
  retry-timeout-ms: 10000

okx:
  ws-url: wss://ws.okx.com:8443/ws/v5/public
  symbols:
    - BTC-USDT
    - ETH-USDT
    - SOL-USDT
  channels:
    - trades
    - books5
  reconnect-delay-sec: 5
```

---

## How It Works

### 1. Schema Initialization

`SchemaInitializer` runs `QuestDbSchema.ALL_DDL` statements against QuestDB REST (`GET /exec?query=...`) at startup using `IF NOT EXISTS` guards — safe to restart.

### 2. QuestDB Sender

```java
// From QuestDbSenderConfig — one shared Sender bean, Spring-managed lifecycle
Sender.fromConfig("http::addr=localhost:9000;auto_flush_rows=5000;auto_flush_interval=1000;retry_timeout=10000;")
```

The `questdb-client` ILP Sender provides:
- **Automatic table creation** — no DDL needed for writes
- **Automatic batching** — rows buffered until flush threshold
- **Retry on transient failure** — network blips handled transparently
- **Thread-safe** — one Sender shared across ingestors

### 3. OKX WebSocket

`OkxWebSocketHandler` uses **Reactor Netty WebSocket client** with:
- **Exponential backoff reconnect** — `Retry.backoff(Long.MAX_VALUE, 5s).maxBackoff(60s)`
- **Channel routing** — `trades` → `TradeIngestor`, `books5` → `OrderBookIngestor`
- **Non-blocking metrics** — `Flux.interval(10s)` logs throughput every 10 seconds

Subscribe message sent on connect:
```json
{
  "op": "subscribe",
  "args": [
    {"channel": "trades",  "instId": "BTC-USDT"},
    {"channel": "books5",  "instId": "BTC-USDT"},
    {"channel": "trades",  "instId": "ETH-USDT"},
    ...
  ]
}
```

### 4. Ingestion

**Trade**:
```java
sender.table("trades")
    .symbol("symbol", trade.symbol())
    .symbol("side", trade.side())
    .doubleColumn("price", trade.price())
    .doubleColumn("amount", trade.amount())
    .at(trade.ts());   // uses original OKX event timestamp
```

**OrderBook** (top-5 bid/ask levels):
```java
sender.table("orderbook")
    .symbol("symbol", book.symbol())
    .doubleColumn("bid1_price", book.bid1Price())
    .doubleColumn("bid1_size",  book.bid1Size())
    // ... levels 2-5 ...
    .doubleColumn("mid_price", book.midPrice())
    .doubleColumn("spread",    book.spread())
    .at(book.ts());
```

---

## Running

```bash
# From project root
./gradlew :okx-feed-handler:bootRun

# Or run the fat jar
./gradlew :okx-feed-handler:bootJar
java -jar okx-feed-handler/build/libs/okx-feed-handler-0.0.1-SNAPSHOT.jar
```

Expected startup log:
```
▶ Creating QuestDB Sender: http::addr=localhost:9000;...
▶ Initializing QuestDB schema...
  DDL executed: CREATE TABLE IF NOT EXISTS trades ... → OK
  DDL executed: CREATE MATERIALIZED VIEW IF NOT EXISTS candles_5s ... → OK
✅ QuestDB schema initialization complete
▶ Connecting to OKX WebSocket: wss://ws.okx.com:8443/ws/v5/public
  ✅ Subscribed: {"channel":"trades","instId":"BTC-USDT"}
📊 Last 10s: 142 trades, 87 orderbook updates ingested
```

---

## Dependencies

```groovy
implementation project(':hft-common')
implementation 'org.springframework.boot:spring-boot-starter-webflux'
implementation 'org.questdb:questdb-client:1.0.1'
```

