# hft-common

Shared library module consumed by all services in the `okx-hft` project.  
Not a Spring Boot application — compiled as a plain `java-library` and added as a dependency.

---

## Contents

```
hft-common/src/main/java/vn/com/baothi/hft/common/
├── config/
│   ├── QuestDbProperties.java      # questdb.* config properties (ILP host/port, flush tuning)
│   └── OkxFeedProperties.java      # okx.* config properties (WS URL, symbols, channels)
├── dto/
│   ├── Trade.java                  # Single trade execution record
│   ├── Candle.java                 # OHLCV candlestick bar
│   ├── OrderBook.java              # Top-5 bid/ask levels snapshot
│   ├── Spread.java                 # Bid-ask spread snapshot
│   ├── RsiPoint.java               # RSI (14-period) data point
│   ├── BollingerBandPoint.java     # Bollinger Band (20-period SMA, 2σ) data point
│   └── VwapPoint.java              # VWAP (cumulative) data point
├── parser/
│   ├── TradeMessageParser.java     # OKX WS "trades" channel → List<Trade>
│   └── OrderBookMessageParser.java # OKX WS "books5" channel → List<OrderBook>
└── schema/
    └── QuestDbSchema.java          # DDL constants for all tables + materialized views
```

---

## DTOs

### `Trade`
```java
record Trade(Instant ts, String symbol, String side, double price, double amount)
```
Mapped from OKX `trades` channel. Fields: `px` → `price`, `sz` → `amount`, `ts` (epoch ms) → `Instant`.

### `Candle`
```java
record Candle(Instant ts, String symbol, double open, double high, double low, double close, double volume)
```
Populated from QuestDB `candles_5s / candles_1m / candles_1h` materialized views.

### `OrderBook`
```java
record OrderBook(Instant ts, String symbol,
    double bid1Price, double bid1Size, ... double bid5Price, double bid5Size,
    double ask1Price, double ask1Size, ... double ask5Price, double ask5Size,
    double midPrice, double spread)
```
Mapped from OKX `books5` channel. `midPrice = (bid1 + ask1) / 2`, `spread = ask1 - bid1`.

### `Spread`
```java
record Spread(Instant ts, String symbol, double bestBid, double bestAsk, double spread, double midPrice)
```
Derived from `OrderBook` — the live bid/ask at level 1.

### `RsiPoint`
```java
record RsiPoint(Instant ts, double close, double rsi)
```
14-period RSI computed from 5-second candles using QuestDB `LAG()` + rolling window functions.

### `BollingerBandPoint`
```java
record BollingerBandPoint(Instant ts, double close, double sma20, double upperBand, double lowerBand)
```
Bollinger Bands (20-period SMA ± 2σ) computed from 5-second candles using QuestDB rolling window functions.

### `VwapPoint`
```java
record VwapPoint(Instant ts, double close, double vwap)
```
Cumulative Volume Weighted Average Price computed from 5-second candles using QuestDB `CUMULATIVE` window function.

---

## Config Properties

### `QuestDbProperties` (`questdb.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `questdb.http-host` | `localhost` | QuestDB HTTP host |
| `questdb.http-port` | `9000` | QuestDB ILP-over-HTTP port |
| `questdb.rest-base-url` | `http://localhost:9000` | Base URL for REST API calls |
| `questdb.auto-flush-rows` | `75000` | ILP Sender flush threshold (rows) |
| `questdb.auto-flush-interval-ms` | `1000` | ILP Sender flush interval (ms) |
| `questdb.retry-timeout-ms` | `10000` | ILP Sender retry timeout (ms) |

Produces a config string via `toSenderConfigString()`:
```
http::addr=localhost:9000;auto_flush_rows=75000;auto_flush_interval=1000;retry_timeout=10000;
```

### `OkxFeedProperties` (`okx.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `okx.ws-url` | `wss://ws.okx.com:8443/ws/v5/public` | OKX v5 public WebSocket URL |
| `okx.symbols` | `[BTC-USDT, ETH-USDT, SOL-USDT]` | Instrument IDs to subscribe |
| `okx.channels` | `[trades, books5]` | OKX channels to subscribe |
| `okx.reconnect-delay-sec` | `5` | Base delay for exponential reconnect backoff |

---

## QuestDB Schema

All DDL is defined as string constants in `QuestDbSchema.ALL_DDL` and executed by `okx-feed-handler` on startup.

```sql
-- trades: raw tick data, deduplicated on (ts, symbol)
CREATE TABLE IF NOT EXISTS trades (
    ts TIMESTAMP, symbol SYMBOL, side SYMBOL, price DOUBLE, amount DOUBLE
) TIMESTAMP(ts) PARTITION BY DAY WAL
DEDUP UPSERT KEYS(ts, symbol);

-- orderbook: top-5 levels, deduplicated on (ts, symbol)
CREATE TABLE IF NOT EXISTS orderbook (
    ts TIMESTAMP, symbol SYMBOL,
    bid1_price DOUBLE, bid1_size DOUBLE,
    ...
    ask5_price DOUBLE, ask5_size DOUBLE,
    mid_price DOUBLE, spread DOUBLE
) TIMESTAMP(ts) PARTITION BY DAY WAL
DEDUP UPSERT KEYS(ts, symbol);

-- Cascading materialized views — auto-updated by QuestDB on every write
CREATE MATERIALIZED VIEW IF NOT EXISTS candles_5s  AS (SELECT ts, symbol, first(price) AS open, ... FROM trades     SAMPLE BY 5s) PARTITION BY DAY;
CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1m  AS (SELECT ts, symbol, first(open)  AS open, ... FROM candles_5s SAMPLE BY 1m) PARTITION BY DAY;
CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1h  AS (SELECT ts, symbol, first(open)  AS open, ... FROM candles_1m SAMPLE BY 1h) PARTITION BY MONTH;
```

---

## Usage

Enable the shared config properties in any consuming service:

```java
@SpringBootApplication
@EnableConfigurationProperties({QuestDbProperties.class, OkxFeedProperties.class})
public class MyApplication { ... }
```

Add to `build.gradle`:
```groovy
dependencies {
    implementation project(':hft-common')
}
```

