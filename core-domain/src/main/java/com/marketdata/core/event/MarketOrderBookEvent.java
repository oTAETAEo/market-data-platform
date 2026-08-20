package com.marketdata.core.event;

import java.time.Instant;
import java.util.List;

public record MarketOrderBookEvent(
        String eventId,
        String providerEventId,
        String provider,
        String venue,
        String assetClass,
        String symbol,
        int depthLevels,
        List<MarketOrderBookLevel> bids,
        List<MarketOrderBookLevel> asks,
        Instant eventTime,
        Instant receivedAt,
        int schemaVersion
) {
}
