package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.bybit.dto.BybitKlineMessage;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BybitMessageMapper {

    private static final String PROVIDER = "BYBIT";
    public static final String VENUE = "BYBIT_LINEAR";
    private static final String ASSET_CLASS = "CRYPTO_FUTURES";
    private static final String WEBSOCKET_SOURCE = "bybit.websocket";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public MarketCandleEvent toCandleEvent(JsonNode data, String topic, Instant receivedAt, long fallbackEventTime) {
        BybitKlineMessage kline = objectMapper.convertValue(data, BybitKlineMessage.class);
        String symbol = symbolFromTopic(topic);
        String interval = normalizeInterval(kline.interval());
        long eventTime = kline.timestamp() != null ? kline.timestamp() : fallbackEventTime;

        return new MarketCandleEvent(
                UUID.randomUUID().toString(),
                symbol + ":" + interval + ":" + kline.start(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                symbol,
                interval,
                kline.open(),
                kline.high(),
                kline.low(),
                kline.close(),
                kline.volume(),
                Instant.ofEpochMilli(kline.start()),
                Instant.ofEpochMilli(kline.end()),
                kline.closed(),
                Instant.ofEpochMilli(eventTime),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public String candleKey(MarketCandleEvent event) {
        return event.venue() + ":" + event.symbol() + ":" + event.interval();
    }

    public MarketDlqEvent toDlqEvent(String payload, Exception error, Instant receivedAt) {
        return new MarketDlqEvent(
                UUID.randomUUID().toString(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                WEBSOCKET_SOURCE,
                PARSE_FAILED,
                payload,
                error.getMessage(),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    private String symbolFromTopic(String topic) {
        String[] parts = topic.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Bybit kline topic에서 심볼을 추출할 수 없습니다.");
        }
        return parts[2].toUpperCase(Locale.ROOT);
    }

    private String normalizeInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            throw new IllegalArgumentException("Bybit kline interval은 비어 있을 수 없습니다.");
        }
        return switch (interval) {
            case "D", "W", "M" -> interval.toLowerCase(Locale.ROOT);
            default -> interval + "m";
        };
    }
}
