package vn.com.baothi.okxfeed.ingestion;

import io.questdb.client.Sender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.com.baothi.hft.common.dto.Trade;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeIngestor {

    private final Sender sender;

    public void ingest(Trade trade) {
        try {
            // 🔥 VALIDATE TIMESTAMP (FIX 1970 ERROR)
            if (trade.ts() == null || trade.ts().toEpochMilli() <= 0) {
                log.warn("❌ INVALID TS -> skip trade: {} {}",
                        trade.symbol(), trade.price());
                return;
            }

            Instant ts = trade.ts();

            sender.table("trades")
                    .symbol("symbol", trade.symbol())
                    .symbol("side", trade.side())
                    .doubleColumn("price", trade.price())
                    .doubleColumn("amount", trade.amount())
                    .timestampColumn("ts", ts)   // 🔥 IMPORTANT
                    .at(ts);

            log.debug("✅ INGEST OK: {} {} {}",
                    trade.symbol(), trade.price(), ts);

        } catch (Exception e) {
            log.error("❌ FAILED INGEST: {} {} @ {}",
                    trade.symbol(), trade.price(), trade.ts(), e);
        }
    }
}