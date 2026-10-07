package com.marketdata.engine;

import com.marketdata.core.event.MarketCandleEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketEngineTest {
    @Test
    void upsertsCandlesKeepsClosedFinalAndBoundsSnapshot() {
        MarketEngine engine = new MarketEngine();
        engine.updateConnection("BINANCE", "BTCUSDT", "1m", "LIVE");
        for (int index = 0; index < 510; index++) {
            engine.accept(candle(index, false, index, "1"));
            engine.accept(candle(index, true, index + 1, "2"));
            engine.accept(candle(index, false, index + 2, "3"));
        }
        MarketContext context = engine.currentContext("BINANCE", "BTCUSDT", "1m");
        assertThat(context.candles()).hasSize(500).allMatch(MarketCandleEvent::closed);
        assertThat(context.candles().getFirst().openTime()).isEqualTo(Instant.ofEpochSecond(600));
        assertThat(context.candles().getLast().close()).isEqualByComparingTo("2");
        assertThat(engine.status("BINANCE", "BTCUSDT", "1m"))
                .extracting(MarketStatus::connection, MarketStatus::candleCount, MarketStatus::analysisReady)
                .containsExactly("LIVE", 500, true);
    }

    @Test
    void ignoresOlderSameStateUpdateAndRequiresWarmup() {
        MarketEngine engine = new MarketEngine();
        engine.accept(candle(0, false, 20, "2"));
        engine.accept(candle(0, false, 10, "1"));
        assertThat(engine.status("BINANCE", "BTCUSDT", "1m").candleCount()).isEqualTo(1);
        assertThatThrownBy(() -> engine.currentContext("BINANCE", "BTCUSDT", "1m"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("200개");
    }

    private MarketCandleEvent candle(int index, boolean closed, long eventOffset, String close) {
        Instant openTime = Instant.ofEpochSecond(index * 60L);
        return new MarketCandleEvent("event-" + index + "-" + eventOffset, "provider-" + index,
                "BINANCE", "BINANCE_USDM_FUTURES", "CRYPTO_FUTURES", "BTCUSDT", "1m",
                new BigDecimal("1"), new BigDecimal("3"), new BigDecimal("0.5"),
                new BigDecimal(close), new BigDecimal("10"), openTime, openTime.plusSeconds(59),
                closed, openTime.plusSeconds(eventOffset), openTime.plusSeconds(eventOffset), 1);
    }
}
