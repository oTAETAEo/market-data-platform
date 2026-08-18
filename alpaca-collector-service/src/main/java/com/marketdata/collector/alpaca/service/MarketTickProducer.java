package com.marketdata.collector.alpaca.service;

import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketTickProducer {

    private static final String TOPIC = "market.trade.v1";
    private static final String DLQ_TOPIC = "market.dlq.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(MarketTickEvent event) {
        kafkaTemplate.send(TOPIC, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Alpaca 시세 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Alpaca 시세 이벤트를 Kafka로 전송했습니다: symbol={}, price={}", event.symbol(), event.price());
                    }
                });
    }

    public void sendDlq(MarketDlqEvent event) {
        kafkaTemplate.send(DLQ_TOPIC, event.eventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Alpaca DLQ 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Alpaca DLQ 이벤트를 Kafka로 전송했습니다: reason={}", event.reason());
                    }
                });
    }
}
