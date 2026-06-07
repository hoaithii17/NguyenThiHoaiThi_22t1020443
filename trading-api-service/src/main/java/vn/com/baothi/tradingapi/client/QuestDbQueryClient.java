package vn.com.baothi.tradingapi.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import vn.com.baothi.hft.common.dto.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reactive client for QuestDB using R2DBC (PostgreSQL wire protocol, port 8812).
 * <p>
 * Replaces the previous HTTP REST approach, eliminating URL-encoding issues
 * with complex CTE queries and providing a true reactive database connection.
 */
@Slf4j
@Component
public class QuestDbQueryClient {

    private final DatabaseClient db;

    public QuestDbQueryClient(DatabaseClient databaseClient) {
        this.db = databaseClient;
    }

    // ─── Typed queries ──────────────────────────────────────────────────────────

    /**
     * Query recent trades.
     */
    public Mono<List<Trade>> queryTrades(String symbol, int limit) {
        String sql = """
                SELECT ts, symbol, side, price, amount
                FROM trades
                WHERE ts > dateadd('h', -1, now())
                AND symbol = '%s'
                ORDER BY ts DESC
                LIMIT %d
                """.formatted(symbol, limit);

        return db.sql(sql)
                .map((row, meta) -> new Trade(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        row.get("side", String.class),
                        toDouble(row.get("price")),
                        toDouble(row.get("amount"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryTrades error: {}", e.getMessage()));
    }

    /**
     * Query the most recent trades since a given timestamp (for streaming delta).
     * Returns trades newer than {@code sinceTs}, ordered oldest-first so clients
     * receive them chronologically.  Returns at most 200 rows per poll.
     */
    public Mono<List<Trade>> queryTradesSince(String symbol, Instant sinceTs) {
        String sql = """
                SELECT ts, symbol, side, price, amount
                FROM trades
                WHERE ts > '%s'
                AND symbol = '%s'
                ORDER BY ts ASC
                LIMIT 200
                """.formatted(sinceTs, symbol);

        return db.sql(sql)
                .map((row, meta) -> new Trade(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        row.get("side", String.class),
                        toDouble(row.get("price")),
                        toDouble(row.get("amount"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryTradesSince error: {}", e.getMessage()));
    }

    /**
     * Query the latest order book snapshot (using QuestDB LATEST ON)
     * and derive spread.
     */
    public Mono<Spread> queryLatestSpread(String symbol) {
        String sql = """
                SELECT ts, symbol, bid1_price, ask1_price, spread, mid_price
                FROM orderbook
                WHERE ts > dateadd('m', -5, now())
                AND symbol = '%s'
                LATEST ON ts PARTITION BY symbol
                """.formatted(symbol);

        return db.sql(sql)
                .map((row, meta) -> new Spread(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        toDouble(row.get("bid1_price")),
                        toDouble(row.get("ask1_price")),
                        toDouble(row.get("spread")),
                        toDouble(row.get("mid_price"))
                ))
                .first()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryLatestSpread error: {}", e.getMessage()));
    }

    /**
     * Query OHLCV candles using QuestDB SAMPLE BY.
     * Uses materialized views for known intervals, dynamic SAMPLE BY for custom.
     */
    public Mono<List<Candle>> queryCandles(String symbol, String interval) {
        String table = switch (interval) {
            case "5s" -> "candles_5s";
            case "1m" -> "candles_1m";
            case "1h" -> "candles_1h";
            default -> null;
        };

        String sql;
        if (table != null) {
            sql = """
                    SELECT ts, symbol, open, high, low, close, volume
                    FROM %s
                    WHERE ts > dateadd('h', -24, now())
                    AND symbol = '%s'
                    ORDER BY ts DESC
                    LIMIT 500
                    """.formatted(table, symbol);
        } else {
            sql = """
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -24, now())
                    AND symbol = '%s'
                    SAMPLE BY %s
                    ORDER BY ts DESC
                    LIMIT 500
                    """.formatted(symbol, interval);
        }

        return db.sql(sql)
                .map((row, meta) -> new Candle(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        toDouble(row.get("open")),
                        toDouble(row.get("high")),
                        toDouble(row.get("low")),
                        toDouble(row.get("close")),
                        toDouble(row.get("volume"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryCandles error: {}", e.getMessage()));
    }

    /**
     * Query latest order book snapshot using QuestDB LATEST ON.
     * LATEST ON guarantees at most one row per partition, so returns {@link Mono} — not a list.
     */
    public Mono<OrderBook> queryLatestOrderBook(String symbol) {
        String sql = """
                SELECT * FROM orderbook
                WHERE ts > dateadd('m', -5, now())
                AND symbol = '%s'
                LATEST ON ts PARTITION BY symbol
                """.formatted(symbol);

        return db.sql(sql)
                .map((row, meta) -> new OrderBook(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        toDouble(row.get("bid1_price")), toDouble(row.get("bid1_size")),
                        toDouble(row.get("bid2_price")), toDouble(row.get("bid2_size")),
                        toDouble(row.get("bid3_price")), toDouble(row.get("bid3_size")),
                        toDouble(row.get("bid4_price")), toDouble(row.get("bid4_size")),
                        toDouble(row.get("bid5_price")), toDouble(row.get("bid5_size")),
                        toDouble(row.get("ask1_price")), toDouble(row.get("ask1_size")),
                        toDouble(row.get("ask2_price")), toDouble(row.get("ask2_size")),
                        toDouble(row.get("ask3_price")), toDouble(row.get("ask3_size")),
                        toDouble(row.get("ask4_price")), toDouble(row.get("ask4_size")),
                        toDouble(row.get("ask5_price")), toDouble(row.get("ask5_size")),
                        toDouble(row.get("mid_price")),
                        toDouble(row.get("spread"))
                ))
                .first()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryLatestOrderBook error: {}", e.getMessage()));
    }

    /**
     * Query spread history using QuestDB SAMPLE BY.
     */
    public Mono<List<Spread>> querySpreadHistory(String symbol, String interval) {
        String sql = """
                SELECT ts,
                    symbol,
                    avg(bid1_price) AS best_bid,
                    avg(ask1_price) AS best_ask,
                    avg(spread) AS spread,
                    avg(mid_price) AS mid_price
                FROM orderbook
                WHERE ts > dateadd('h', -1, now())
                AND symbol = '%s'
                SAMPLE BY %s
                ORDER BY ts DESC
                LIMIT 500
                """.formatted(symbol, interval);

        return db.sql(sql)
                .map((row, meta) -> new Spread(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        toDouble(row.get("best_bid")),
                        toDouble(row.get("best_ask")),
                        toDouble(row.get("spread")),
                        toDouble(row.get("mid_price"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("querySpreadHistory error: {}", e.getMessage()));
    }

    /**
     * Query available symbols.
     */
    public Mono<List<String>> querySymbols() {
        return db.sql("SELECT DISTINCT symbol FROM trades ORDER BY symbol")
                .map((row, meta) -> row.get("symbol", String.class))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("querySymbols error: {}", e.getMessage()));
    }

    // ─── Typed indicator queries ─────────────────────────────────────────────────

    /**
     * Query RSI (14-period) as typed DTOs.
     *
     * @param symbol instrument ID
     * @param limit  max rows returned (use ≤100 for streaming)
     */
    public Mono<List<RsiPoint>> queryRsiTyped(String symbol, int limit) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                changes AS (
                    SELECT ts, close,
                        close - LAG(close) OVER (ORDER BY ts) AS change
                    FROM ohlc
                ),
                gains_losses AS (
                    SELECT ts, close,
                        CASE WHEN change > 0 THEN change ELSE 0 END AS gain,
                        CASE WHEN change < 0 THEN ABS(change) ELSE 0 END AS loss
                    FROM changes
                ),
                avg_gl AS (
                    SELECT ts, close,
                        AVG(gain) OVER (ORDER BY ts
                            ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_gain,
                        AVG(loss) OVER (ORDER BY ts
                            ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_loss
                    FROM gains_losses
                )
                SELECT ts, close,
                    CASE WHEN avg_loss = 0 THEN 100
                         ELSE 100 - (100 / (1 + avg_gain / NULLIF(avg_loss, 0)))
                    END AS rsi
                FROM avg_gl
                ORDER BY ts DESC
                LIMIT %d
                """.formatted(symbol, limit);

        return db.sql(sql)
                .map((row, meta) -> new RsiPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("rsi"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryRsiTyped error: {}", e.getMessage()));
    }

    /**
     * Query Bollinger Bands (20-period SMA, 2σ) as typed DTOs.
     *
     * @param symbol instrument ID
     * @param limit  max rows returned (use ≤100 for streaming)
     */
    public Mono<List<BollingerBandPoint>> queryBollingerTyped(String symbol, int limit) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                stats AS (
                    SELECT ts, close,
                        AVG(close) OVER (
                            ORDER BY ts ROWS BETWEEN 19 PRECEDING AND CURRENT ROW
                        ) AS sma20,
                        AVG(close * close) OVER (
                            ORDER BY ts ROWS BETWEEN 19 PRECEDING AND CURRENT ROW
                        ) AS avg_close_sq
                    FROM ohlc
                )
                SELECT ts, close, sma20,
                    sma20 + 2 * sqrt(avg_close_sq - (sma20 * sma20)) AS upper_band,
                    sma20 - 2 * sqrt(avg_close_sq - (sma20 * sma20)) AS lower_band
                FROM stats
                ORDER BY ts DESC
                LIMIT %d
                """.formatted(symbol, limit);

        return db.sql(sql)
                .map((row, meta) -> new BollingerBandPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("sma20")),
                        toDouble(row.get("upper_band")),
                        toDouble(row.get("lower_band"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryBollingerTyped error: {}", e.getMessage()));
    }

    /**
     * Query VWAP (cumulative) as typed DTOs.
     *
     * @param symbol instrument ID
     * @param limit  max rows returned (use ≤100 for streaming)
     */
    public Mono<List<VwapPoint>> queryVwapTyped(String symbol, int limit) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                vwap AS (
                    SELECT ts, close,
                        sum((high + low + close) / 3 * volume) OVER (ORDER BY ts CUMULATIVE)
                        / sum(volume) OVER (ORDER BY ts CUMULATIVE) AS vwap
                    FROM ohlc
                )
                SELECT ts, close, vwap FROM vwap
                ORDER BY ts DESC
                LIMIT %d
                """.formatted(symbol, limit);

        return db.sql(sql)
                .map((row, meta) -> new VwapPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("vwap"))
                ))
                .all()
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryVwapTyped error: {}", e.getMessage()));
    }

    // ─── Delta-streaming queries (Flux, cursor-based) ────────────────────────────

    /**
     * Query OHLCV candles newer than {@code since} — for SSE delta streaming.
     * Returns rows ASC (oldest first) so the client appends chronologically.
     * Only new candles since the last-seen timestamp are emitted; already-seen
     * history is never re-sent.
     */
    public Flux<Candle> queryCandlesSince(String symbol, String interval, Instant since) {
        String table = switch (interval) {
            case "5s" -> "candles_5s";
            case "1m" -> "candles_1m";
            case "1h" -> "candles_1h";
            default -> null;
        };

        String sql;
        if (table != null) {
            sql = """
                    SELECT ts, symbol, open, high, low, close, volume
                    FROM %s
                    WHERE ts > '%s'
                    AND symbol = '%s'
                    ORDER BY ts ASC
                    LIMIT 500
                    """.formatted(table, since, symbol);
        } else {
            sql = """
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > '%s'
                    AND symbol = '%s'
                    SAMPLE BY %s
                    ORDER BY ts ASC
                    LIMIT 500
                    """.formatted(since, symbol, interval);
        }

        return db.sql(sql)
                .map((row, meta) -> new Candle(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        row.get("symbol", String.class),
                        toDouble(row.get("open")),
                        toDouble(row.get("high")),
                        toDouble(row.get("low")),
                        toDouble(row.get("close")),
                        toDouble(row.get("volume"))
                ))
                .all()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryCandlesSince error: {}", e.getMessage()));
    }

    /**
     * Query RSI (14-period, 5s candles) newer than {@code since} — for SSE delta streaming.
     */
    public Flux<RsiPoint> queryRsiSince(String symbol, Instant since) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                changes AS (
                    SELECT ts, close,
                        close - LAG(close) OVER (ORDER BY ts) AS change
                    FROM ohlc
                ),
                gains_losses AS (
                    SELECT ts, close,
                        CASE WHEN change > 0 THEN change ELSE 0 END AS gain,
                        CASE WHEN change < 0 THEN ABS(change) ELSE 0 END AS loss
                    FROM changes
                ),
                avg_gl AS (
                    SELECT ts, close,
                        AVG(gain) OVER (ORDER BY ts
                            ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_gain,
                        AVG(loss) OVER (ORDER BY ts
                            ROWS BETWEEN 13 PRECEDING AND CURRENT ROW) AS avg_loss
                    FROM gains_losses
                )
                SELECT ts, close,
                    CASE WHEN avg_loss = 0 THEN 100
                         ELSE 100 - (100 / (1 + avg_gain / NULLIF(avg_loss, 0)))
                    END AS rsi
                FROM avg_gl
                WHERE ts > '%s'
                ORDER BY ts ASC
                LIMIT 500
                """.formatted(symbol, since);

        return db.sql(sql)
                .map((row, meta) -> new RsiPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("rsi"))
                ))
                .all()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryRsiSince error: {}", e.getMessage()));
    }

    /**
     * Query Bollinger Bands (20-period SMA, 2σ) newer than {@code since} — for SSE delta streaming.
     */
    public Flux<BollingerBandPoint> queryBollingerSince(String symbol, Instant since) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                stats AS (
                    SELECT ts, close,
                        AVG(close) OVER (
                            ORDER BY ts ROWS BETWEEN 19 PRECEDING AND CURRENT ROW
                        ) AS sma20,
                        AVG(close * close) OVER (
                            ORDER BY ts ROWS BETWEEN 19 PRECEDING AND CURRENT ROW
                        ) AS avg_close_sq
                    FROM ohlc
                )
                SELECT ts, close, sma20,
                    sma20 + 2 * sqrt(avg_close_sq - (sma20 * sma20)) AS upper_band,
                    sma20 - 2 * sqrt(avg_close_sq - (sma20 * sma20)) AS lower_band
                FROM stats
                WHERE ts > '%s'
                ORDER BY ts ASC
                LIMIT 500
                """.formatted(symbol, since);

        return db.sql(sql)
                .map((row, meta) -> new BollingerBandPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("sma20")),
                        toDouble(row.get("upper_band")),
                        toDouble(row.get("lower_band"))
                ))
                .all()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryBollingerSince error: {}", e.getMessage()));
    }

    /**
     * Query VWAP (cumulative) newer than {@code since} — for SSE delta streaming.
     */
    public Flux<VwapPoint> queryVwapSince(String symbol, Instant since) {
        String sql = """
                WITH ohlc AS (
                    SELECT ts, symbol,
                        first(price) AS open, max(price) AS high,
                        min(price) AS low, last(price) AS close,
                        sum(amount) AS volume
                    FROM trades
                    WHERE ts > dateadd('h', -1, now())
                    AND symbol = '%s'
                    SAMPLE BY 5s
                ),
                vwap AS (
                    SELECT ts, close,
                        sum((high + low + close) / 3 * volume) OVER (ORDER BY ts CUMULATIVE)
                        / sum(volume) OVER (ORDER BY ts CUMULATIVE) AS vwap
                    FROM ohlc
                )
                SELECT ts, close, vwap FROM vwap
                WHERE ts > '%s'
                ORDER BY ts ASC
                LIMIT 500
                """.formatted(symbol, since);

        return db.sql(sql)
                .map((row, meta) -> new VwapPoint(
                        toInstant(row.get("ts", LocalDateTime.class)),
                        toDouble(row.get("close")),
                        toDouble(row.get("vwap"))
                ))
                .all()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("queryVwapSince error: {}", e.getMessage()));
    }

    // ─── Stats / generic queries ──────────────────────────────────────────────

    /**
     * Query trade count and volume stats — demonstrates QuestDB aggregation speed.
     */
    public Mono<List<Map<String, Object>>> queryStats() {
        String sql = """
                SELECT
                    count() AS total_trades,
                    count_distinct(symbol) AS symbols,
                    sum(amount * price) AS total_volume_usd,
                    min(ts) AS first_trade,
                    max(ts) AS last_trade
                FROM trades
                WHERE ts > dateadd('h', -24, now())
                """;
        return executeGenericQuery(sql);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Execute a SQL query and return rows as normalized maps.
     * Converts LocalDateTime → ISO Instant strings for JSON serialization.
     */
    private Mono<List<Map<String, Object>>> executeGenericQuery(String sql) {
        return db.sql(sql)
                .fetch()
                .all()
                .map(this::normalizeRow)
                .collectList()
                .retryWhen(retryOnConnectionFailure())
                .doOnError(e -> log.error("QuestDB query error: {} for SQL: {}",
                        e.getMessage(), sql.strip()));
    }

    /**
     * Shared retry spec for transient R2DBC connection failures.
     * Retries up to 3 times with 500ms exponential backoff.
     */
    private Retry retryOnConnectionFailure() {
        return Retry.backoff(3, Duration.ofMillis(500))
                .filter(e -> e instanceof DataAccessResourceFailureException)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying QuestDB query (attempt {}): {}",
                        signal.totalRetries() + 1, signal.failure().getMessage()));
    }

    /**
     * Normalize a row map: convert LocalDateTime timestamps to ISO strings.
     */
    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        var normalized = new LinkedHashMap<String, Object>();
        row.forEach((key, value) -> {
            if (value instanceof LocalDateTime ldt) {
                normalized.put(key, ldt.toInstant(ZoneOffset.UTC).toString());
            } else {
                normalized.put(key, value);
            }
        });
        return normalized;
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : Instant.now();
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}

