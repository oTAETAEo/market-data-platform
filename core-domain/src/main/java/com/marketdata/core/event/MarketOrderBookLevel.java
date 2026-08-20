package com.marketdata.core.event;

import java.math.BigDecimal;

public record MarketOrderBookLevel(
        BigDecimal price,
        BigDecimal quantity
) {
}
