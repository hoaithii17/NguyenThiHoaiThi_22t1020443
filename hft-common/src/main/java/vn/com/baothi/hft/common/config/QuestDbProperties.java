package vn.com.baothi.hft.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QuestDB connection properties shared across all services.
 * <p>
 * Builds the official questdb-client configuration string from Spring properties.
 *
 * @see <a href="https://questdb.com/docs/ingestion/clients/java/">QuestDB Java Client</a>
 */
@ConfigurationProperties(prefix = "questdb")
public record QuestDbProperties(
        /** QuestDB HTTP host (default: localhost) */
        String httpHost,
        /** QuestDB HTTP / ILP-over-HTTP port (default: 9000) */
        int httpPort,
        /** QuestDB REST API base URL (default: http://localhost:9000) */
        String restBaseUrl,
        /** Auto-flush after this many rows (default: 75000) */
        int autoFlushRows,
        /** Auto-flush interval in milliseconds (default: 1000) */
        int autoFlushIntervalMs,
        /** Retry timeout in milliseconds (default: 10000) */
        int retryTimeoutMs
) {
    public QuestDbProperties {
        if (httpHost == null || httpHost.isBlank()) httpHost = "localhost";
        if (httpPort <= 0) httpPort = 9000;
        if (restBaseUrl == null || restBaseUrl.isBlank()) restBaseUrl = "http://localhost:9000";
        if (autoFlushRows <= 0) autoFlushRows = 75_000;
        if (autoFlushIntervalMs <= 0) autoFlushIntervalMs = 1000;
        if (retryTimeoutMs <= 0) retryTimeoutMs = 10_000;
    }

    /**
     * Builds the official questdb-client configuration string.
     * <p>
     * Format: {@code http::addr=host:port;auto_flush_rows=N;auto_flush_interval=N;retry_timeout=N;}
     *
     * @return ILP-over-HTTP config string
     */
    public String toSenderConfigString() {
        return "http::addr=" + httpHost + ":" + httpPort + ";"
                + "auto_flush_rows=" + autoFlushRows + ";"
                + "auto_flush_interval=" + autoFlushIntervalMs + ";"
                + "retry_timeout=" + retryTimeoutMs + ";";
    }
}

