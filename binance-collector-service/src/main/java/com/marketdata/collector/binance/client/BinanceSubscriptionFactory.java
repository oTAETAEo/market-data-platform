package com.marketdata.collector.binance.client;

import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class BinanceSubscriptionFactory {

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

        String streams = symbols.stream()
                .map(this::klineStream)
                .collect(java.util.stream.Collectors.joining("/"));

        String querySeparator = properties.getBaseUrl().contains("?") ? "&" : "?";
        return properties.getBaseUrl() + querySeparator + "streams=" + streams;
    }

    private String klineStream(String symbol) {
        return symbol.toLowerCase(Locale.ROOT) + "@kline_" + properties.getKlineInterval();
    }
}
