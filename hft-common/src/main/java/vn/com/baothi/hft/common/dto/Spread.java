package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Bid-ask spread snapshot.
 */
public record Spread(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        String symbol,
        double bestBid,
        double bestAsk,
        double spread,
        double midPrice
) {}

