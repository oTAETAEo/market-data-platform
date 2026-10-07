package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import com.marketdata.collector.binance.service.MarketDataProducer;
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
class BinanceWebSocketClientTest {

    private static final String BASE_URL = "wss://fstream.binance.com/public/stream";

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
        webSocketProperties.setKlineInterval("15m");
        subscriptionFactory = new BinanceSubscriptionFactory(webSocketProperties);
        BinanceMessageMapper mapper = new BinanceMessageMapper(objectMapper);
        messageHandler = new BinanceMessageHandler(objectMapper, mapper, producer);
    }

    @Test
    void createsCombinedStreamUrlForMultipleFuturesKlineSymbols() {
        webSocketProperties.setSymbols("BTCUSDT, ethusdt, SOLUSDT, BTCUSDT");

        assertThat(subscriptionFactory.symbolList())
                .containsExactly("BTCUSDT", "ETHUSDT", "SOLUSDT");
        assertThat(subscriptionFactory.streamUrl()).isEqualTo(
                "wss://fstream.binance.com/public/stream?streams="
                        + "btcusdt@kline_15m/"
                        + "ethusdt@kline_15m/"
                        + "solusdt@kline_15m"
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
    void normalizesKlineWithProviderEventTime() {
        String eventTime = "2026-08-18T12:01:00.250Z";
        long eventTimeMillis = Instant.parse(eventTime).toEpochMilli();
        long openTimeMillis = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli();
        long closeTimeMillis = Instant.parse("2026-08-18T12:00:59.999Z").toEpochMilli();
        when(producer.sendCandle(any(), any())).thenReturn(Mono.empty());

        messageHandler.handle("""
                {
                  "stream":"btcusdt@kline_15m",
                  "data":{
                  "e":"kline",
                  "E":%d,
                  "s":"BTCUSDT",
                  "k":{
                    "t":%d,
                    "T":%d,
                    "s":"BTCUSDT",
                    "i":"15m",
                    "o":"64000.00",
                    "c":"64010.00",
                    "h":"64020.00",
                    "l":"63990.00",
                    "v":"12.50",
                    "x":true
                  }
                  }
                }
                """.formatted(eventTimeMillis, openTimeMillis, closeTimeMillis)).block();

        ArgumentCaptor<MarketCandleEvent> captor = ArgumentCaptor.forClass(MarketCandleEvent.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(producer).sendCandle(captor.capture(), keyCaptor.capture());

        MarketCandleEvent event = captor.getValue();
        assertThat(event.provider()).isEqualTo("BINANCE");
        assertThat(event.venue()).isEqualTo("BINANCE_USDM_FUTURES");
        assertThat(event.assetClass()).isEqualTo("CRYPTO_FUTURES");
        assertThat(event.interval()).isEqualTo("15m");
        assertThat(event.openTime()).isEqualTo(Instant.ofEpochMilli(openTimeMillis));
        assertThat(event.closeTime()).isEqualTo(Instant.ofEpochMilli(closeTimeMillis));
        assertThat(event.eventTime()).isEqualTo(Instant.ofEpochMilli(eventTimeMillis));
        assertThat(event.eventTime()).isNotEqualTo(event.closeTime());
        assertThat(keyCaptor.getValue()).isEqualTo("BINANCE_USDM_FUTURES:BTCUSDT:15m");
    }
}
