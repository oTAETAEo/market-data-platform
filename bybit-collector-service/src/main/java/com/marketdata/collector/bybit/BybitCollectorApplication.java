package com.marketdata.collector.bybit;

import com.marketdata.collector.bybit.config.BybitWebSocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BybitWebSocketProperties.class)
public class BybitCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BybitCollectorApplication.class, args);
    }
}
