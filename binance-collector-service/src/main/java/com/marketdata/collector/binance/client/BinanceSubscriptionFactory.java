package com.marketdata.collector.binance.client;

import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class BinanceSubscriptionFactory {

    private static final Set<Integer> SUPPORTED_DEPTH_LEVELS = Set.of(5, 10, 20);
    private static final Set<String> SUPPORTED_DEPTH_UPDATE_INTERVALS = Set.of("100ms", "1000ms");

    private final BinanceWebSocketProperties properties;

    public List<String> symbolList() {
        return Arrays.stream(properties.getSymbols().split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public String streamUrl() {
        List<String> symbols = symbolList();
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("Binance 구독 심볼은 최소 1개 이상 설정해야 합니다.");
        }
        validateDepthConfiguration();

        String streams = symbols.stream()
                .flatMap(this::marketStreams)
                .collect(java.util.stream.Collectors.joining("/"));

        String querySeparator = properties.getBaseUrl().contains("?") ? "&" : "?";
        return properties.getBaseUrl() + querySeparator + "streams=" + streams;
    }

    private Stream<String> marketStreams(String symbol) {
        String normalizedSymbol = symbol.toLowerCase(Locale.ROOT);
        return Stream.of(
                normalizedSymbol + "@trade",
                normalizedSymbol + "@bookTicker",
                normalizedSymbol + "@kline_" + properties.getKlineInterval(),
                depthStream(normalizedSymbol)
        );
    }

    private String depthStream(String normalizedSymbol) {
        String stream = normalizedSymbol + "@depth" + properties.getDepthLevels();
        return "100ms".equals(properties.getDepthUpdateInterval()) ? stream + "@100ms" : stream;
    }

    private void validateDepthConfiguration() {
        if (!SUPPORTED_DEPTH_LEVELS.contains(properties.getDepthLevels())) {
            throw new IllegalArgumentException("Binance depth 단계는 5, 10, 20 중 하나여야 합니다.");
        }
        if (!SUPPORTED_DEPTH_UPDATE_INTERVALS.contains(properties.getDepthUpdateInterval())) {
            throw new IllegalArgumentException("Binance depth 갱신 간격은 100ms 또는 1000ms여야 합니다.");
        }
    }
}
