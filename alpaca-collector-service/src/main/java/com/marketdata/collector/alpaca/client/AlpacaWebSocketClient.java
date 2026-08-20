package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import com.marketdata.collector.alpaca.dto.AlpacaBarMessage;
import com.marketdata.collector.alpaca.dto.AlpacaQuoteMessage;
import com.marketdata.collector.alpaca.dto.AlpacaTradeMessage;
import com.marketdata.collector.alpaca.service.MarketDataProducer;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlpacaWebSocketClient {

    private static final String PROVIDER = "ALPACA";
    private static final String VENUE = "ALPACA_IEX";
    private static final String ASSET_CLASS = "STOCK";
    private static final String ONE_MINUTE = "1m";

    private final AlpacaWebSocketProperties properties;
    private final MarketDataProducer producer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void connect() {
        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(properties.url()), this::openSession)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10)))
                .subscribe(
                        null,
                        error -> log.error("Alpaca WebSocket error", error),
                        () -> log.info("Alpaca WebSocket closed")
                );
    }

    private Mono<Void> openSession(WebSocketSession session) {
        Sinks.Many<String> outbound = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean subscribed = new AtomicBoolean(false);
        emit(outbound, createAuthMessage(), "auth");

        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> processPayload(payload, outbound, subscribed))
                .then()
                .doFinally(signal -> outbound.tryEmitComplete());

        Mono<Void> send = session.send(outbound.asFlux().map(session::textMessage));
        return send.and(receive);
    }

    private Mono<Void> processPayload(String payload, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        try {
            JsonNode messages = objectMapper.readTree(payload);
            if (!messages.isArray()) {
                log.debug("Ignoring non-array Alpaca message: {}", payload);
                return Mono.empty();
            }
            return Flux.fromIterable(messages)
                    .flatMap(message -> processMessage(message, outbound, subscribed))
                    .then();
        } catch (Exception error) {
            return publishParseFailure(payload, error, Instant.now());
        }
    }

    private Mono<Void> processMessage(JsonNode message, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        Instant receivedAt = Instant.now();
        String type = message.path("T").asText();
        return switch (type) {
            case "success" -> processSuccess(message, outbound, subscribed);
            case "subscription" -> {
                log.info("Alpaca subscription confirmed: {}", message);
                yield Mono.empty();
            }
            case "error" -> {
                log.error("Alpaca stream error: {}", message);
                yield Mono.empty();
            }
            case "t" -> processTrade(message, receivedAt);
            case "q" -> processQuote(message, receivedAt);
            case "b", "u" -> processBar(message, receivedAt);
            default -> {
                log.debug("Ignoring Alpaca message type {}: {}", type, message);
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> processSuccess(JsonNode message, Sinks.Many<String> outbound, AtomicBoolean subscribed) {
        String status = message.path("msg").asText();
        log.info("Alpaca session status: {}", status);
        if ("authenticated".equals(status) && subscribed.compareAndSet(false, true)) {
            emit(outbound, createSubscribeMessage(), "subscribe");
        }
        return Mono.empty();
    }

    private Mono<Void> processTrade(JsonNode message, Instant receivedAt) {
        try {
            AlpacaTradeMessage trade = objectMapper.convertValue(message, AlpacaTradeMessage.class);
            MarketTickEvent event = new MarketTickEvent(
                    UUID.randomUUID().toString(),
                    String.valueOf(trade.tradeId()),
                    PROVIDER, VENUE, ASSET_CLASS, trade.symbol(),
                    trade.price(), trade.size(), trade.timestamp(), receivedAt, 1
            );
            return producer.sendTick(event);
        } catch (Exception error) {
            return publishParseFailure(message.toString(), error, receivedAt);
        }
    }

    private Mono<Void> processQuote(JsonNode message, Instant receivedAt) {
        try {
            AlpacaQuoteMessage quote = objectMapper.convertValue(message, AlpacaQuoteMessage.class);
            String providerEventId = quote.symbol() + ":" + quote.timestamp() + ":" + quote.bidPrice() + ":" + quote.askPrice();
            MarketBookTickerEvent event = new MarketBookTickerEvent(
                    UUID.randomUUID().toString(), providerEventId,
                    PROVIDER, VENUE, ASSET_CLASS, quote.symbol(),
                    quote.bidPrice(), quote.bidSize(), quote.askPrice(), quote.askSize(),
                    quote.timestamp(), receivedAt, 1
            );
            return producer.sendBookTicker(event);
        } catch (Exception error) {
            return publishParseFailure(message.toString(), error, receivedAt);
        }
    }

    private Mono<Void> processBar(JsonNode message, Instant receivedAt) {
        try {
            AlpacaBarMessage bar = objectMapper.convertValue(message, AlpacaBarMessage.class);
            Instant openTime = bar.openTime();
            Instant closeTime = openTime.plus(1, ChronoUnit.MINUTES).minusMillis(1);
            String providerEventId = bar.symbol() + ":" + ONE_MINUTE + ":" + openTime.toEpochMilli();
            MarketCandleEvent event = new MarketCandleEvent(
                    UUID.randomUUID().toString(), providerEventId,
                    PROVIDER, VENUE, ASSET_CLASS, bar.symbol(), ONE_MINUTE,
                    bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(),
                    openTime, closeTime, true, closeTime, receivedAt, 1
            );
            return producer.sendCandle(event);
        } catch (Exception error) {
            return publishParseFailure(message.toString(), error, receivedAt);
        }
    }

    private Mono<Void> publishParseFailure(String payload, Exception error, Instant receivedAt) {
        log.error("Alpaca payload parse failed: {}", payload, error);
        return producer.sendDlq(new MarketDlqEvent(
                UUID.randomUUID().toString(), PROVIDER, VENUE, ASSET_CLASS,
                "alpaca.websocket", "PARSE_FAILED", payload, error.getMessage(), receivedAt, 1
        ));
    }

    private String createAuthMessage() {
        return objectMapper.createObjectNode()
                .put("action", "auth")
                .put("key", properties.key())
                .put("secret", properties.secret())
                .toString();
    }

    private String createSubscribeMessage() {
        List<String> symbols = parseSymbols();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("action", "subscribe");
        request.set("trades", symbolArray(symbols));
        request.set("quotes", symbolArray(symbols));
        request.set("bars", symbolArray(symbols));
        request.set("updatedBars", symbolArray(symbols));
        return request.toString();
    }

    private List<String> parseSymbols() {
        if (properties.symbols() == null) {
            throw new IllegalStateException("alpaca.websocket.symbols must not be empty");
        }
        List<String> symbols = Arrays.stream(properties.symbols().split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .toList();
        if (symbols.isEmpty()) {
            throw new IllegalStateException("alpaca.websocket.symbols must not be empty");
        }
        return symbols;
    }

    private ArrayNode symbolArray(List<String> symbols) {
        ArrayNode array = objectMapper.createArrayNode();
        symbols.forEach(array::add);
        return array;
    }

    private void emit(Sinks.Many<String> outbound, String payload, String kind) {
        Sinks.EmitResult result = outbound.tryEmitNext(payload);
        if (result.isFailure()) {
            log.error("Alpaca {} queue failure: {}", kind, result);
        }
    }
}