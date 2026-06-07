package vn.com.baothi.okxfeed.config;

import io.questdb.client.Sender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.com.baothi.hft.common.config.QuestDbProperties;

/**
 * Creates a Spring-managed {@link Sender} bean using the official QuestDB Java client.
 * <p>
 * The Sender uses ILP-over-HTTP with built-in:
 * <ul>
 *   <li>Automatic batching &amp; auto-flush</li>
 *   <li>Automatic write retries on transient failures</li>
 *   <li>Automatic table creation</li>
 *   <li>Connection health checks</li>
 * </ul>
 *
 * @see <a href="https://questdb.com/docs/ingestion/clients/java/">QuestDB Java Client</a>
 */
@Slf4j
@Configuration
public class QuestDbSenderConfig {

    @Bean(destroyMethod = "close")
    public Sender questDbSender(QuestDbProperties props) {
        String config = props.toSenderConfigString();
        log.info("▶ Creating QuestDB Sender: {}", config);
        return Sender.fromConfig(config);
    }
}

