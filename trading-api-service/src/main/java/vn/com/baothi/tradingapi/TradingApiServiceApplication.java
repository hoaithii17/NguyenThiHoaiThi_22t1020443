package vn.com.baothi.tradingapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import vn.com.baothi.hft.common.config.OkxFeedProperties;

@SpringBootApplication
@EnableConfigurationProperties(OkxFeedProperties.class)
public class TradingApiServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(TradingApiServiceApplication.class, args);
    }
}

