package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Represents a single trade execution from OKX.
 */
public record Trade(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        String symbol,
        String side,
        double price,
        double amount
) {}

