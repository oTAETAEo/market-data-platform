package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.bybit.service.MarketDataProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BybitMessageHandler {

    private final ObjectMapper objectMapper;
    private final BybitMessageMapper mapper;
    private final MarketDataProducer producer;

    public Mono<Void> handle(String jsonPayload) {
        Instant receivedAt = Instant.now();
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String topic = root.path("topic").asText();
            if (!topic.startsWith("kline.")) {
                log.debug("처리 대상이 아닌 Bybit WebSocket 메시지를 무시했습니다: {}", jsonPayload);
                return Mono.empty();
            }
            long fallbackEventTime = root.path("ts").asLong(receivedAt.toEpochMilli());
            return Flux.fromIterable(root.path("data"))
                    .concatMap(data -> {
                        var event = mapper.toCandleEvent(data, topic, receivedAt, fallbackEventTime);
                        return producer.sendCandle(event, mapper.candleKey(event));
                    })
                    .then();
        } catch (Exception error) {
            return publishParseFailure(jsonPayload, error, receivedAt);
        }
    }

    private Mono<Void> publishParseFailure(String payload, Exception error, Instant receivedAt) {
        log.error("Bybit WebSocket 메시지 파싱에 실패했습니다: {}", payload, error);
        return producer.sendDlq(mapper.toDlqEvent(payload, error, receivedAt));
    }
}
