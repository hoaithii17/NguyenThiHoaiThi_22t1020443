package vn.com.baothi.tradingapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.com.baothi.hft.common.dto.Candle;
import vn.com.baothi.tradingapi.client.QuestDbQueryClient;

import java.util.List;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
public class CandleController {

    private final QuestDbQueryClient queryClient;

    /**
     * GET /api/candles?symbol=BTC-USDT&interval=5s
     * Returns OHLCV candles. Uses QuestDB materialized views for 5s/1m/1h,
     * or dynamic SAMPLE BY for custom intervals.
     */
    @GetMapping
    public Mono<List<Candle>> getCandles(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "5s") String interval) {
        return queryClient.queryCandles(symbol, interval);
    }
}

