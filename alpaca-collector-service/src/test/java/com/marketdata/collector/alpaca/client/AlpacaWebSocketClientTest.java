package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marketdata.collector.alpaca.config.AlpacaWebSocketProperties;
import com.marketdata.collector.alpaca.service.MarketDataProducer;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketTickEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlpacaWebSocketClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private MarketDataProducer producer;

    private AlpacaMessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        AlpacaWebSocketProperties properties = new AlpacaWebSocketProperties("wss://example.test", "key", "secret", "AAPL");
        AlpacaSubscriptionFactory subscriptionFactory = new AlpacaSubscriptionFactory(properties, objectMapper);
        AlpacaMessageMapper mapper = new AlpacaMessageMapper(objectMapper);
        messageHandler = new AlpacaMessageHandler(objectMapper, subscriptionFactory, mapper, producer);
    }

    @Test
    void normalizesTradeWithProviderTradeId() throws Exception {
        when(producer.sendTick(any())).thenReturn(Mono.empty());

        process("""
                {"T":"t","i":42,"S":"AAPL","p":210.15,"s":3,"t":"2026-08-18T12:00:01Z"}
                """);

        ArgumentCaptor<MarketTickEvent> captor = ArgumentCaptor.forClass(MarketTickEvent.class);
        verify(producer).sendTick(captor.capture());

        MarketTickEvent event = captor.getValue();
        assertThat(event.providerEventId()).isEqualTo("42");
        assertThat(event.venue()).isEqualTo("ALPACA_IEX");
        assertThat(event.symbol()).isEqualTo("AAPL");
        assertThat(event.price()).hasToString("210.15");
    }

    @Test
    void normalizesQuoteToBookTicker() throws Exception {
        when(producer.sendBookTicker(any())).thenReturn(Mono.empty());

        process("""
                {"T":"q","S":"AAPL","bp":210.10,"bs":2,"ap":210.20,"as":4,"t":"2026-08-18T12:00:02Z"}
                """);

        ArgumentCaptor<MarketBookTickerEvent> captor = ArgumentCaptor.forClass(MarketBookTickerEvent.class);
        verify(producer).sendBookTicker(captor.capture());

        MarketBookTickerEvent event = captor.getValue();
        assertThat(event.symbol()).isEqualTo("AAPL");
        assertThat(event.bidPrice()).isEqualByComparingTo("210.10");
        assertThat(event.askPrice()).isEqualByComparingTo("210.20");
    }

    @Test
    void normalizesUpdatedBarAsClosedOneMinuteCandle() throws Exception {
        when(producer.sendCandle(any())).thenReturn(Mono.empty());

        process("""
                {"T":"u","S":"AAPL","o":210.00,"h":211.00,"l":209.80,"c":210.50,"v":100,"t":"2026-08-18T12:00:00Z"}
                """);

        ArgumentCaptor<MarketCandleEvent> captor = ArgumentCaptor.forClass(MarketCandleEvent.class);
        verify(producer).sendCandle(captor.capture());

        MarketCandleEvent event = captor.getValue();
        assertThat(event.interval()).isEqualTo("1m");
        assertThat(event.closed()).isTrue();
        assertThat(event.openTime()).isEqualTo(Instant.parse("2026-08-18T12:00:00Z"));
        assertThat(event.closeTime()).isEqualTo(Instant.parse("2026-08-18T12:00:59.999Z"));
        assertThat(event.eventTime()).isEqualTo(event.closeTime());
    }

    private void process(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        messageHandler.processMessage(node, sink, new AtomicBoolean(false)).block();
    }
}
