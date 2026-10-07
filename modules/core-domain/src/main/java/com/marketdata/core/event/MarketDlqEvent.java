package com.marketdata.core.event;

import java.time.Instant;

public record MarketDlqEvent(
        String eventId,
        String provider,
        String venue,
        String assetClass,
        String sourceTopic,
        String reason,
        String rawPayload,
        String errorMessage,
        Instant receivedAt,
        int schemaVersion
) {
}
