package com.marketdata.collector.binance.service;

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
        send(TRADE_TOPIC, event.symbol(), event, "TRADE");
    }

    public void sendBookTicker(MarketBookTickerEvent event) {
        send(QUOTE_TOPIC, event.symbol(), event, "QUOTE");
    }

    public void sendCandle(MarketCandleEvent event) {
        send(BAR_TOPIC, event.symbol(), event, "BAR");
    }

    public void sendDlq(MarketDlqEvent event) {
        send(DLQ_TOPIC, event.eventId(), event, "DLQ");
    }

    private void send(String topic, String key, Object event, String eventType) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, error) -> {
            if (error == null) {
                log.debug("Binance {} event sent: topic={}, key={}", eventType, topic, key);
            } else {
                log.error("Binance 이벤트 Kafka 전송에 실패했습니다: type={}, topic={}, key={}",
                        eventType, topic, key, error);
            }
        });
    }
}
