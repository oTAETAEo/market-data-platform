package com.marketdata.collector.alpaca.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlpacaQuoteMessage(
        @JsonProperty("S") String symbol,
        @JsonProperty("bp") BigDecimal bidPrice,
        @JsonProperty("bs") BigDecimal bidSize,
        @JsonProperty("ap") BigDecimal askPrice,
        @JsonProperty("as") BigDecimal askSize,
        @JsonProperty("t") Instant timestamp
) {
}
