package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.binance.dto.BinanceBookTickerMessage;
import com.marketdata.collector.binance.dto.BinanceKlineMessage;
import com.marketdata.collector.binance.dto.BinanceTradeMessage;
import com.marketdata.collector.binance.service.MarketDataProducer;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWebSocketClient {

    @Value("${binance.websocket.url}")
    private String binanceWsUrl;

    private final MarketDataProducer producer;

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void connect() {

        WebSocketClient client = new ReactorNettyWebSocketClient();

        client.execute(URI.create(binanceWsUrl), session ->
                        session.receive()
                                .map(WebSocketMessage::getPayloadAsText)
                                .doOnNext(this::processMessage)
                                .then()
                ).retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10)))
                .subscribe(
                        null,
                        error -> log.error("Binance WebSocket 오류가 발생했습니다", error),
                        () -> log.info("Binance WebSocket 연결이 종료되었습니다")
                );
    }

    private void processMessage(String jsonPayload) {
        Instant receivedAt = Instant.now();
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            JsonNode data = root.has("data") ? root.path("data") : root;
            String stream = root.has("stream") ? root.path("stream").asText() : data.path("e").asText();

            if (stream.endsWith("@trade") || "trade".equals(data.path("e").asText())) {
                processTrade(data);
                return;
            }

            if (stream.endsWith("@bookTicker")) {
                processBookTicker(data);
                return;
            }

            if (stream.contains("@kline_") || "kline".equals(data.path("e").asText())) {
                processKline(data);
                return;
            }

            log.debug("처리 대상이 아닌 Binance WebSocket 메시지를 무시했습니다: {}", jsonPayload);
        } catch (Exception e) {
            log.error("Binance WebSocket 메시지 파싱에 실패했습니다: {}", jsonPayload, e);
            producer.sendDlq(new MarketDlqEvent(
                    UUID.randomUUID().toString(),
                    "BINANCE",
                    "BINANCE_SPOT",
                    "CRYPTO",
                    "binance.websocket",
                    "PARSE_FAILED",
                    jsonPayload,
                    e.getMessage(),
                    receivedAt,
                    1
            ));
        }
    }

    private void processTrade(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceTradeMessage trade = objectMapper.convertValue(data, BinanceTradeMessage.class);
        MarketTickEvent event = new MarketTickEvent(
                UUID.randomUUID().toString(),
                String.valueOf(trade.tradeId()),
                "BINANCE",
                "BINANCE_SPOT",
                "CRYPTO",
                trade.symbol(),
                trade.price(),
                trade.quantity(),
                Instant.ofEpochMilli(trade.tradeTime()),
                receivedAt,
                1
        );

        producer.sendTick(event);
    }

    private void processBookTicker(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceBookTickerMessage bookTicker = objectMapper.convertValue(data, BinanceBookTickerMessage.class);
        MarketBookTickerEvent event = new MarketBookTickerEvent(
                UUID.randomUUID().toString(),
                String.valueOf(bookTicker.updateId()),
                "BINANCE",
                "BINANCE_SPOT",
                "CRYPTO",
                bookTicker.symbol(),
                bookTicker.bidPrice(),
                bookTicker.bidQuantity(),
                bookTicker.askPrice(),
                bookTicker.askQuantity(),
                receivedAt,
                receivedAt,
                1
        );

        producer.sendBookTicker(event);
    }

    private void processKline(JsonNode data) {
        Instant receivedAt = Instant.now();
        BinanceKlineMessage klineMessage = objectMapper.convertValue(data, BinanceKlineMessage.class);
        BinanceKlineMessage.Kline kline = klineMessage.kline();
        MarketCandleEvent event = new MarketCandleEvent(
                UUID.randomUUID().toString(),
                kline.symbol() + ":" + kline.interval() + ":" + kline.openTime(),
                "BINANCE",
                "BINANCE_SPOT",
                "CRYPTO",
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
                Instant.ofEpochMilli(kline.closeTime()),
                receivedAt,
                1
        );

        producer.sendCandle(event);
    }
}
