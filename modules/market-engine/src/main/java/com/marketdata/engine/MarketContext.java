package com.marketdata.engine;

import com.marketdata.core.event.MarketCandleEvent;

import java.time.Instant;
import java.util.List;

public record MarketContext(
        String exchange,
        String symbol,
        String timeframe,
        int candleCount,
        Instant generatedAt,
        List<String> highlights,
        List<MarketCandleEvent> candles
) {
}
