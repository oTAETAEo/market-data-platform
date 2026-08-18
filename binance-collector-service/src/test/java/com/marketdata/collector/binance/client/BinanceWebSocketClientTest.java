package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketdata.collector.binance.service.MarketDataProducer;
import com.marketdata.core.event.MarketCandleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BinanceWebSocketClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private MarketDataProducer producer;

    private BinanceWebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new BinanceWebSocketClient(producer, objectMapper);
    }

    @Test
    void normalizesKlineWithProviderEventTime() {
        String eventTime = "2026-08-18T12:01:00.250Z";
        long eventTimeMillis = Instant.parse(eventTime).toEpochMilli();
        long openTimeMillis = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli();
        long closeTimeMillis = Instant.parse("2026-08-18T12:00:59.999Z").toEpochMilli();

        ReflectionTestUtils.invokeMethod(client, "processMessage", """
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
                """.formatted(eventTimeMillis, openTimeMillis, closeTimeMillis));

        ArgumentCaptor<MarketCandleEvent> captor = ArgumentCaptor.forClass(MarketCandleEvent.class);
        verify(producer).sendCandle(captor.capture());

        MarketCandleEvent event = captor.getValue();
        assertThat(event.openTime()).isEqualTo(Instant.ofEpochMilli(openTimeMillis));
        assertThat(event.closeTime()).isEqualTo(Instant.ofEpochMilli(closeTimeMillis));
        assertThat(event.eventTime()).isEqualTo(Instant.ofEpochMilli(eventTimeMillis));
        assertThat(event.eventTime()).isNotEqualTo(event.closeTime());
    }
}
