package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * A single Bollinger Band data point.
 * Computed from 5-second candles with a 20-period SMA and 2σ bands.
 */
public record BollingerBandPoint(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        double close,
        double sma20,
        double upperBand,
        double lowerBand
) {}

