package com.marketdata.engine;

import com.marketdata.core.event.MarketCandleEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MarketEngine {
    private static final int MAX_CANDLES = 500;
    private static final int MIN_ANALYSIS_CANDLES = 200;

    private final Map<MarketStreamKey, CandleBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<MarketStreamKey, String> connections = new ConcurrentHashMap<>();

    public void accept(MarketCandleEvent event) {
        validate(event);
        MarketStreamKey key = MarketStreamKey.ofEvent(event.provider(), event.symbol(), event.interval());
        buffers.computeIfAbsent(key, ignored -> new CandleBuffer()).upsert(event);
    }

    public void updateConnection(String exchange, String symbol, String timeframe, String connection) {
        connections.put(new MarketStreamKey(exchange, symbol, timeframe), connection == null ? "ERROR" : connection);
    }

    public MarketStatus status(String exchange, String symbol, String timeframe) {
        MarketStreamKey key = new MarketStreamKey(exchange, symbol, timeframe);
        CandleBuffer buffer = buffers.get(key);
        List<MarketCandleEvent> candles = buffer == null ? List.of() : buffer.snapshot();
        Instant lastReceivedAt = candles.stream().map(MarketCandleEvent::receivedAt)
                .max(Comparator.naturalOrder()).orElse(null);
        long closedCount = candles.stream().filter(MarketCandleEvent::closed).count();
        return new MarketStatus(connections.getOrDefault(key, "STOPPED"), candles.size(), lastReceivedAt,
                closedCount >= MIN_ANALYSIS_CANDLES);
    }

    public MarketContext currentContext(String exchange, String symbol, String timeframe) {
        MarketStreamKey key = new MarketStreamKey(exchange, symbol, timeframe);
        CandleBuffer buffer = buffers.get(key);
        List<MarketCandleEvent> candles = buffer == null ? List.of() : buffer.snapshot();
        if (candles.stream().filter(MarketCandleEvent::closed).count() < MIN_ANALYSIS_CANDLES) {
            throw new IllegalStateException("분석에 필요한 확정 캔들이 부족합니다. 최소 200개가 필요합니다.");
        }
        MarketStatus status = status(exchange, symbol, timeframe);
        return new MarketContext(key.exchange(), key.symbol(), key.timeframe(), candles.size(), Instant.now(),
                List.of("실시간 선물 캔들 스냅샷", "연결 상태: " + status.connection(),
                        "마지막 수신: " + status.lastReceivedAt()), candles);
    }

    private void validate(MarketCandleEvent event) {
        if (event == null || event.provider() == null || event.symbol() == null || event.interval() == null
                || event.openTime() == null || event.closeTime() == null || event.eventTime() == null
                || event.receivedAt() == null
                || event.open() == null || event.high() == null || event.low() == null
                || event.close() == null || event.volume() == null) {
            throw new IllegalArgumentException("complete candle event is required");
        }
        if (event.high().compareTo(event.low()) < 0 || event.volume().signum() < 0
                || event.closeTime().isBefore(event.openTime())) {
            throw new IllegalArgumentException("invalid candle values");
        }
    }

    private static final class CandleBuffer {
        private final LinkedHashMap<Instant, MarketCandleEvent> candles = new LinkedHashMap<>();

        synchronized void upsert(MarketCandleEvent incoming) {
            MarketCandleEvent current = candles.get(incoming.openTime());
            if (current != null) {
                if (current.closed() && !incoming.closed()) return;
                if (current.eventTime() != null && incoming.eventTime() != null
                        && incoming.eventTime().isBefore(current.eventTime())
                        && current.closed() == incoming.closed()) return;
            }
            candles.put(incoming.openTime(), incoming);
            if (candles.size() > MAX_CANDLES) {
                Instant oldest = candles.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
                candles.remove(oldest);
            }
        }

        synchronized List<MarketCandleEvent> snapshot() {
            ArrayList<MarketCandleEvent> copy = new ArrayList<>(candles.values());
            copy.sort(Comparator.comparing(MarketCandleEvent::openTime));
            return List.copyOf(copy);
        }
    }
}
