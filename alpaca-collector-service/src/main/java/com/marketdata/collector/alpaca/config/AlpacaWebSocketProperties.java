package com.marketdata.collector.alpaca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca.websocket")
public record AlpacaWebSocketProperties(
        String url,
        String key,
        String secret,
        String symbols
) {
}
