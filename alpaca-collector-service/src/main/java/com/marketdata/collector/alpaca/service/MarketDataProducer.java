package com.marketdata.collector.alpaca.service;

import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataProducer {
    private static final String TRADE_TOPIC = "market.trade.v1";
    private static final String QUOTE_TOPIC = "market.quote.v1";
    private static final String BAR_TOPIC = "market.bar.v1";
    private static final String DLQ_TOPIC = "market.dlq.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTick(MarketTickEvent event) {
        send(TRADE_TOPIC, event.symbol(), event, "trade");
    }

    public void sendBookTicker(MarketBookTickerEvent event) {
        send(QUOTE_TOPIC, event.symbol(), event, "quote");
    }

    public void sendCandle(MarketCandleEvent event) {
        send(BAR_TOPIC, event.symbol(), event, "bar");
    }

    public void sendDlq(MarketDlqEvent event) {
        send(DLQ_TOPIC, event.eventId(), event, "dlq");
    }

    private void send(String topic, String key, Object event, String eventType) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, error) -> {
            if (error == null) {
                log.debug("Alpaca {} event sent: topic={}, key={}", eventType, topic, key);
            } else {
                log.error("Alpaca {} event send failed: topic={}, key={}", eventType, topic, key, error);
            }
        });
    }
}
