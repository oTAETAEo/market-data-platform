package com.marketdata.collector.binance.service;

import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketOrderBookEvent;
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
        return send(MarketTopics.TRADE, event.symbol(), event, "TRADE");
    }

    public Mono<Void> sendBookTicker(MarketBookTickerEvent event) {
        return send(MarketTopics.QUOTE, event.symbol(), event, "QUOTE");
    }

    public Mono<Void> sendCandle(MarketCandleEvent event) {
        return send(MarketTopics.BAR, event.symbol(), event, "BAR");
    }

    public Mono<Void> sendOrderBook(MarketOrderBookEvent event) {
        return send(MarketTopics.DEPTH, event.symbol(), event, "DEPTH");
    }

    public Mono<Void> sendDlq(MarketDlqEvent event) {
        return send(MarketTopics.DLQ, event.eventId(), event, "DLQ");
    }

    private Mono<Void> send(String topic, String key, Object event, String eventType) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, event);
        SenderRecord<String, Object, String> senderRecord = SenderRecord.create(producerRecord, key);

        return kafkaSender.send(Mono.just(senderRecord))
                .doOnNext(result -> {
                    if (result.exception() != null) {
                        log.error("Binance 이벤트 Kafka 전송에 실패했습니다: type={}, topic={}, key={}",
                                eventType, topic, key, result.exception());
                    } else {
                        log.debug("Binance {} event sent: topic={}, key={}, partition={}, offset={}",
                                eventType, topic, key,
                                result.recordMetadata().partition(),
                                result.recordMetadata().offset());
                    }
                })
                .doOnError(error -> log.error("Kafka 전송 파이프라인 에러: type={}, topic={}, key={}",
                        eventType, topic, key, error))
                .then();
    }
}
