package com.marketdata.collector.alpaca.service;

import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import com.marketdata.core.kafka.MarketTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataProducer {
    private final KafkaSender<String, Object> kafkaSender;

    public Mono<Void> sendTick(MarketTickEvent event) {
        return send(MarketTopics.TRADE, event.symbol(), event, "trade");
    }

    public Mono<Void> sendBookTicker(MarketBookTickerEvent event) {
        return send(MarketTopics.QUOTE, event.symbol(), event, "quote");
    }

    public Mono<Void> sendCandle(MarketCandleEvent event) {
        return send(MarketTopics.BAR, event.symbol(), event, "bar");
    }

    public Mono<Void> sendDlq(MarketDlqEvent event) {
        return send(MarketTopics.DLQ, event.eventId(), event, "dlq");
    }

    private Mono<Void> send(String topic, String key, Object event, String eventType) {

        // ProducerRecord 생성
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

        // SenderRecord 생성
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(record, key);

        return kafkaSender.send(Mono.just(senderRecord))
                .doOnNext(result -> {
                    if (result.exception() != null) {
                        log.error("Alpaca {} event send failed: topic={}, key={}",
                                eventType, topic, result.correlationMetadata(), result.exception());
                    } else {
                        log.debug("Alpaca {} event sent: topic={}, key={}",
                                eventType, topic, result.correlationMetadata());
                    }
                })
                .then(); // Mono<SenderResult<String>> -> Mono<Void> 변환
    }
}
