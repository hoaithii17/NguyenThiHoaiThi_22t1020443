package vn.com.baothi.okxfeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import vn.com.baothi.hft.common.config.QuestDbProperties;
import vn.com.baothi.hft.common.config.OkxFeedProperties;

@SpringBootApplication
@EnableConfigurationProperties({QuestDbProperties.class, OkxFeedProperties.class})
public class OkxFeedHandlerApplication {

    static void main(String[] args) {
        SpringApplication.run(OkxFeedHandlerApplication.class, args);
    }
}
