package com.marketdata.engine;

import java.time.Instant;

public record MarketStatus(String connection, int candleCount, Instant lastReceivedAt, boolean analysisReady) {
    public static MarketStatus empty() {
        return new MarketStatus("STOPPED", 0, null, false);
    }
}
