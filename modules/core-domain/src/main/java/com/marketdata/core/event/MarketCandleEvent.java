package com.marketdata.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketCandleEvent(
        String eventId,
        String providerEventId,
        String provider,
        String venue,
        String assetClass,
        String symbol,
        String interval,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        Instant openTime,
        Instant closeTime,
        boolean closed,
        Instant eventTime,
        Instant receivedAt,
        int schemaVersion
) {
}
