package vn.com.baothi.okxfeed.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.questdb.client.Sender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import vn.com.baothi.hft.common.config.OkxFeedProperties;
import vn.com.baothi.okxfeed.ingestion.OrderBookIngestor;
import vn.com.baothi.okxfeed.ingestion.TradeIngestor;
import vn.com.baothi.hft.common.parser.OrderBookMessageParser;
import vn.com.baothi.hft.common.parser.TradeMessageParser;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactive WebSocket client for OKX v5 Public API — performance-optimized.
 * <p>
 * Architecture:
 * <ol>
 *   <li>WebSocket messages arrive on the Netty event-loop → kept lightweight</li>
 *   <li>Raw payloads are emitted into a Reactor {@link Sinks.Many} (lock-free)</li>
 *   <li>A dedicated ingestion scheduler picks up payloads, parses JSON, and
 *       writes to QuestDB — fully off the event loop</li>
 * </ol>
 * <p>
 * This decouples the network I/O thread from CPU-intensive JSON parsing and
 * QuestDB ILP writes, preventing back-pressure from stalling the WebSocket read.
 */
@Slf4j
@Component
public class OkxWebSocketHandler {

    private final OkxFeedProperties okxProps;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TradeMessageParser tradeParser = new TradeMessageParser();
    private final OrderBookMessageParser bookParser = new OrderBookMessageParser();
    private final Sender sender;
    private final TradeIngestor tradeIngestor;
    private final OrderBookIngestor bookIngestor;

    private final AtomicLong tradeCount = new AtomicLong(0);
    private final AtomicLong bookCount = new AtomicLong(0);

    /**
     * Lock-free sink: event loop emits raw payloads here.
     * directBestEffort drops if the downstream can't keep up — we never stall the WS read.
     */
    private final Sinks.Many<String> rawPayloadSink =
            Sinks.many().unicast().onBackpressureBuffer();

