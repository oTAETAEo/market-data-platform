package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import com.marketdata.collector.binance.dto.BinanceBookTickerMessage;
import com.marketdata.collector.binance.dto.BinanceDepthMessage;
import com.marketdata.collector.binance.dto.BinanceKlineMessage;
import com.marketdata.collector.binance.dto.BinanceTradeMessage;
import com.marketdata.collector.binance.service.MarketDataProducer;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketOrderBookEvent;
import com.marketdata.core.event.MarketOrderBookLevel;
import com.marketdata.core.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    private static final String PROVIDER = "BINANCE";
    private static final String VENUE = "BINANCE_SPOT";
    private static final String ASSET_CLASS = "CRYPTO";
    private static final String WEBSOCKET_SOURCE = "binance.websocket";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private final BinanceWebSocketProperties webSocketProperties;
    private final MarketDataProducer producer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void connect() {

        String combinedStreamUrl = webSocketProperties.combinedStreamUrl();

        log.info("Binance WebSocket 연결을 시작합니다.");

        WebSocketClient client = new ReactorNettyWebSocketClient();
        client.execute(URI.create(combinedStreamUrl), session ->
                        session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .concatMap(this::processMessage)
                                .then()
                ).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10)))
                .subscribe(
                        null,
                        error -> log.error("Binance WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Binance WebSocket 연결이 종료되었습니다")
                );
    }

    private Mono<Void> processMessage(String jsonPayload) {
        Instant receivedAt = Instant.now();
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            JsonNode data = root.has("data") ? root.path("data") : root;
            String stream = root.has("stream") ? root.path("stream").asText() : data.path("e").asText();

            if (stream.endsWith("@trade") || "trade".equals(data.path("e").asText())) {
                return processTrade(data);
            }
            if (stream.endsWith("@bookTicker")) {
                return processBookTicker(data);
            }
            if (stream.contains("@depth")) {
                return processDepth(data, stream, receivedAt);
            }
            if (stream.contains("@kline_") || "kline".equals(data.path("e").asText())) {
                return processKline(data);
            }

            log.debug("처리 대상이 아닌 Binance WebSocket 메시지를 무시했습니다: {}", jsonPayload);
            return Mono.empty();
        } catch (Exception e) {
            log.error("Binance WebSocket 메시지 파싱에 실패했습니다: {}", jsonPayload, e);
            return producer.sendDlq(new MarketDlqEvent(
                    UUID.randomUUID().toString(),
                    PROVIDER,
                    VENUE,
                    ASSET_CLASS,
                    WEBSOCKET_SOURCE,
                    PARSE_FAILED,
                    jsonPayload,
                    e.getMessage(),
                    receivedAt,
                    SCHEMA_VERSION
            ));
        }
    }

    private Mono<Void> processTrade(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceTradeMessage trade = objectMapper.convertValue(data, BinanceTradeMessage.class);
        MarketTickEvent event = new MarketTickEvent(
                UUID.randomUUID().toString(),
                String.valueOf(trade.tradeId()),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                trade.symbol(),
                trade.price(),
                trade.quantity(),
                Instant.ofEpochMilli(trade.tradeTime()),
                receivedAt,
                SCHEMA_VERSION
        );
        return producer.sendTick(event);
    }

    private Mono<Void> processBookTicker(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceBookTickerMessage bookTicker = objectMapper.convertValue(data, BinanceBookTickerMessage.class);
        MarketBookTickerEvent event = new MarketBookTickerEvent(
                UUID.randomUUID().toString(),
                String.valueOf(bookTicker.updateId()),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                bookTicker.symbol(),
                bookTicker.bidPrice(),
                bookTicker.bidQuantity(),
                bookTicker.askPrice(),
                bookTicker.askQuantity(),
                receivedAt,
                receivedAt,
                SCHEMA_VERSION
        );
        return producer.sendBookTicker(event);
    }

    private Mono<Void> processDepth(JsonNode data, String stream, Instant receivedAt) {
        BinanceDepthMessage depth = objectMapper.convertValue(data, BinanceDepthMessage.class);
        String symbol = symbolFromStream(stream);
        MarketOrderBookEvent event = new MarketOrderBookEvent(
                UUID.randomUUID().toString(),
                symbol + ":" + depth.lastUpdateId(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                symbol,
                webSocketProperties.getDepthLevels(),
                toOrderBookLevels(depth.bids()),
                toOrderBookLevels(depth.asks()),
                receivedAt,
                receivedAt,
                SCHEMA_VERSION
        );
        return producer.sendOrderBook(event);
    }

    private Mono<Void> processKline(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceKlineMessage klineMessage = objectMapper.convertValue(data, BinanceKlineMessage.class);
        BinanceKlineMessage.Kline kline = klineMessage.kline();
        MarketCandleEvent event = new MarketCandleEvent(
                UUID.randomUUID().toString(),
                kline.symbol() + ":" + kline.interval() + ":" + kline.openTime(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                kline.symbol(),
                kline.interval(),
                kline.open(),
                kline.high(),
                kline.low(),
                kline.close(),
                kline.volume(),
                Instant.ofEpochMilli(kline.openTime()),
                Instant.ofEpochMilli(kline.closeTime()),
                kline.closed(),
                Instant.ofEpochMilli(klineMessage.eventTime()),
                receivedAt,
                SCHEMA_VERSION
        );
        return producer.sendCandle(event);
    }

    private List<MarketOrderBookLevel> toOrderBookLevels(List<List<String>> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(this::toOrderBookLevel)
                .toList();
    }

    private MarketOrderBookLevel toOrderBookLevel(List<String> level) {
        if (level == null || level.size() < 2) {
            throw new IllegalArgumentException("Binance depth 호가 데이터는 가격과 수량을 포함해야 합니다.");
        }
        return new MarketOrderBookLevel(
                new BigDecimal(level.get(0)),
                new BigDecimal(level.get(1))
        );
    }

    private String symbolFromStream(String stream) {
        int separatorIndex = stream.indexOf('@');
        if (separatorIndex <= 0) {
            throw new IllegalArgumentException("Binance depth stream에서 심볼을 추출할 수 없습니다.");
        }
        return stream.substring(0, separatorIndex).toUpperCase(Locale.ROOT);
    }
}