package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.bybit.config.BybitWebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BybitSubscriptionFactory {

    private final BybitWebSocketProperties properties;
    private final ObjectMapper objectMapper;

    public String streamUrl() {
        return properties.getBaseUrl();
    }

    public List<String> symbolList() {
        return Arrays.stream(properties.getSymbols().split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public List<String> topics() {
        List<String> symbols = symbolList();
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("Bybit 구독 심볼은 최소 1개 이상 설정해야 합니다.");
        }
        return symbols.stream()
                .map(symbol -> "kline." + properties.getKlineInterval() + "." + symbol)
                .toList();
    }

    public String subscriptionMessage() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "op", "subscribe",
                    "args", topics()
            ));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Bybit 구독 메시지를 생성할 수 없습니다.", error);
        }
    }
}
