package com.marketdata.collector.binance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BinanceTradeMessage(
        @JsonProperty("e") String eventType,
        @JsonProperty("E") Long eventTime,
        @JsonProperty("s") String symbol,
        @JsonProperty("t") Long tradeId,
        @JsonProperty("p") BigDecimal price,
        @JsonProperty("q") BigDecimal quantity,
        @JsonProperty("T") Long tradeTime
) {}