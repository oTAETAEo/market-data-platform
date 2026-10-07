package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.binance.service.MarketDataProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceMessageHandler {

    private final ObjectMapper objectMapper;
    private final BinanceMessageMapper mapper;
    private final MarketDataProducer producer;

    public Mono<Void> handle(String jsonPayload) {
        Instant receivedAt = Instant.now();
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            JsonNode data = root.has("data") ? root.path("data") : root;
            String stream = root.has("stream") ? root.path("stream").asText() : data.path("e").asText();
            return route(jsonPayload, data, stream, receivedAt);
        } catch (Exception error) {
            return publishParseFailure(jsonPayload, error, receivedAt);
        }
    }

    private Mono<Void> route(String jsonPayload, JsonNode data, String stream, Instant receivedAt) {
        try {
            if (stream.contains("@kline_") || "kline".equals(data.path("e").asText())) {
                var event = mapper.toCandleEvent(data, receivedAt);
                return producer.sendCandle(event, mapper.candleKey(event));
            }

            log.debug("처리 대상이 아닌 Binance WebSocket 메시지를 무시했습니다: {}", jsonPayload);
            return Mono.empty();
        } catch (Exception error) {
            return publishParseFailure(jsonPayload, error, receivedAt);
        }
    }

    private Mono<Void> publishParseFailure(String payload, Exception error, Instant receivedAt) {
        log.error("Binance WebSocket 메시지 파싱에 실패했습니다: {}", payload, error);
        return producer.sendDlq(mapper.toDlqEvent(payload, error, receivedAt));
    }
}
