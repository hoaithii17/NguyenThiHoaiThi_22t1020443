package vn.com.baothi.hft.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * OKX v5 WebSocket API configuration — shared by all services that connect to OKX.
 */
@ConfigurationProperties(prefix = "okx")
public record OkxFeedProperties(
        /** WebSocket URL (default: wss://ws.okx.com:8443/ws/v5/public) */
        String wsUrl,
        /** Instrument IDs to subscribe (e.g., BTC-USDT, ETH-USDT, SOL-USDT) */
        List<String> symbols,
        /** Channels to subscribe (e.g., trades, books5) */
        List<String> channels,
        /** Reconnect delay in seconds after disconnect */
        int reconnectDelaySec
) {
    public OkxFeedProperties {
        if (wsUrl == null || wsUrl.isBlank()) wsUrl = "wss://ws.okx.com:8443/ws/v5/public";
        if (symbols == null || symbols.isEmpty()) symbols = List.of("BTC-USDT", "ETH-USDT", "SOL-USDT");
        if (channels == null || channels.isEmpty()) channels = List.of("trades", "books5");
        if (reconnectDelaySec <= 0) reconnectDelaySec = 5;
    }
}

