package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * A single RSI (Relative Strength Index) data point.
 * Computed from 5-second candles with a 14-period lookback.
 */
public record RsiPoint(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        double close,
        double rsi
) {}

