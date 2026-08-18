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

    private static final String TRADES_TOPIC = "market.trade.v1";
    private static final String QUOTES_TOPIC = "market.quote.v1";
    private static final String BARS_TOPIC = "market.bar.v1";
    private static final String DLQ_TOPIC = "market.dlq.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTick(MarketTickEvent event) {
        kafkaTemplate.send(TRADES_TOPIC, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Binance 체결 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Binance 체결 이벤트를 Kafka로 전송했습니다: symbol={}, price={}", event.symbol(), event.price());
                    }
                });
    }

    public void sendBookTicker(MarketBookTickerEvent event) {
        kafkaTemplate.send(QUOTES_TOPIC, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Binance 호가 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Binance 호가 이벤트를 Kafka로 전송했습니다: symbol={}, bid={}, ask={}",
                                event.symbol(), event.bidPrice(), event.askPrice());
                    }
                });
    }

    public void sendCandle(MarketCandleEvent event) {
        kafkaTemplate.send(BARS_TOPIC, event.symbol(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Binance 캔들 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Binance 캔들 이벤트를 Kafka로 전송했습니다: symbol={}, interval={}, close={}",
                                event.symbol(), event.interval(), event.close());
                    }
                });
    }

    public void sendDlq(MarketDlqEvent event) {
        kafkaTemplate.send(DLQ_TOPIC, event.eventId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Binance DLQ 이벤트 Kafka 전송에 실패했습니다: {}", event, ex);
                    } else {
                        log.debug("Binance DLQ 이벤트를 Kafka로 전송했습니다: reason={}", event.reason());
                    }
                });
    }
}
