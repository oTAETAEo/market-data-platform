package com.marketdata.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketBookTickerEvent(
        String eventId,
        String providerEventId,
        String provider,
        String venue,
        String assetClass,
        String symbol,
        BigDecimal bidPrice,
        BigDecimal bidQuantity,
        BigDecimal askPrice,
        BigDecimal askQuantity,
        Instant eventTime,
        Instant receivedAt,
        int schemaVersion
) {
}
