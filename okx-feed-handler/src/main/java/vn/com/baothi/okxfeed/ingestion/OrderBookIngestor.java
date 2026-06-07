package vn.com.baothi.okxfeed.ingestion;

import io.questdb.client.Sender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.com.baothi.hft.common.dto.OrderBook;

/**
 * Ingests {@link OrderBook} DTOs into QuestDB via the official Java client.
 * <p>
 * Uses {@link Sender} with ILP-over-HTTP, which provides automatic batching,
 * auto-flush, retry on transient failures, and automatic table creation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookIngestor {

    private final Sender sender;

    public void ingest(OrderBook book) {
        try {
            sender.table("orderbook")
                    .symbol("symbol", book.symbol())
                    .doubleColumn("bid1_price", book.bid1Price())
                    .doubleColumn("bid1_size", book.bid1Size())
                    .doubleColumn("bid2_price", book.bid2Price())
                    .doubleColumn("bid2_size", book.bid2Size())
                    .doubleColumn("bid3_price", book.bid3Price())
                    .doubleColumn("bid3_size", book.bid3Size())
                    .doubleColumn("bid4_price", book.bid4Price())
                    .doubleColumn("bid4_size", book.bid4Size())
                    .doubleColumn("bid5_price", book.bid5Price())
                    .doubleColumn("bid5_size", book.bid5Size())
                    .doubleColumn("ask1_price", book.ask1Price())
                    .doubleColumn("ask1_size", book.ask1Size())
                    .doubleColumn("ask2_price", book.ask2Price())
                    .doubleColumn("ask2_size", book.ask2Size())
                    .doubleColumn("ask3_price", book.ask3Price())
                    .doubleColumn("ask3_size", book.ask3Size())
                    .doubleColumn("ask4_price", book.ask4Price())
                    .doubleColumn("ask4_size", book.ask4Size())
                    .doubleColumn("ask5_price", book.ask5Price())
                    .doubleColumn("ask5_size", book.ask5Size())
                    .doubleColumn("mid_price", book.midPrice())
                    .doubleColumn("spread", book.spread())
                    .at(book.ts());
        } catch (Exception e) {
            log.error("Failed to ingest orderbook: {} — {}", book.symbol(), e.getMessage());
        }
    }
}

