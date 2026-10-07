package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketdata.collector.bybit.config.BybitWebSocketProperties;
import com.marketdata.collector.bybit.service.MarketDataProducer;
import com.marketdata.core.event.MarketCandleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BybitWebSocketClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private MarketDataProducer producer;

    private BybitWebSocketProperties webSocketProperties;
    private BybitSubscriptionFactory subscriptionFactory;
    private BybitMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        webSocketProperties = new BybitWebSocketProperties();
        webSocketProperties.setSymbols("BTCUSDT");
        webSocketProperties.setKlineInterval("15");
        subscriptionFactory = new BybitSubscriptionFactory(webSocketProperties, objectMapper);
        BybitMessageMapper mapper = new BybitMessageMapper(objectMapper);
        messageHandler = new BybitMessageHandler(objectMapper, mapper, producer);
    }

    @Test
    void createsKlineSubscriptionForMultipleSymbols() {
        webSocketProperties.setSymbols("BTCUSDT, ethusdt, BTCUSDT");

        assertThat(subscriptionFactory.streamUrl()).isEqualTo("wss://stream.bybit.com/v5/public/linear");
        assertThat(subscriptionFactory.topics())
                .containsExactly("kline.15.BTCUSDT", "kline.15.ETHUSDT");
        assertThat(subscriptionFactory.subscriptionMessage())
                .contains("\"op\":\"subscribe\"")
                .contains("\"kline.15.BTCUSDT\"")
                .contains("\"kline.15.ETHUSDT\"");
    }

    @Test
    void rejectsEmptySymbolList() {
        webSocketProperties.setSymbols(" , ");

        assertThatIllegalArgumentException()
                .isThrownBy(subscriptionFactory::topics)
                .withMessage("Bybit 구독 심볼은 최소 1개 이상 설정해야 합니다.");
    }

    @Test
    void normalizesKlineToMarketCandleEvent() {
        long eventTimeMillis = Instant.parse("2026-08-18T12:01:00.250Z").toEpochMilli();
        long openTimeMillis = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli();
        long closeTimeMillis = Instant.parse("2026-08-18T12:14:59.999Z").toEpochMilli();
        when(producer.sendCandle(any(), any())).thenReturn(Mono.empty());

        messageHandler.handle("""
                {
                  "topic":"kline.15.BTCUSDT",
                  "type":"snapshot",
                  "ts":%d,
                  "data":[{
                    "start":%d,
                    "end":%d,
                    "interval":"15",
                    "open":"64000.00",
                    "close":"64010.00",
                    "high":"64020.00",
                    "low":"63990.00",
                    "volume":"12.50",
                    "confirm":true,
                    "timestamp":%d
                  }]
                }
                """.formatted(eventTimeMillis, openTimeMillis, closeTimeMillis, eventTimeMillis)).block();

        ArgumentCaptor<MarketCandleEvent> captor = ArgumentCaptor.forClass(MarketCandleEvent.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendCandle(captor.capture(), keyCaptor.capture());

        MarketCandleEvent event = captor.getValue();
        assertThat(event.provider()).isEqualTo("BYBIT");
        assertThat(event.venue()).isEqualTo("BYBIT_LINEAR");
        assertThat(event.assetClass()).isEqualTo("CRYPTO_FUTURES");
        assertThat(event.symbol()).isEqualTo("BTCUSDT");
        assertThat(event.interval()).isEqualTo("15m");
        assertThat(event.openTime()).isEqualTo(Instant.ofEpochMilli(openTimeMillis));
        assertThat(event.closeTime()).isEqualTo(Instant.ofEpochMilli(closeTimeMillis));
        assertThat(event.eventTime()).isEqualTo(Instant.ofEpochMilli(eventTimeMillis));
        assertThat(event.closed()).isTrue();
        assertThat(keyCaptor.getValue()).isEqualTo("BYBIT_LINEAR:BTCUSDT:15m");
    }
}
