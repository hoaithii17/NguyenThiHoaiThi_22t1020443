package vn.com.baothi.hft.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Represents top-5 order book levels from OKX books5 channel.
 * Uses individual columns instead of arrays for simpler ILP ingestion.
 */
public record OrderBook(
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,
        String symbol,
        double bid1Price, double bid1Size,
        double bid2Price, double bid2Size,
        double bid3Price, double bid3Size,
        double bid4Price, double bid4Size,
        double bid5Price, double bid5Size,
        double ask1Price, double ask1Size,
        double ask2Price, double ask2Size,
        double ask3Price, double ask3Size,
        double ask4Price, double ask4Size,
        double ask5Price, double ask5Size,
        double midPrice,
        double spread
) {}

