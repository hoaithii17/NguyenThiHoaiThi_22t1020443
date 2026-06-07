package vn.com.baothi.hft.common.parser;

import com.fasterxml.jackson.databind.JsonNode;
import vn.com.baothi.hft.common.dto.OrderBook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses OKX v5 WebSocket books5 messages into OrderBook DTOs.
 * <p>
 * OKX books5 message format:
 * <pre>
 * {
 *   "arg": {"channel": "books5", "instId": "BTC-USDT"},
 *   "data": [{
 *     "asks": [["42001.0","1.8","0","1"], ...],
 *     "bids": [["41999.0","2.1","0","1"], ...],
 *     "instId": "BTC-USDT", "ts": "1679856000000"
 *   }]
 * }
 * </pre>
 * Each level: [price, size, deprecated, numOrders]
 */
public class OrderBookMessageParser {

    public List<OrderBook> parse(JsonNode root) {
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return List.of();
        }

        List<OrderBook> books = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            String symbol = item.get("instId").asText();
            long tsMillis = Long.parseLong(item.get("ts").asText());
            Instant ts = Instant.ofEpochMilli(tsMillis);

            JsonNode bids = item.get("bids");
            JsonNode asks = item.get("asks");

            double[] bidPrices = new double[5];
            double[] bidSizes = new double[5];
            double[] askPrices = new double[5];
            double[] askSizes = new double[5];

            for (int i = 0; i < 5; i++) {
                if (bids != null && i < bids.size()) {
                    bidPrices[i] = Double.parseDouble(bids.get(i).get(0).asText());
                    bidSizes[i] = Double.parseDouble(bids.get(i).get(1).asText());
                }
                if (asks != null && i < asks.size()) {
                    askPrices[i] = Double.parseDouble(asks.get(i).get(0).asText());
                    askSizes[i] = Double.parseDouble(asks.get(i).get(1).asText());
                }
            }

            double midPrice = (bidPrices[0] + askPrices[0]) / 2.0;
            double spread = askPrices[0] - bidPrices[0];

            books.add(new OrderBook(
                    ts, symbol,
                    bidPrices[0], bidSizes[0],
                    bidPrices[1], bidSizes[1],
                    bidPrices[2], bidSizes[2],
                    bidPrices[3], bidSizes[3],
                    bidPrices[4], bidSizes[4],
                    askPrices[0], askSizes[0],
                    askPrices[1], askSizes[1],
                    askPrices[2], askSizes[2],
                    askPrices[3], askSizes[3],
                    askPrices[4], askSizes[4],
                    midPrice, spread
            ));
        }
        return books;
    }
}

