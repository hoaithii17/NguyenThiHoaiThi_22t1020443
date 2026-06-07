package vn.com.baothi.hft.common.parser;

import com.fasterxml.jackson.databind.JsonNode;
import vn.com.baothi.hft.common.dto.Trade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses OKX v5 WebSocket trade messages into Trade DTOs.
 * <p>
 * OKX trade message format:
 * <pre>
 * {
 *   "arg": {"channel": "trades", "instId": "BTC-USDT"},
 *   "data": [{
 *     "instId": "BTC-USDT", "tradeId": "123",
 *     "px": "42000.5", "sz": "1.5", "side": "buy", "ts": "1679856000000"
 *   }]
 * }
 * </pre>
 */
public class TradeMessageParser {

    public List<Trade> parse(JsonNode root) {
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return List.of();
        }

        List<Trade> trades = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            String symbol = item.get("instId").asText();
            String side = item.get("side").asText();
            double price = Double.parseDouble(item.get("px").asText());
            double amount = Double.parseDouble(item.get("sz").asText());
            long tsMillis = Long.parseLong(item.get("ts").asText());
            Instant ts = Instant.ofEpochMilli(tsMillis);

            trades.add(new Trade(ts, symbol, side, price, amount));
        }
        return trades;
    }
}

