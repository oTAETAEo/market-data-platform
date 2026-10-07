package com.marketdata.collector.bybit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "bybit.websocket")
public class BybitWebSocketProperties {

    private static final String DEFAULT_BASE_URL = "wss://stream.bybit.com/v5/public/linear";
    private static final String DEFAULT_KLINE_INTERVAL = "15";

    private String baseUrl = DEFAULT_BASE_URL;
    private String symbols = "BTCUSDT";
    private String klineInterval = DEFAULT_KLINE_INTERVAL;
}
