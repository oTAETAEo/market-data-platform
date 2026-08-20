package com.marketdata.collector.binance;

import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(BinanceWebSocketProperties.class)
public class BinanceCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BinanceCollectorApplication.class, args);
    }
}
