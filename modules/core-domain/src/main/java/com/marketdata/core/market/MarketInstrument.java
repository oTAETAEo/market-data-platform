package com.marketdata.core.market;

public record MarketInstrument(String symbol, String baseAsset, String quoteAsset, String displayName) {
}
