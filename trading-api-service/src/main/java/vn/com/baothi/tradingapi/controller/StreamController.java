package vn.com.baothi.tradingapi.controller;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import vn.com.baothi.hft.common.config.OkxFeedProperties;
import vn.com.baothi.hft.common.dto.*;
import vn.com.baothi.tradingapi.client.QuestDbQueryClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Server-Sent Events (SSE) streaming controller — <b>pure QuestDB R2DBC read layer</b>.
 * <p>
 * Served over <b>HTTPS/H2</b> (port 8443). HTTP/2 multiplexes all SSE streams
 * over a single TCP connection, eliminating the browser's 6-connection-per-host
 * limit that plagued HTTP/1.1 SSE. Each stream runs as an independent H2 stream.
 * <p>
 * All streams use:
 * <ul>
 *   <li><b>Immediate first poll</b> ({@code Duration.ZERO} start delay) — data arrives
 *       within milliseconds of subscribing, not after the first interval.</li>
 *   <li><b>Heartbeat keepalive</b> — a {@code :heartbeat} SSE comment every 15s
 *       prevents browsers, proxies, and load-balancers from closing idle connections.</li>
 *   <li><b>{@code concatMap}</b> — max 1 outstanding query at a time per stream,
 *       preventing R2DBC connection pool exhaustion.</li>
 *   <li><b>Graceful shutdown</b> — all streams complete immediately on
 *       {@code @PreDestroy} via a shared shutdown signal.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    /** Fast poll for tick-level data (trades, orderbook, spread) */
    private static final Duration TICK_POLL_INTERVAL = Duration.ofMillis(500);

    /** Indicator poll interval — aligned with 5s candle granularity */
    private static final Duration INDICATOR_POLL_INTERVAL = Duration.ofSeconds(3);

    /**
     * Shutdown signal — completes on @PreDestroy, which causes all SSE streams
     * to terminate immediately via takeUntilOther(). Without this, the server
     * hangs waiting for infinite SSE connections to close during graceful shutdown.
     */
    private final Sinks.Empty<Void> shutdownSignal = Sinks.empty();

    private final QuestDbQueryClient queryClient;
    private final OkxFeedProperties feedProperties;

    /**
     * Backpressure-safe periodic trigger for polling streams.
     * <p>
     * Uses repeatWhen (fixed-delay) instead of Flux.interval so ticks are only
     * produced when downstream has demand, avoiding OverflowException on slow clients.
     * </p>
     */
    private Flux<Long> pollingTicks(Duration interval) {
        var tick = new AtomicLong(0);
        return Flux.defer(() -> Mono.fromSupplier(tick::getAndIncrement))
                .repeatWhen(repeat -> repeat.delayElements(interval));
    }

    @PreDestroy
    void shutdown() {
        log.info("⏹ [SSE] Completing all SSE streams for graceful shutdown");
        shutdownSignal.tryEmitEmpty();
    }

    // ─── Tick-level streams (delta-polled from QuestDB) ─────────────────────────

    /**
     * GET /api/stream/trades?symbol=BTC-USDT
     * <p>
     * Near real-time: delta-polls QuestDB every 500ms for new trades since
     * the last-seen timestamp, emitting each as an individual SSE event.
     */
    @GetMapping(value = "/trades", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Trade>> streamTrades(
            @RequestParam(defaultValue = "BTC-USDT") String symbol) {
        validateSymbol(symbol);

        AtomicLong seq = new AtomicLong(0);
        AtomicReference<Instant> cursor = new AtomicReference<>(Instant.now().minusSeconds(5));

        Flux<ServerSentEvent<Trade>> data = pollingTicks(TICK_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryTradesSince(symbol, cursor.get())
                        .flatMapMany(trades -> {
                            if (!trades.isEmpty()) {
                                cursor.set(trades.getLast().ts());
                            }
                            return Flux.fromIterable(trades);
                        })
                        .map(trade -> ServerSentEvent.<Trade>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("trade")
                                .data(trade)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("Trade stream poll error: {}", e.getMessage());
                            return Flux.empty();
                        }));

        return withHeartbeat(data);
    }

    /**
     * GET /api/stream/orderbook?symbol=BTC-USDT
     * <p>
     * Polls the latest order book snapshot from QuestDB every 500ms.
     * Uses QuestDB LATEST ON which guarantees a single row — emitted as one SSE event.
     */
    @GetMapping(value = "/orderbook", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<OrderBook>> streamOrderBook(
            @RequestParam(defaultValue = "BTC-USDT") String symbol) {
        validateSymbol(symbol);
        AtomicLong seq = new AtomicLong(0);
        Flux<ServerSentEvent<OrderBook>> data = pollingTicks(TICK_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryLatestOrderBook(symbol)
                        .map(book -> ServerSentEvent.<OrderBook>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("orderbook")
                                .data(book)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("OrderBook stream poll error: {}", e.getMessage());
                            return Mono.empty();
                        }));
        return withHeartbeat(data);
    }

    /**
     * GET /api/stream/spread?symbol=BTC-USDT
     * <p>
     * Polls the latest spread from QuestDB (derived from the latest order book)
     * every 500ms.
     */
    @GetMapping(value = "/spread", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Spread>> streamSpread(
            @RequestParam(defaultValue = "BTC-USDT") String symbol) {
        validateSymbol(symbol);
        AtomicLong seq = new AtomicLong(0);
        Flux<ServerSentEvent<Spread>> data = pollingTicks(TICK_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryLatestSpread(symbol)
                        .map(spread -> ServerSentEvent.<Spread>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("spread")
                                .data(spread)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("Spread stream poll error: {}", e.getMessage());
                            return Mono.empty();
                        }));
        return withHeartbeat(data);
    }

    // ─── Aggregated / indicator streams (polled from QuestDB) ───────────────────

    /**
     * GET /api/stream/candles?symbol=BTC-USDT&interval=5s
     * <p>
     * Delta-polls QuestDB every 500ms for candles newer than the last-seen timestamp,
     * emitting each candle as an individual SSE event (same pattern as trade streaming).
     * Poll interval is intentionally aligned with {@link #TICK_POLL_INTERVAL} so that
     * a candle's {@code close} price becomes visible to the client within the same
     * window as the matching trade event — preventing the trade stream from showing a
     * price that the candle stream has not yet reflected.
     * On first connect the cursor is set 5 minutes back, pre-populating the chart
     * with recent history; subsequent polls only send newly closed candles.
     */
    @GetMapping(value = "/candles", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Candle>> streamCandles(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "5s") String interval) {
        validateSymbol(symbol);
        AtomicLong seq = new AtomicLong(0);
        AtomicReference<Instant> cursor = new AtomicReference<>(Instant.now().minusSeconds(300));
        Flux<ServerSentEvent<Candle>> data = pollingTicks(TICK_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryCandlesSince(symbol, interval, cursor.get())
                        .doOnNext(candle -> cursor.set(candle.ts()))
                        .map(candle -> ServerSentEvent.<Candle>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("candle")
                                .data(candle)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("Candle stream error: {}", e.getMessage());
                            return Flux.empty();
                        })
                );
        return withHeartbeat(data);
    }

    /**
     * GET /api/stream/rsi?symbol=BTC-USDT&limit=100
     * <p>
     * Delta-polls QuestDB every 3s for RSI points newer than the last-seen timestamp.
     * {@code limit} controls initial history depth: cursor starts at
     * {@code now - limit × 5s} so the chart is pre-populated on connect.
     */
    @GetMapping(value = "/rsi", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RsiPoint>> streamRsi(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        validateSymbol(symbol);
        int safeLimit = Math.clamp(limit, 1, 500);
        AtomicLong seq = new AtomicLong(0);
        AtomicReference<Instant> cursor = new AtomicReference<>(
                Instant.now().minusSeconds((long) safeLimit * 5));
        Flux<ServerSentEvent<RsiPoint>> data = pollingTicks(INDICATOR_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryRsiSince(symbol, cursor.get())
                        .doOnNext(p -> cursor.set(p.ts()))
                        .map(p -> ServerSentEvent.<RsiPoint>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("rsi")
                                .data(p)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("RSI stream error: {}", e.getMessage());
                            return Flux.empty();
                        })
                );
        return withHeartbeat(data);
    }

    /**
     * GET /api/stream/bollinger?symbol=BTC-USDT&limit=100
     * <p>
     * Delta-polls QuestDB every 3s for Bollinger Band points newer than the last-seen
     * timestamp. {@code limit} controls initial history depth.
     */
    @GetMapping(value = "/bollinger", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BollingerBandPoint>> streamBollinger(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        validateSymbol(symbol);
        int safeLimit = Math.clamp(limit, 1, 500);
        AtomicLong seq = new AtomicLong(0);
        AtomicReference<Instant> cursor = new AtomicReference<>(
                Instant.now().minusSeconds((long) safeLimit * 5));
        Flux<ServerSentEvent<BollingerBandPoint>> data = pollingTicks(INDICATOR_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryBollingerSince(symbol, cursor.get())
                        .doOnNext(p -> cursor.set(p.ts()))
                        .map(p -> ServerSentEvent.<BollingerBandPoint>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("bollinger")
                                .data(p)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("Bollinger stream error: {}", e.getMessage());
                            return Flux.empty();
                        })
                );
        return withHeartbeat(data);
    }

    /**
     * GET /api/stream/vwap?symbol=BTC-USDT&limit=100
     * <p>
     * Delta-polls QuestDB every 3s for VWAP points newer than the last-seen timestamp.
     * {@code limit} controls initial history depth.
     */
    @GetMapping(value = "/vwap", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<VwapPoint>> streamVwap(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        validateSymbol(symbol);
        int safeLimit = Math.clamp(limit, 1, 500);
        AtomicLong seq = new AtomicLong(0);
        AtomicReference<Instant> cursor = new AtomicReference<>(
                Instant.now().minusSeconds((long) safeLimit * 5));
        Flux<ServerSentEvent<VwapPoint>> data = pollingTicks(INDICATOR_POLL_INTERVAL)
                .concatMap(_ -> queryClient.queryVwapSince(symbol, cursor.get())
                        .doOnNext(p -> cursor.set(p.ts()))
                        .map(p -> ServerSentEvent.<VwapPoint>builder()
                                .id(String.valueOf(seq.incrementAndGet()))
                                .event("vwap")
                                .data(p)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("VWAP stream error: {}", e.getMessage());
                            return Flux.empty();
                        })
                );
        return withHeartbeat(data);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private void validateSymbol(String symbol) {
        Set<String> supported = Set.copyOf(feedProperties.symbols());
        if (!supported.contains(symbol)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Unknown symbol '%s'. Supported symbols: %s"
                            .formatted(symbol, feedProperties.symbols()));
        }
    }

    /**
     * Merge a periodic heartbeat comment into an SSE data stream.
     * <p>
     * Keeps the connection alive when there are no data events — critical for
     * HTTP/2 where the browser relies on DATA frames to keep the H2 stream open.
     * <p>
     * An immediate {@code :connected} comment is prepended so that Spring WebFlux
     * writes the {@code 200 OK} response headers right away, establishing the SSE
     * connection before the first poll completes (or the first heartbeat fires).
     * Without this, clients see a hanging request when QuestDB has no recent data.
     */
    private <T> Flux<ServerSentEvent<T>> withHeartbeat(Flux<ServerSentEvent<T>> data) {
        Flux<ServerSentEvent<T>> connected = Flux.just(
                ServerSentEvent.<T>builder().comment("connected").build());
        Flux<ServerSentEvent<T>> heartbeat = pollingTicks(HEARTBEAT_INTERVAL)
                .skip(1) // keep `connected` as the first frame; heartbeat starts at +15s
                .map(_ -> ServerSentEvent.<T>builder()
                        .comment("heartbeat")
                        .build());
        return Flux.concat(connected, Flux.merge(data, heartbeat))
                .takeUntilOther(shutdownSignal.asMono());
    }
}
