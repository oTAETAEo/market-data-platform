package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import com.marketdata.collector.alpaca.dto.AlpacaTradeMessage;
import com.marketdata.collector.alpaca.service.MarketTickProducer;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaWebSocketClient {

    private final AlpacaWebSocketProperties properties;

    private final MarketTickProducer producer;

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void connect() {
        WebSocketClient client = new ReactorNettyWebSocketClient();

        client.execute(URI.create(properties.url()), session -> {
                    Flux<WebSocketMessage> outboundMessages = Flux.just(
                            createAuthMessage(),
                            createSubscribeMessage()
                    ).map(session::textMessage);

                    Mono<Void> inboundMessages = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(this::processMessage)
                            .then();

                    return session.send(outboundMessages).then(inboundMessages);
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10)))
                .subscribe(
                        null,
                        error -> log.error("Alpaca WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Alpaca WebSocket 연결이 종료되었습니다")
                );
    }

    private String createAuthMessage() {
        return String.format(
                "{\"action\":\"auth\",\"key\":\"%s\",\"secret\":\"%s\"}",
                properties.key(),
                properties.secret()
        );
    }

    private String createSubscribeMessage() {
        String subscribedSymbols = Arrays.stream(properties.symbols().split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .map(symbol -> "\"" + symbol + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("\"BTC/USD\"");

        return String.format("{\"action\":\"subscribe\",\"trades\":[%s]}", subscribedSymbols);
    }

    private void processMessage(String jsonPayload) {
        Instant receivedAt = Instant.now();
        try {
            JsonNode messages = objectMapper.readTree(jsonPayload);
            if (!messages.isArray()) {
                log.debug("배열 형식이 아닌 Alpaca WebSocket 메시지를 수신했습니다: {}", jsonPayload);
                return;
            }

            for (JsonNode message : messages) {
                processMessageNode(message);
            }
        } catch (Exception e) {
            log.error("Alpaca WebSocket 메시지 파싱에 실패했습니다: {}", jsonPayload, e);
            producer.sendDlq(new MarketDlqEvent(
                    UUID.randomUUID().toString(),
                    "ALPACA",
                    "ALPACA_IEX",
                    "STOCK",
                    "alpaca.websocket",
                    "PARSE_FAILED",
                    jsonPayload,
                    e.getMessage(),
                    receivedAt,
                    1
            ));
        }
    }

    private void processMessageNode(JsonNode message) {
        Instant receivedAt = Instant.now();
        String messageType = message.path("T").asText();

        if ("success".equals(messageType) || "subscription".equals(messageType)) {
            log.info("Alpaca WebSocket 제어 메시지를 수신했습니다: {}", message);
            return;
        }

        if ("error".equals(messageType)) {
            log.error("Alpaca WebSocket 오류 메시지를 수신했습니다: {}", message);
            return;
        }

        AlpacaTradeMessage trade = objectMapper.convertValue(message, AlpacaTradeMessage.class);
        if (!trade.isTrade()) {
            log.debug("처리 대상이 아닌 Alpaca WebSocket 메시지를 무시했습니다: {}", message);
            return;
        }

        MarketTickEvent event = new MarketTickEvent(
                UUID.randomUUID().toString(),
                trade.symbol() + ":" + trade.timestamp(),
                "ALPACA",
                "ALPACA_IEX",
                "STOCK",
                trade.symbol(),
                trade.price(),
                trade.size(),
                trade.timestamp(),
                receivedAt,
                1
        );

        producer.send(event);
    }
}
