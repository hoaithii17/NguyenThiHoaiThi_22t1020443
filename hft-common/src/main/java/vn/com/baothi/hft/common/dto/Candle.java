package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * OHLCV candle aggregated by QuestDB materialized views.
 */
public record Candle(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        String symbol,
        double open,
        double high,
        double low,
        double close,
        double volume
) {}

