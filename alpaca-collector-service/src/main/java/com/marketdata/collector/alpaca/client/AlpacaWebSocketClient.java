package com.marketdata.collector.alpaca.client;

import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaWebSocketClient {

    private final AlpacaWebSocketProperties properties;
    private final AlpacaSubscriptionFactory subscriptionFactory;
    private final AlpacaMessageHandler messageHandler;

    @PostConstruct
    public void connect() {
        log.info("Alpaca WebSocket 연결을 시작합니다.");
        connectToStream(properties.url());
    }

    private void connectToStream(String streamUrl) {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(streamUrl), this::openSession)
                .retryWhen(reconnectBackoff())
                .subscribe(
                        null,
                        error -> log.error("Alpaca WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Alpaca WebSocket 연결이 종료되었습니다"));
    }

    private Mono<Void> openSession(WebSocketSession session) {
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean subscribed = new AtomicBoolean(false);
        messageHandler.emit(outbound, subscriptionFactory.authMessage(), "auth");

        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> messageHandler.handle(payload, outbound, subscribed))
                .then()
                .doFinally(signal -> outbound.tryEmitComplete());

        Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
        return send.and(receive);
    }

    private RetryBackoffSpec reconnectBackoff() {
        return Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10));
    }
}
