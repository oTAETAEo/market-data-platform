package com.marketdata.collector.bybit.client;

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
public class BybitWebSocketClient {

    private final BybitSubscriptionFactory subscriptionFactory;
    private final BybitMessageHandler messageHandler;

    @PostConstruct
    public void connect() {
        String streamUrl = subscriptionFactory.streamUrl();
        log.info("Bybit WebSocket 연결을 시작합니다.");

        connectToStream(streamUrl);
    }

    private void connectToStream(String streamUrl) {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(streamUrl), this::openSession)
                .retryWhen(reconnectBackoff())
                .subscribe(
                        null,
                        error -> log.error("Bybit WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Bybit WebSocket 연결이 종료되었습니다"));
    }

    private Mono<Void> openSession(WebSocketSession session) {
        Mono<Void> subscribe = session.send(Mono.just(session.textMessage(subscriptionFactory.subscriptionMessage())));
        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .concatMap(messageHandler::handle)
                .then();

        return subscribe.then(receive);
    }

    private RetryBackoffSpec reconnectBackoff() {
        return Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10));
    }
}
