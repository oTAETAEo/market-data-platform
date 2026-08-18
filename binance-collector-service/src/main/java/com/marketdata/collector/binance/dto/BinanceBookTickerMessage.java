package com.marketdata.collector.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record BinanceBookTickerMessage(
        @JsonProperty("u") Long updateId,
        @JsonProperty("s") String symbol,
        @JsonProperty("b") BigDecimal bidPrice,
        @JsonProperty("B") BigDecimal bidQuantity,
        @JsonProperty("a") BigDecimal askPrice,
        @JsonProperty("A") BigDecimal askQuantity
) {
}
