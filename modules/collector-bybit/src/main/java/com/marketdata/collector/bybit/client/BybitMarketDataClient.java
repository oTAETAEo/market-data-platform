package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.core.event.MarketCandleEvent;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BybitMarketDataClient implements AutoCloseable {

    private static final Map<String, String> INTERVALS = Map.of(
            "1m", "1",
            "5m", "5",
            "15m", "15",
            "1h", "60",
            "4h", "240"
    );
    private static final Set<String> SUPPORTED_INTERVALS = INTERVALS.keySet();
    private static final int REST_LIMIT = 300;
    private static final int MAX_MESSAGE_CHARS = 1_048_576;
    private static final String REST_URL = "https://api.bybit.com";
    private static final String WS_URL = "wss://stream.bybit.com/v5/public/linear";
    private static final String PROVIDER = "BYBIT";
    private static final String VENUE = "BYBIT_LINEAR";
    private static final String ASSET_CLASS = "CRYPTO_FUTURES";
    private static final int SCHEMA_VERSION = 1;

    private final Consumer<MarketCandleEvent> onCandle;
    private final Consumer<String> onStatus;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectExecutor;
    private final String restBaseUrl;
    private final String webSocketUrl;
    private final AtomicLong runIds = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Object lock = new Object();
    private final StringBuilder partialMessage = new StringBuilder();

    private volatile CompletableFuture<HttpResponse<String>> request;
    private volatile CompletableFuture<WebSocket> openingSocket;
    private volatile ScheduledFuture<?> reconnectTask;
    private volatile WebSocket socket;
    private volatile String symbol;
    private volatile String interval;
    private volatile String bybitInterval;

    public BybitMarketDataClient(Consumer<MarketCandleEvent> onCandle, Consumer<String> onStatus) {
        this(onCandle, onStatus, HttpClient.newHttpClient(), new ObjectMapper(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "bybit-market-reconnect");
                    thread.setDaemon(true);
                    return thread;
                }), REST_URL, WS_URL);
    }

    BybitMarketDataClient(
            Consumer<MarketCandleEvent> onCandle,
            Consumer<String> onStatus,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ScheduledExecutorService reconnectExecutor,
            String restBaseUrl,
            String webSocketUrl
    ) {
        this.onCandle = onCandle;
        this.onStatus = onStatus;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.reconnectExecutor = reconnectExecutor;
        this.restBaseUrl = restBaseUrl;
        this.webSocketUrl = webSocketUrl;
    }

    public CompletableFuture<Integer> start(String symbol, String interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedInterval = normalizeInterval(interval);
        String normalizedBybitInterval = INTERVALS.get(normalizedInterval);
        stop(false);

        long runId = runIds.incrementAndGet();
        running.set(true);
        this.symbol = normalizedSymbol;
        this.interval = normalizedInterval;
        this.bybitInterval = normalizedBybitInterval;
        onStatus.accept("CONNECTING");

        HttpRequest httpRequest = HttpRequest.newBuilder(restUri(normalizedSymbol, normalizedBybitInterval))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> currentRequest = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        request = currentRequest;
        return currentRequest.thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException("Bybit REST warmup failed");
                    }
                    return response.body();
                })
                .thenApply(body -> publishRestCandles(body, normalizedSymbol, normalizedInterval, runId))
                .whenComplete((count, error) -> {
                    if (request == currentRequest) {
                        request = null;
                    }
                    if (!isCurrent(runId)) {
                        return;
                    }
                    if (error != null) {
                        onStatus.accept("ERROR");
                    } else {
                        openWebSocket(runId);
                    }
                });
    }

    @Override
    public void close() {
        stop(true);
        reconnectExecutor.shutdownNow();
    }

    private void stop(boolean publishStopped) {
        running.set(false);
        runIds.incrementAndGet();
        cancelReconnect();
        CompletableFuture<HttpResponse<String>> currentRequest = request;
        if (currentRequest != null && !currentRequest.isDone()) {
            currentRequest.cancel(true);
        }
        CompletableFuture<WebSocket> currentOpeningSocket = openingSocket;
        if (currentOpeningSocket != null) {
            currentOpeningSocket.cancel(true);
        }
        WebSocket currentSocket = socket;
        if (currentSocket != null) {
            currentSocket.sendClose(WebSocket.NORMAL_CLOSURE, "closing");
            currentSocket.abort();
        }
        synchronized (lock) {
            partialMessage.setLength(0);
        }
        if (publishStopped) {
            onStatus.accept("STOPPED");
        }
    }

    void handleWebSocketText(CharSequence data, boolean last, long runId) {
        if (!isCurrent(runId)) {
            return;
        }
        String message;
        synchronized (lock) {
            if (partialMessage.length() + data.length() > MAX_MESSAGE_CHARS) {
                partialMessage.setLength(0);
                onStatus.accept("ERROR");
                return;
            }
            partialMessage.append(data);
            if (!last) {
                return;
            }
            message = partialMessage.toString();
            partialMessage.setLength(0);
        }
        publishWebSocketCandles(message, runId);
    }

    long currentRunId() {
        return runIds.get();
    }

    private void openWebSocket(long runId) {
        openingSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(webSocketUrl), new Listener(runId));
    }

    private int publishRestCandles(String body, String symbol, String interval, long runId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("retCode").asInt(0) != 0) {
                throw new IllegalStateException("Bybit REST warmup failed");
            }
            JsonNode rows = root.path("result").path("list");
            List<JsonNode> chronological = new ArrayList<>();
            rows.forEach(chronological::add);
            Collections.reverse(chronological);
            int count = 0;
            for (JsonNode row : chronological) {
                if (!isCurrent(runId)) {
                    return count;
                }
                long openTime = row.get(0).asLong();
                long closeTime = closeTime(openTime, interval);
                onCandle.accept(newEvent(
                        symbol,
                        interval,
                        decimal(row.get(1)),
                        decimal(row.get(2)),
                        decimal(row.get(3)),
                        decimal(row.get(4)),
                        decimal(row.get(5)),
                        openTime,
                        closeTime,
                        closeTime < Instant.now().toEpochMilli(),
                        closeTime
                ));
                count++;
            }
            return count;
        } catch (Exception error) {
            throw new IllegalStateException("Bybit REST warmup failed", error);
        }
    }

    private void publishWebSocketCandles(String body, long runId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String topic = root.path("topic").asText();
            if (!topic.startsWith("kline.")) {
                return;
            }
            long fallbackEventTime = root.path("ts").asLong(Instant.now().toEpochMilli());
            for (JsonNode data : root.path("data")) {
                if (!isCurrent(runId)) {
                    return;
                }
                long openTime = data.path("start").asLong();
                long end = data.path("end").asLong(closeTime(openTime, interval));
                long eventTime = data.path("timestamp").asLong(fallbackEventTime);
                onCandle.accept(newEvent(
                        symbolFromTopic(topic),
                        normalizeIncomingInterval(data.path("interval").asText()),
                        decimal(data.path("open")),
                        decimal(data.path("high")),
                        decimal(data.path("low")),
                        decimal(data.path("close")),
                        decimal(data.path("volume")),
                        openTime,
                        end,
                        data.path("confirm").asBoolean(false),
                        eventTime
                ));
            }
        } catch (Exception error) {
            onStatus.accept("ERROR");
        }
    }

    private MarketCandleEvent newEvent(
            String symbol,
            String interval,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            long openTime,
            long closeTime,
            boolean closed,
            long eventTime
    ) {
        Instant receivedAt = Instant.now();
        return new MarketCandleEvent(
                UUID.randomUUID().toString(),
                symbol + ":" + interval + ":" + openTime,
                PROVIDER,
                VENUE,
                ASSET_CLASS,
                symbol,
                interval,
                open,
                high,
                low,
                close,
                volume,
                Instant.ofEpochMilli(openTime),
                Instant.ofEpochMilli(closeTime),
                closed,
                Instant.ofEpochMilli(eventTime),
                receivedAt,
                SCHEMA_VERSION
        );
    }

    private URI restUri(String symbol, String interval) {
        return URI.create(restBaseUrl + "/v5/market/kline?category=linear&symbol=" + encode(symbol)
                + "&interval=" + encode(interval)
                + "&limit=" + REST_LIMIT);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("symbol must be an alphanumeric USDT ticker");
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]+USDT")) {
            throw new IllegalArgumentException("symbol must be an alphanumeric USDT ticker");
        }
        return normalized;
    }

    private String normalizeInterval(String interval) {
        if (interval == null || !SUPPORTED_INTERVALS.contains(interval.trim())) {
            throw new IllegalArgumentException("interval must be one of 1m, 5m, 15m, 1h, 4h");
        }
        return interval.trim();
    }

    private String normalizeIncomingInterval(String incoming) {
        return INTERVALS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(incoming))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(interval);
    }

    private String symbolFromTopic(String topic) {
        String[] parts = topic.split("\\.");
        return parts.length >= 3 ? parts[2].toUpperCase(Locale.ROOT) : symbol;
    }

    private long closeTime(long openTime, String interval) {
        return openTime + switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            default -> 60_000L;
        } - 1;
    }

    private void scheduleReconnect(long runId) {
        if (!isCurrent(runId)) {
            return;
        }
        onStatus.accept("RECONNECTING");
        cancelReconnect();
        reconnectTask = reconnectExecutor.schedule(() -> {
            if (isCurrent(runId)) {
                openWebSocket(runId);
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        ScheduledFuture<?> task = reconnectTask;
        if (task != null) {
            task.cancel(true);
        }
    }

    private boolean isCurrent(long runId) {
        return running.get() && runIds.get() == runId;
    }

    private BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private final class Listener implements WebSocket.Listener {
        private final long runId;

        private Listener(long runId) {
            this.runId = runId;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            if (isCurrent(runId)) {
                onStatus.accept("LIVE");
                webSocket.sendText(subscriptionMessage(), true);
                webSocket.request(1);
            } else {
                webSocket.abort();
            }
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            handleWebSocketText(data, last, runId);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (isCurrent(runId)) {
                scheduleReconnect(runId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (isCurrent(runId)) {
                scheduleReconnect(runId);
            }
        }
    }

    private String subscriptionMessage() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "op", "subscribe",
                    "args", List.of("kline." + bybitInterval + "." + symbol)
            ));
        } catch (Exception error) {
            throw new IllegalStateException("Bybit subscription failed", error);
        }
    }
}
