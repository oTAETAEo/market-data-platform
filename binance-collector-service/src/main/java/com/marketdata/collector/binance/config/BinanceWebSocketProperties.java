package com.marketdata.collector.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "binance.websocket")
public class BinanceWebSocketProperties {

    private static final String DEFAULT_BASE_URL = "wss://stream.binance.com:9443/stream";
    private static final String DEFAULT_KLINE_INTERVAL = "1m";
    private static final int DEFAULT_DEPTH_LEVELS = 20;
    private static final String DEFAULT_DEPTH_UPDATE_INTERVAL = "100ms";

    private String baseUrl = DEFAULT_BASE_URL;
    private String symbols = "BTCUSDT";
    private String klineInterval = DEFAULT_KLINE_INTERVAL;
    private int depthLevels = DEFAULT_DEPTH_LEVELS;
    private String depthUpdateInterval = DEFAULT_DEPTH_UPDATE_INTERVAL;
}
