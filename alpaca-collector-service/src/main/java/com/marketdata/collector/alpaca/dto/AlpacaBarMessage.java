package com.marketdata.collector.alpaca.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaBarMessage(
        @JsonProperty("S") String symbol,
        @JsonProperty("o") BigDecimal open,
        @JsonProperty("h") BigDecimal high,
        @JsonProperty("l") BigDecimal low,
        @JsonProperty("c") BigDecimal close,
        @JsonProperty("v") BigDecimal volume,
        @JsonProperty("t") Instant openTime
) {
}
