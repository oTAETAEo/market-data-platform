package com.marketdata.core.kafka;

public final class MarketTopics {

    public static final String TRADE = "market.trade.v1";
    public static final String QUOTE = "market.quote.v1";
    public static final String BAR = "market.bar.v1";
    public static final String DEPTH = "market.depth.v1";
    public static final String DLQ = "market.dlq.v1";

    private MarketTopics() {
    }
}
