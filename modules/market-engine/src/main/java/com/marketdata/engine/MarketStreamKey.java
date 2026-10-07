package com.marketdata.engine;

import java.util.Locale;

public record MarketStreamKey(String exchange, String symbol, String timeframe) {
    public MarketStreamKey {
        exchange = normalize(exchange);
        symbol = normalize(symbol);
        timeframe = timeframe == null ? "" : timeframe.trim();
        if (exchange.isBlank() || symbol.isBlank() || timeframe.isBlank()) {
            throw new IllegalArgumentException("exchange, symbol and timeframe are required");
        }
    }

    public static MarketStreamKey ofEvent(String provider, String symbol, String interval) {
        return new MarketStreamKey(provider, symbol, interval);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
