package com.marketdata.collector.binance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Getter
@Setter
@ConfigurationProperties(prefix = "binance.websocket")
public class BinanceWebSocketProperties {

    private static final String DEFAULT_BASE_URL = "wss://stream.binance.com:9443/stream";
    private static final String DEFAULT_KLINE_INTERVAL = "1m";
    private static final int DEFAULT_DEPTH_LEVELS = 20;
    private static final String DEFAULT_DEPTH_UPDATE_INTERVAL = "100ms";
    private static final Set<Integer> SUPPORTED_DEPTH_LEVELS = Set.of(5, 10, 20);
    private static final Set<String> SUPPORTED_DEPTH_UPDATE_INTERVALS = Set.of("100ms", "1000ms");

    private String baseUrl = DEFAULT_BASE_URL;
    private String symbols = "BTCUSDT";
    private String klineInterval = DEFAULT_KLINE_INTERVAL;
    private int depthLevels = DEFAULT_DEPTH_LEVELS;
    private String depthUpdateInterval = DEFAULT_DEPTH_UPDATE_INTERVAL;

    public List<String> symbolList() {
        return Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public String combinedStreamUrl() {
        List<String> symbolList = symbolList();
        if (symbolList.isEmpty()) {
            throw new IllegalArgumentException("Binance 구독 심볼은 최소 1개 이상 설정해야 합니다.");
        }
        validateDepthConfiguration();

        String streams = symbolList.stream()
                .flatMap(this::marketStreams)
                .collect(java.util.stream.Collectors.joining("/"));

        String querySeparator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + querySeparator + "streams=" + streams;
    }

    private Stream<String> marketStreams(String symbol) {
        String normalizedSymbol = symbol.toLowerCase(Locale.ROOT);
        return Stream.of(
                normalizedSymbol + "@trade",
                normalizedSymbol + "@bookTicker",
                normalizedSymbol + "@kline_" + klineInterval,
                depthStream(normalizedSymbol)
        );
    }

    private String depthStream(String normalizedSymbol) {
        String stream = normalizedSymbol + "@depth" + depthLevels;
        return "100ms".equals(depthUpdateInterval) ? stream + "@100ms" : stream;
    }

    private void validateDepthConfiguration() {
        if (!SUPPORTED_DEPTH_LEVELS.contains(depthLevels)) {
            throw new IllegalArgumentException("Binance depth 단계는 5, 10, 20 중 하나여야 합니다.");
        }
        if (!SUPPORTED_DEPTH_UPDATE_INTERVALS.contains(depthUpdateInterval)) {
            throw new IllegalArgumentException("Binance depth 갱신 간격은 100ms 또는 1000ms여야 합니다.");
        }
    }
}
