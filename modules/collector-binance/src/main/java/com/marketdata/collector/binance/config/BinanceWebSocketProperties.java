package com.marketdata.collector.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "binance.websocket")
public class BinanceWebSocketProperties {

    private static final String DEFAULT_BASE_URL = "wss://fstream.binance.com/public/stream";
    private static final String DEFAULT_KLINE_INTERVAL = "15m";

    private String baseUrl = DEFAULT_BASE_URL;
    private String symbols = "BTCUSDT";
    private String klineInterval = DEFAULT_KLINE_INTERVAL;
}
