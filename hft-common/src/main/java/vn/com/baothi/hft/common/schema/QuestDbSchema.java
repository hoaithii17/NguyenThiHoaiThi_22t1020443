package vn.com.baothi.hft.common.schema;

/**
 * QuestDB DDL statements for HFT market data pipeline.
 * <p>
 * QuestDB is NOT PostgreSQL. Key rules:
 * <ul>
 *   <li>SYMBOL type for repeated strings (tickers, sides) — indexed and fast</li>
 *   <li>TIMESTAMP(col) designates the time column — required for SAMPLE BY, LATEST ON</li>
 *   <li>PARTITION BY DAY for daily granularity queries</li>
 *   <li>WAL enables concurrent writes (required for ILP ingestion)</li>
 *   <li>DEDUP UPSERT KEYS for idempotent ingestion</li>
 *   <li>Materialized views cascade: trades → 5s → 1m → 1h</li>
 * </ul>
 */
public final class QuestDbSchema {

    private QuestDbSchema() {}

    // ==================== TABLES ====================

    public static final String CREATE_TRADES_TABLE = """
            CREATE TABLE IF NOT EXISTS trades (
                ts TIMESTAMP,
                symbol SYMBOL,
                side SYMBOL,
                price DOUBLE,
                amount DOUBLE
            ) TIMESTAMP(ts) PARTITION BY DAY WAL
            DEDUP UPSERT KEYS(ts, symbol);
            """;

    public static final String CREATE_ORDERBOOK_TABLE = """
            CREATE TABLE IF NOT EXISTS orderbook (
                ts TIMESTAMP,
                symbol SYMBOL,
                bid1_price DOUBLE, bid1_size DOUBLE,
                bid2_price DOUBLE, bid2_size DOUBLE,
                bid3_price DOUBLE, bid3_size DOUBLE,
                bid4_price DOUBLE, bid4_size DOUBLE,
                bid5_price DOUBLE, bid5_size DOUBLE,
                ask1_price DOUBLE, ask1_size DOUBLE,
                ask2_price DOUBLE, ask2_size DOUBLE,
                ask3_price DOUBLE, ask3_size DOUBLE,
                ask4_price DOUBLE, ask4_size DOUBLE,
                ask5_price DOUBLE, ask5_size DOUBLE,
                mid_price DOUBLE,
                spread DOUBLE
            ) TIMESTAMP(ts) PARTITION BY DAY WAL
            DEDUP UPSERT KEYS(ts, symbol);
            """;

    // ==================== MATERIALIZED VIEWS ====================
    // Cascading: trades → candles_5s → candles_1m → candles_1h

    public static final String CREATE_CANDLES_5S = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS candles_5s AS (
                SELECT ts, symbol,
                    first(price) AS open, max(price) AS high,
                    min(price) AS low, last(price) AS close,
                    sum(amount) AS volume
                FROM trades SAMPLE BY 5s
            ) PARTITION BY DAY;
            """;

    public static final String CREATE_CANDLES_1M = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1m AS (
                SELECT ts, symbol,
                    first(open) AS open, max(high) AS high,
                    min(low) AS low, last(close) AS close,
                    sum(volume) AS volume
                FROM candles_5s SAMPLE BY 1m
            ) PARTITION BY DAY;
            """;

    public static final String CREATE_CANDLES_1H = """
            CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1h AS (
                SELECT ts, symbol,
                    first(open) AS open, max(high) AS high,
                    min(low) AS low, last(close) AS close,
                    sum(volume) AS volume
                FROM candles_1m SAMPLE BY 1h
            ) PARTITION BY MONTH;
            """;

    /** All DDL statements in order. Execute sequentially. */
    public static final String[] ALL_DDL = {
            CREATE_TRADES_TABLE,
            CREATE_ORDERBOOK_TABLE,
            CREATE_CANDLES_5S,
            CREATE_CANDLES_1M,
            CREATE_CANDLES_1H
    };
}

