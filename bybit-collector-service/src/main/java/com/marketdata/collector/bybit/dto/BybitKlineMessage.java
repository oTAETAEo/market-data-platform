package com.marketdata.collector.bybit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BybitKlineMessage(
        @JsonProperty("start") Long start,
        @JsonProperty("end") Long end,
        @JsonProperty("interval") String interval,
        @JsonProperty("open") BigDecimal open,
        @JsonProperty("close") BigDecimal close,
        @JsonProperty("high") BigDecimal high,
        @JsonProperty("low") BigDecimal low,
        @JsonProperty("volume") BigDecimal volume,
        @JsonProperty("confirm") boolean closed,
        @JsonProperty("timestamp") Long timestamp
) {
}
