package com.marketdata.collector.alpaca;

import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AlpacaWebSocketProperties.class)
public class AlpacaCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlpacaCollectorApplication.class, args);
    }
}
