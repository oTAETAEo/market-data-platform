package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import com.marketdata.collector.binance.service.MarketDataProducer;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketOrderBookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketClientTest {

    private static final String BASE_URL = "wss://stream.binance.com:9443/stream";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private MarketDataProducer producer;

    private BinanceWebSocketProperties webSocketProperties;
    private BinanceSubscriptionFactory subscriptionFactory;
    private BinanceMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        webSocketProperties = new BinanceWebSocketProperties();
        webSocketProperties.setBaseUrl(BASE_URL);
        webSocketProperties.setSymbols("BTCUSDT");
        webSocketProperties.setKlineInterval("1m");
        webSocketProperties.setDepthLevels(20);
        webSocketProperties.setDepthUpdateInterval("100ms");
        subscriptionFactory = new BinanceSubscriptionFactory(webSocketProperties);
        BinanceMessageMapper mapper = new BinanceMessageMapper(objectMapper, webSocketProperties);
        messageHandler = new BinanceMessageHandler(objectMapper, mapper, producer);
    }

    @Test
    void createsCombinedStreamUrlForMultipleSymbolsIncludingDepth() {
        webSocketProperties.setSymbols("BTCUSDT, ethusdt, SOLUSDT, BTCUSDT");

        assertThat(subscriptionFactory.symbolList())
                .containsExactly("BTCUSDT", "ETHUSDT", "SOLUSDT");
        assertThat(subscriptionFactory.streamUrl()).isEqualTo(
                "wss://stream.binance.com:9443/stream?streams="
                        + "btcusdt@trade/btcusdt@bookTicker/btcusdt@kline_1m/btcusdt@depth20@100ms/"
                        + "ethusdt@trade/ethusdt@bookTicker/ethusdt@kline_1m/ethusdt@depth20@100ms/"
                        + "solusdt@trade/solusdt@bookTicker/solusdt@kline_1m/solusdt@depth20@100ms"
        );
    }

    @Test
    void rejectsEmptySymbolList() {
        webSocketProperties.setSymbols(" , ");

        assertThatIllegalArgumentException()
                .isThrownBy(subscriptionFactory::streamUrl)
                .withMessage("Binance 구독 심볼은 최소 1개 이상 설정해야 합니다.");
    }

    @Test
    void normalizesPartialDepthToOrderBookEvent() {
        when(producer.sendOrderBook(any())).thenReturn(Mono.empty());

        messageHandler.handle("""
                {
                  "stream":"btcusdt@depth20@100ms",
                  "data":{
                    "lastUpdateId":123456,
                    "bids":[["64000.10","0.500"],["64000.00","1.250"]],
                    "asks":[["64000.20","0.300"],["64000.30","0.700"]]
                  }
                }
                """).block();

        ArgumentCaptor<MarketOrderBookEvent> captor = ArgumentCaptor.forClass(MarketOrderBookEvent.class);
        verify(producer).sendOrderBook(captor.capture());

        MarketOrderBookEvent event = captor.getValue();
        assertThat(event.providerEventId()).isEqualTo("BTCUSDT:123456");
        assertThat(event.symbol()).isEqualTo("BTCUSDT");
        assertThat(event.depthLevels()).isEqualTo(20);
        assertThat(event.bids()).hasSize(2);
        assertThat(event.asks()).hasSize(2);
        assertThat(event.bids().getFirst().price()).isEqualByComparingTo(new BigDecimal("64000.10"));
        assertThat(event.bids().getFirst().quantity()).isEqualByComparingTo(new BigDecimal("0.500"));
        assertThat(event.asks().getFirst().price()).isEqualByComparingTo(new BigDecimal("64000.20"));
        assertThat(event.eventTime()).isEqualTo(event.receivedAt());
    }

    @Test
    void normalizesKlineWithProviderEventTime() {
        String eventTime = "2026-08-18T12:01:00.250Z";
        long eventTimeMillis = Instant.parse(eventTime).toEpochMilli();
        long openTimeMillis = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli();
        long closeTimeMillis = Instant.parse("2026-08-18T12:00:59.999Z").toEpochMilli();
        when(producer.sendCandle(any())).thenReturn(Mono.empty());

        messageHandler.handle("""
                {
                  "e":"kline",
                  "E":%d,
                  "s":"BTCUSDT",
                  "k":{
                    "t":%d,
                    "T":%d,
                    "s":"BTCUSDT",
                    "i":"1m",
                    "o":"64000.00",
                    "c":"64010.00",
                    "h":"64020.00",
                    "l":"63990.00",
                    "v":"12.50",
                    "x":true
                  }
                }
                """.formatted(eventTimeMillis, openTimeMillis, closeTimeMillis)).block();

        ArgumentCaptor<MarketCandleEvent> captor = ArgumentCaptor.forClass(MarketCandleEvent.class);
        verify(producer).sendCandle(captor.capture());

        MarketCandleEvent event = captor.getValue();
        assertThat(event.openTime()).isEqualTo(Instant.ofEpochMilli(openTimeMillis));
        assertThat(event.closeTime()).isEqualTo(Instant.ofEpochMilli(closeTimeMillis));
        assertThat(event.eventTime()).isEqualTo(Instant.ofEpochMilli(eventTimeMillis));
        assertThat(event.eventTime()).isNotEqualTo(event.closeTime());
    }
}
