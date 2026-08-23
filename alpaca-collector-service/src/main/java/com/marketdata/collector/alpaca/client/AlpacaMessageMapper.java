package com.marketdata.collector.alpaca.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.alpaca.dto.AlpacaBarMessage;
import com.marketdata.collector.alpaca.dto.AlpacaQuoteMessage;
import com.marketdata.collector.alpaca.dto.AlpacaTradeMessage;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AlpacaMessageMapper {

    private static final String PROVIDER = "ALPACA";
    private static final String VENUE = "ALPACA_IEX";
    private static final String ASSET_CLASS = "STOCK";
    private static final String WEBSOCKET_SOURCE = "alpaca.websocket";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final String ONE_MINUTE = "1m";
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public MarketTickEvent toTickEvent(JsonNode message, Instant receivedAt) {
        AlpacaTradeMessage trade = objectMapper.convertValue(message, AlpacaTradeMessage.class);
        return new MarketTickEvent(
                UUID.randomUUID().toString(),
                String.valueOf(trade.tradeId()),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                trade.symbol(),
                trade.price(),
                trade.size(),
                trade.timestamp(),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public MarketBookTickerEvent toBookTickerEvent(JsonNode message, Instant receivedAt) {
        AlpacaQuoteMessage quote = objectMapper.convertValue(message, AlpacaQuoteMessage.class);
        String providerEventId = quote.symbol() + ":" + quote.timestamp() + ":" + quote.bidPrice() + ":" + quote.askPrice();
        return new MarketBookTickerEvent(
                UUID.randomUUID().toString(),
                providerEventId,
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                quote.symbol(),
                quote.bidPrice(),
                quote.bidSize(),
                quote.askPrice(),
                quote.askSize(),
                quote.timestamp(),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public MarketCandleEvent toCandleEvent(JsonNode message, Instant receivedAt) {
        AlpacaBarMessage bar = objectMapper.convertValue(message, AlpacaBarMessage.class);
        Instant openTime = bar.openTime();
        Instant closeTime = openTime.plus(1, ChronoUnit.MINUTES).minusMillis(1);
        String providerEventId = bar.symbol() + ":" + ONE_MINUTE + ":" + openTime.toEpochMilli();
        return new MarketCandleEvent(
                UUID.randomUUID().toString(),
                providerEventId,
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                bar.symbol(),
                ONE_MINUTE,
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume(),
                openTime,
                closeTime,
                true,
                closeTime,
                receivedAt,
                SCHEMA_VERSION
        );
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
