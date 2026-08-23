package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.collector.binance.config.BinanceWebSocketProperties;
import com.marketdata.collector.binance.dto.BinanceBookTickerMessage;
import com.marketdata.collector.binance.dto.BinanceDepthMessage;
import com.marketdata.collector.binance.dto.BinanceKlineMessage;
import com.marketdata.collector.binance.dto.BinanceTradeMessage;
import com.marketdata.core.event.MarketBookTickerEvent;
import com.marketdata.core.event.MarketCandleEvent;
import com.marketdata.core.event.MarketDlqEvent;
import com.marketdata.core.event.MarketOrderBookEvent;
import com.marketdata.core.event.MarketOrderBookLevel;
import com.marketdata.core.event.MarketTickEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BinanceMessageMapper {

    private static final String PROVIDER = "BINANCE";
    private static final String VENUE = "BINANCE_SPOT";
    private static final String ASSET_CLASS = "CRYPTO";
    private static final String WEBSOCKET_SOURCE = "binance.websocket";
    private static final String PARSE_FAILED = "PARSE_FAILED";
    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final BinanceWebSocketProperties properties;

    public MarketTickEvent toTickEvent(JsonNode data, Instant receivedAt) {
        BinanceTradeMessage trade = objectMapper.convertValue(data, BinanceTradeMessage.class);
        return new MarketTickEvent(
                UUID.randomUUID().toString(),
                String.valueOf(trade.tradeId()),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                trade.symbol(),
                trade.price(),
                trade.quantity(),
                Instant.ofEpochMilli(trade.tradeTime()),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public MarketBookTickerEvent toBookTickerEvent(JsonNode data, Instant receivedAt) {
        BinanceBookTickerMessage bookTicker = objectMapper.convertValue(data, BinanceBookTickerMessage.class);
        return new MarketBookTickerEvent(
                UUID.randomUUID().toString(),
                String.valueOf(bookTicker.updateId()),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                bookTicker.symbol(),
                bookTicker.bidPrice(),
                bookTicker.bidQuantity(),
                bookTicker.askPrice(),
                bookTicker.askQuantity(),
                receivedAt,
                receivedAt,
                SCHEMA_VERSION
        );
    }

    public MarketOrderBookEvent toOrderBookEvent(JsonNode data, String stream, Instant receivedAt) {
        BinanceDepthMessage depth = objectMapper.convertValue(data, BinanceDepthMessage.class);
        String symbol = symbolFromStream(stream);
        return new MarketOrderBookEvent(
                UUID.randomUUID().toString(),
                symbol + ":" + depth.lastUpdateId(),
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                symbol,
                properties.getDepthLevels(),
                toOrderBookLevels(depth.bids()),
                toOrderBookLevels(depth.asks()),
                receivedAt,
                receivedAt,
                SCHEMA_VERSION
        );
    }

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

    private List<MarketOrderBookLevel> toOrderBookLevels(List<List<String>> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(this::toOrderBookLevel)
                .toList();
    }

    private MarketOrderBookLevel toOrderBookLevel(List<String> level) {
        if (level == null || level.size() < 2) {
            throw new IllegalArgumentException("Binance depth 호가 데이터는 가격과 수량을 포함해야 합니다.");
        }
        return new MarketOrderBookLevel(
                new BigDecimal(level.get(0)),
                new BigDecimal(level.get(1))
        );
    }

    private String symbolFromStream(String stream) {
        int separatorIndex = stream.indexOf('@');
        if (separatorIndex <= 0) {
            throw new IllegalArgumentException("Binance depth stream에서 심볼을 추출할 수 없습니다.");
        }
        return stream.substring(0, separatorIndex).toUpperCase(Locale.ROOT);
    }
}
