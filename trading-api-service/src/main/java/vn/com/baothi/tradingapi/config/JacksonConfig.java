package vn.com.baothi.tradingapi.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Global Jackson configuration for financial-grade JSON serialization.
 * <p>
 * Fixes two issues with {@code double} fields in DTOs (Candle, Trade, OrderBook, Spread):
 * <ul>
 *   <li>Floating-point noise: {@code 0.18486587000000002} → {@code 0.18486587}</li>
 *   <li>Scientific notation: {@code 7.087899999999999E-4} → {@code 0.00070879}</li>
 * </ul>
 * Rounds to 8 decimal places (OKX's maximum precision for amounts) and
 * uses plain decimal notation.
 * <p>
 * The ObjectMapper bean is explicitly injected into WebFlux codecs
 * via {@link WebClientConfig#configureHttpMessageCodecs} to guarantee
 * it is used for both REST responses and SSE event serialization.
 */
@Configuration
public class JacksonConfig {

    /**
     * Maximum decimal places for financial values.
     * OKX uses 8 decimal places for BTC amounts (satoshi precision).
     */
    private static final int FINANCIAL_SCALE = 8;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

        SimpleModule module = new SimpleModule("FinancialDoubleModule");
        var serializer = new FinancialDoubleSerializer();
        module.addSerializer(Double.class, serializer);
        module.addSerializer(double.class, serializer);
        mapper.registerModule(module);

        return mapper;
    }

    /**
     * Serializes {@code double} values with controlled precision.
     * <p>
     * Uses {@link BigDecimal} to round to {@value FINANCIAL_SCALE} decimal places,
     * strip trailing zeros, and write as a plain JSON number (no scientific notation).
     */
    static class FinancialDoubleSerializer extends JsonSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            if (value == null || value.isNaN() || value.isInfinite()) {
                gen.writeNull();
            } else {
                gen.writeNumber(BigDecimal.valueOf(value)
                        .setScale(FINANCIAL_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString());
            }
        }

        @Override
        public Class<Double> handledType() {
            return Double.class;
        }
    }
}
