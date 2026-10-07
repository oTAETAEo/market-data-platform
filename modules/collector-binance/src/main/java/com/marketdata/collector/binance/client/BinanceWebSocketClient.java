package com.marketdata.collector.binance.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.net.URI;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    private final BinanceSubscriptionFactory subscriptionFactory;
    private final BinanceMessageHandler messageHandler;

    @PostConstruct
    public void connect() {
        String streamUrl = subscriptionFactory.streamUrl();
        log.info("Binance WebSocket 연결을 시작합니다.");

        connectToStream(streamUrl);
    }

    private void connectToStream(String streamUrl) {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(streamUrl), this::receiveMessages)
                .retryWhen(reconnectBackoff())
                .subscribe(
                        null,
                        error -> log.error("Binance WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Binance WebSocket 연결이 종료되었습니다"));
    }

    private Mono<Void> receiveMessages(WebSocketSession session) {
        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(messageHandler::handle)
                .then();
    }

    private RetryBackoffSpec reconnectBackoff() {
        return Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10));
    }
}
