package vn.com.baothi.tradingapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import vn.com.baothi.hft.common.dto.OrderBook;
import vn.com.baothi.hft.common.dto.Spread;
import vn.com.baothi.tradingapi.client.QuestDbQueryClient;

import java.util.List;

@RestController
@RequestMapping("/api/orderbook")
@RequiredArgsConstructor
public class OrderBookController {

    private final QuestDbQueryClient queryClient;

    /**
     * GET /api/orderbook?symbol=BTC-USDT
     * Returns the latest order book snapshot using QuestDB LATEST ON.
     * Returns a single object (not an array) — LATEST ON guarantees at most one row.
     */
    @GetMapping
    public Mono<OrderBook> getOrderBook(
            @RequestParam(defaultValue = "BTC-USDT") String symbol) {
        return queryClient.queryLatestOrderBook(symbol);
    }

    /**
     * GET /api/orderbook/spread?symbol=BTC-USDT&interval=5s
     * Returns spread history using QuestDB SAMPLE BY.
     */
    @GetMapping("/spread")
    public Mono<List<Spread>> getSpreadHistory(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "5s") String interval) {
        return queryClient.querySpreadHistory(symbol, interval);
    }
}

