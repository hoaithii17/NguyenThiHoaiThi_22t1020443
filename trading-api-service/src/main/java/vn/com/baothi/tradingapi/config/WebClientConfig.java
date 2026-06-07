package vn.com.baothi.tradingapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux configuration: CORS, buffer limits, and Jackson codec wiring.
 * <p>
 * Uses {@link Jackson2JsonEncoder}/{@link Jackson2JsonDecoder} (Jackson 2.x / com.fasterxml)
 * which are deprecated but still the correct path until the Jackson 3.x (tools.jackson)
 * migration is complete in Spring Framework 7.
 */
@SuppressWarnings("removal")   // Jackson2Json* deprecated in favor of tools.jackson — not yet migrated
@Configuration
public class WebClientConfig implements WebFluxConfigurer {

    private final ObjectMapper objectMapper;

    public WebClientConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(512 * 1024); // 512 KB
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
    }
}
