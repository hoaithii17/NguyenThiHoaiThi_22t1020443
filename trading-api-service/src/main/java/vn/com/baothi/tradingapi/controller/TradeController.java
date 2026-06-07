package vn.com.baothi.tradingapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.com.baothi.hft.common.dto.Trade;
import vn.com.baothi.tradingapi.client.QuestDbQueryClient;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final QuestDbQueryClient queryClient;

    /**
     * GET /api/trades?symbol=BTC-USDT&limit=100
     * Returns recent trades for a symbol.
     */
    @GetMapping
    public Mono<List<Trade>> getTrades(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        return queryClient.queryTrades(symbol, limit);
    }
}