    /**
     * Dedicated single-thread scheduler for QuestDB ingestion.
     * The QuestDB Sender is NOT thread-safe — a single thread avoids synchronization.
     */
    private final Scheduler ingestionScheduler =
            Schedulers.fromExecutorService(
                    Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "questdb-ingest");
                        t.setDaemon(true);
                        return t;
                    }),
                    "questdb-ingest");

    private Disposable wsDisposable;
    private Disposable ingestionDisposable;
    private Disposable metricsDisposable;
    private Disposable flushDisposable;

    public OkxWebSocketHandler(
            OkxFeedProperties okxProps,
            Sender sender,
            TradeIngestor tradeIngestor,
            OrderBookIngestor bookIngestor) {
        this.okxProps = okxProps;
        this.sender = sender;
        this.tradeIngestor = tradeIngestor;
        this.bookIngestor = bookIngestor;
    }

    @PostConstruct
    public void connect() {
        log.info("▶ Connecting to OKX WebSocket: {}", okxProps.wsUrl());
        log.info("  Symbols: {}", okxProps.symbols());
        log.info("  Channels: {}", okxProps.channels());

        // Start the ingestion pipeline BEFORE the WebSocket connects
        startIngestionPipeline();

        var client = new ReactorNettyWebSocketClient();

        wsDisposable = client.execute(
                URI.create(okxProps.wsUrl()),
                session -> {
                    String subscribeMsg = buildSubscribeMessage();
                    log.info("  Sending subscribe: {}", subscribeMsg);

                    var send = session.send(
                            Mono.just(session.textMessage(subscribeMsg))
                    );

                    // Only extract text on the event loop — everything else is offloaded
                    var receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(rawPayloadSink::tryEmitNext)
                            .then();

                    return send.thenMany(receive).then();
                }
        ).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(okxProps.reconnectDelaySec()))
                .maxBackoff(Duration.ofSeconds(60))
                .doBeforeRetry(signal -> log.warn("🔄 Reconnecting to OKX WebSocket (attempt {}): {}",
                        signal.totalRetries() + 1, signal.failure().getMessage()))
        ).subscribe(
                unused -> {},
                error -> log.error("❌ OKX WebSocket fatal error: {}", error.getMessage()),
                () -> log.info("OKX WebSocket completed")
        );

        // Non-blocking metrics logger
        metricsDisposable = Flux.interval(Duration.ofSeconds(10))
                .doOnNext(_ -> {
                    long trades = tradeCount.getAndSet(0);
                    long books = bookCount.getAndSet(0);
                    log.info("📊 Last 10s: {} trades, {} orderbook updates ingested", trades, books);
                })
                .subscribe();

        // Explicit periodic flush on the ingestion thread — guarantees the Sender
        // buffer is drained even during low-traffic windows.  The auto_flush_interval
        // config only checks at row-append time; this timer fires unconditionally.
        flushDisposable = Flux.interval(Duration.ofMillis(200))
                .publishOn(ingestionScheduler)
                .doOnNext(_ -> {
                    try {
                        sender.flush();
                    } catch (Exception e) {
                        log.debug("Flush error (will retry): {}", e.getMessage());
                    }
                })
                .subscribe();
    }

    /**
     * Ingestion pipeline: drains the raw-payload sink on a dedicated thread,
     * parses JSON, and writes to QuestDB. Runs entirely off the Netty event loop.
     */
    private void startIngestionPipeline() {
        ingestionDisposable = rawPayloadSink.asFlux()
                .publishOn(ingestionScheduler)
                .doOnNext(this::handleMessage)
                .onErrorContinue((e, obj) ->
                        log.error("Ingestion pipeline error: {}", e.getMessage()))
                .subscribe();
        log.info("  ✅ Ingestion pipeline started on dedicated thread");
    }

    @PreDestroy
    public void disconnect() {
        if (metricsDisposable != null && !metricsDisposable.isDisposed()) {
            metricsDisposable.dispose();
        }
        if (flushDisposable != null && !flushDisposable.isDisposed()) {
            flushDisposable.dispose();
        }
        if (wsDisposable != null && !wsDisposable.isDisposed()) {
            wsDisposable.dispose();
        }
        if (ingestionDisposable != null && !ingestionDisposable.isDisposed()) {
            ingestionDisposable.dispose();
        }
        // Final flush to drain any remaining rows in the Sender buffer
        try {
            sender.flush();
        } catch (Exception e) {
            log.warn("Final flush error: {}", e.getMessage());
        }
        ingestionScheduler.dispose();
        log.info("⏹ OKX WebSocket disconnected");
    }

    private void handleMessage(String payload) {
        try {
            if ("ping".equals(payload)) return;

            JsonNode root = mapper.readTree(payload);

            if (root.has("event")) {
                String event = root.get("event").asText();
                if ("subscribe".equals(event)) {
                    log.info("  ✅ Subscribed: {}", root.get("arg"));
                } else if ("error".equals(event)) {
                    log.error("  ❌ OKX error: code={}, msg={}",
                            root.get("code").asText(), root.get("msg").asText());
                }
                return;
            }

            if (root.has("arg") && root.has("data")) {
                String channel = root.get("arg").get("channel").asText();

                switch (channel) {
                    case "trades" -> {
                        var trades = tradeParser.parse(root);
                        trades.forEach(tradeIngestor::ingest);
                        tradeCount.addAndGet(trades.size());
                    }
                    case "books5" -> {
                        var books = bookParser.parse(root);
                        books.forEach(bookIngestor::ingest);
                        bookCount.addAndGet(books.size());
                    }
                    default -> log.debug("Unknown channel: {}", channel);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle message: {}", e.getMessage());
        }
    }

    private String buildSubscribeMessage() {
        try {
            ObjectNode msg = mapper.createObjectNode();
            msg.put("op", "subscribe");
            ArrayNode args = mapper.createArrayNode();

            for (String channel : okxProps.channels()) {
                for (String symbol : okxProps.symbols()) {
                    ObjectNode arg = mapper.createObjectNode();
                    arg.put("channel", channel);
                    arg.put("instId", symbol);
                    args.add(arg);
                }
            }
            msg.set("args", args);
            return mapper.writeValueAsString(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build subscribe message", e);
        }
    }
}

