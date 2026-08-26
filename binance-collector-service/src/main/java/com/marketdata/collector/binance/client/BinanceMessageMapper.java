package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.binance.dto.BinanceKlineMessage;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BinanceMessageMapper {

    private static final String PROVIDER = "BINANCE";
    public static final String VENUE = "BINANCE_USDM_FUTURES";
    private static final String ASSET_CLASS = "CRYPTO_FUTURES";
    private static final String WEBSOCKET_SOURCE = "binance.websocket";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public MarketCandleEvent toCandleEvent(JsonNode data, Instant receivedAt) {
        BinanceKlineMessage klineMessage = objectMapper.convertValue(data, BinanceKlineMessage.class);
        BinanceKlineMessage.Kline kline = klineMessage.kline();
        return new MarketCandleEvent(
                UUID.randomUUID().toString(),
                kline.symbol() + ":" + kline.interval() + ":" + kline.openTime(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                kline.symbol(),
                kline.interval(),
                kline.open(),
                kline.high(),
                kline.low(),
                kline.close(),
                kline.volume(),
                Instant.ofEpochMilli(kline.openTime()),
                Instant.ofEpochMilli(kline.closeTime()),
                kline.closed(),
                Instant.ofEpochMilli(klineMessage.eventTime()),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public String candleKey(MarketCandleEvent event) {
        return event.venue() + ":" + event.symbol() + ":" + event.interval();
    }

    public MarketDlqEvent toDlqEvent(String payload, Exception error, Instant receivedAt) {
        return new MarketDlqEvent(
                UUID.randomUUID().toString(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                WEBSOCKET_SOURCE,
                PARSE_FAILED,
                payload,
                error.getMessage(),
                receivedAt,
                SCHEMA_VERSION
        );
    }
}
