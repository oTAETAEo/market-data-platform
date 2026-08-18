package com.marketdata.core.event;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketTickEvent(
        String eventId,
        String providerEventId,
        String provider,
        String venue,
        String assetClass,
        String symbol,
        BigDecimal price,     // 체결가
        BigDecimal quantity,  // 체결 수량
        Instant eventTime,    // 공급자 기준 이벤트 시각
        Instant receivedAt,   // 수집기 수신 시각
        int schemaVersion
) {}
