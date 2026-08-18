package com.marketdata.collector.alpaca.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record AlpacaTradeMessage(
        @JsonProperty("T") String messageType,
        @JsonProperty("S") String symbol,
        @JsonProperty("p") BigDecimal price,
        @JsonProperty("s") BigDecimal size,
        @JsonProperty("t") Instant timestamp
) {
    public boolean isTrade() {
        return "t".equals(messageType);
    }
}
