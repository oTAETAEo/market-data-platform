package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.alpaca.service.MarketDataProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaMessageHandler {

    private final ObjectMapper objectMapper;
    private final AlpacaSubscriptionFactory subscriptionFactory;
    private final AlpacaMessageMapper mapper;
    private final MarketDataProducer producer;

    public Mono<Void> handle(String payload, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        try {
            JsonNode messages = objectMapper.readTree(payload);
            if (!messages.isArray()) {
                log.debug("Alpaca 배열 형태가 아닌 메시지를 무시했습니다: {}", payload);
                return Mono.empty();
            }
            return Flux.fromIterable(messages)
                    .flatMap(message -> processMessage(message, outbound, subscribed))
                    .then();
        } catch (Exception error) {
            return publishParseFailure(payload, error, Instant.now());
        }
    }

    Mono<Void> processMessage(JsonNode message, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        Instant receivedAt = Instant.now();
        String type = message.path("T").asText();
        try {
            return switch (type) {
                case "success" -> processSuccess(message, outbound, subscribed);
                case "subscription" -> {
                    log.info("Alpaca 구독이 확인되었습니다: {}", message);
                    yield Mono.empty();
                }
                case "error" -> {
                    log.error("Alpaca WebSocket 오류 메시지: {}", message);
                    yield Mono.empty();
                }
                case "t" -> producer.sendTick(mapper.toTickEvent(message, receivedAt));
                case "q" -> producer.sendBookTicker(mapper.toBookTickerEvent(message, receivedAt));
                case "b", "u" -> producer.sendCandle(mapper.toCandleEvent(message, receivedAt));
                default -> {
                    log.debug("처리 대상이 아닌 Alpaca 메시지 타입을 무시했습니다: type={}, message={}", type, message);
                    yield Mono.empty();
                }
            };
        } catch (Exception error) {
            return publishParseFailure(message.toString(), error, receivedAt);
        }
    }

    private Mono<Void> processSuccess(JsonNode message, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        String status = message.path("msg").asText();
        log.info("Alpaca 세션 상태: {}", status);
        if ("authenticated".equals(status) && subscribed.compareAndSet(false, true)) {
            emit(outbound, subscriptionFactory.subscribeMessage(), "subscribe");
        }
        return Mono.empty();
    }

    private Mono<Void> publishParseFailure(String payload, Exception error, Instant receivedAt) {
        log.error("Alpaca payload 파싱에 실패했습니다: {}", payload, error);
        return producer.sendDlq(mapper.toDlqEvent(payload, error, receivedAt));
    }

    public void emit(Sinks.Many<String> outbound, String payload, String kind) {
        Sinks.EmitResult result = outbound.tryEmitNext(payload);
        if (result.isFailure()) {
            log.error("Alpaca {} queue failure: {}", kind, result);
        }
    }
}
