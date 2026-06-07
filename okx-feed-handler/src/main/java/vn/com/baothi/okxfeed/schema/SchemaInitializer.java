package vn.com.baothi.okxfeed.schema;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import vn.com.baothi.hft.common.config.QuestDbProperties;
import vn.com.baothi.hft.common.schema.QuestDbSchema;

@Slf4j
@Component
public class SchemaInitializer {

    private final WebClient webClient;

    public SchemaInitializer(QuestDbProperties props) {
        this.webClient = WebClient.builder()
                .baseUrl(props.restBaseUrl())
                .build();
    }

    @PostConstruct
    public void initializeSchema() {
        log.info("▶ Initializing QuestDB schema...");

        for (String ddl : QuestDbSchema.ALL_DDL) {
            String sql = ddl.trim().replaceAll(";$", "");

            try {
                String response = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/exec")
                                .queryParam("query", sql)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("✅ DDL OK: {}", shortSql(sql));

            } catch (Exception e) {
                log.error("❌ DDL FAILED: {} → {}",
                        shortSql(sql), e.getMessage());
            }
        }

        log.info("✅ QuestDB schema initialization complete");
    }

    private String shortSql(String sql) {
        return sql.length() > 80 ? sql.substring(0, 80) + "..." : sql;
    }
}