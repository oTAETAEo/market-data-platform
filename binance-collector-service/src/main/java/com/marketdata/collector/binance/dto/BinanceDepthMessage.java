package com.marketdata.collector.binance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceDepthMessage(
        @JsonProperty("lastUpdateId") long lastUpdateId,
        @JsonProperty("bids") List<List<String>> bids,
        @JsonProperty("asks") List<List<String>> asks
) {
}
