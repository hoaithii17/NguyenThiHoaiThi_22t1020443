package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * A single VWAP (Volume Weighted Average Price) data point.
 * Computed from 5-second candles with cumulative window function.
 */
public record VwapPoint(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        double close,
        double vwap
) {}

