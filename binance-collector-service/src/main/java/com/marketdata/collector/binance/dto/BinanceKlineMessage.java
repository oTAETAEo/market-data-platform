package com.marketdata.collector.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record BinanceKlineMessage(
        @JsonProperty("e") String eventType,
        @JsonProperty("E") Long eventTime,
        @JsonProperty("s") String symbol,
        @JsonProperty("k") Kline kline
) {

    public record Kline(
            @JsonProperty("t") Long openTime,
            @JsonProperty("T") Long closeTime,
            @JsonProperty("s") String symbol,
            @JsonProperty("i") String interval,
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("c") BigDecimal close,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("v") BigDecimal volume,
            @JsonProperty("x") boolean closed
    ) {
    }
}
