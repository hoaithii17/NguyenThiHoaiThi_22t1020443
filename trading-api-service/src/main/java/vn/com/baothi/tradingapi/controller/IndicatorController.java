package vn.com.baothi.tradingapi.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import vn.com.baothi.hft.common.dto.BollingerBandPoint;
import vn.com.baothi.hft.common.dto.RsiPoint;
import vn.com.baothi.hft.common.dto.VwapPoint;
import vn.com.baothi.tradingapi.client.QuestDbQueryClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
public class IndicatorController {

    private final QuestDbQueryClient queryClient;

    /**
     * GET /api/indicators/vwap?symbol=BTC-USDT&limit=500
     * Returns VWAP as typed DTOs.
     */
    @GetMapping("/vwap")
    public Mono<List<VwapPoint>> getVwap(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "500") int limit) {
        return queryClient.queryVwapTyped(symbol, Math.min(limit, 500));
    }

    /**
     * GET /api/indicators/bollinger?symbol=BTC-USDT&limit=500
     * Returns Bollinger Bands as typed DTOs.
     */
    @GetMapping("/bollinger")
    public Mono<List<BollingerBandPoint>> getBollingerBands(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "500") int limit) {
        return queryClient.queryBollingerTyped(symbol, Math.min(limit, 500));
    }

    /**
     * GET /api/indicators/rsi?symbol=BTC-USDT&limit=500
     * Returns RSI as typed DTOs.
     */
    @GetMapping("/rsi")
    public Mono<List<RsiPoint>> getRsi(
            @RequestParam(defaultValue = "BTC-USDT") String symbol,
            @RequestParam(defaultValue = "500") int limit) {
        return queryClient.queryRsiTyped(symbol, Math.min(limit, 500));
    }

    /**
     * GET /api/indicators/stats
     * Returns aggregated trading stats — demonstrates QuestDB aggregation speed.
     */
    @GetMapping("/stats")
    public Mono<List<Map<String, Object>>> getStats() {
        return queryClient.queryStats();
    }

    /**
     * GET /api/indicators/symbols
     * Returns list of available symbols.
     */
    @GetMapping("/symbols")
    public Mono<List<String>> getSymbols() {
        return queryClient.querySymbols();
    }
}

