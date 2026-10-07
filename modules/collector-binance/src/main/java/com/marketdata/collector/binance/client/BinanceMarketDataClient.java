package com.marketdata.collector.binance.client;

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
import java.util.Locale;
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

public class BinanceMarketDataClient implements AutoCloseable {

    private static final Set<String> SUPPORTED_INTERVALS = Set.of("1m", "5m", "15m", "1h", "4h");
    private static final int REST_LIMIT = 300;
    private static final int MAX_MESSAGE_CHARS = 1_048_576;
    private static final String REST_URL = "https://fapi.binance.com";
    private static final String WS_URL = "wss://fstream.binance.com/ws/";
    private static final String PROVIDER = "BINANCE";
    private static final String VENUE = "BINANCE_USDM_FUTURES";
    private static final String ASSET_CLASS = "CRYPTO_FUTURES";
    private static final int SCHEMA_VERSION = 1;

    private final Consumer<MarketCandleEvent> onCandle;
    private final Consumer<String> onStatus;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService reconnectExecutor;
    private final String restBaseUrl;
    private final String webSocketBaseUrl;
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

    public BinanceMarketDataClient(Consumer<MarketCandleEvent> onCandle, Consumer<String> onStatus) {
        this(onCandle, onStatus, HttpClient.newHttpClient(), new ObjectMapper(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "binance-market-reconnect");
                    thread.setDaemon(true);
                    return thread;
                }), REST_URL, WS_URL);
    }

    BinanceMarketDataClient(
            Consumer<MarketCandleEvent> onCandle,
            Consumer<String> onStatus,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            ScheduledExecutorService reconnectExecutor,
            String restBaseUrl,
            String webSocketBaseUrl
    ) {
        this.onCandle = onCandle;
        this.onStatus = onStatus;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.reconnectExecutor = reconnectExecutor;
        this.restBaseUrl = restBaseUrl;
        this.webSocketBaseUrl = webSocketBaseUrl;
    }

    public CompletableFuture<Integer> start(String symbol, String interval) {
        String normalizedSymbol = normalizeSymbol(symbol);
        String normalizedInterval = normalizeInterval(interval);
        stop(false);

        long runId = runIds.incrementAndGet();
        running.set(true);
        this.symbol = normalizedSymbol;
        this.interval = normalizedInterval;
        onStatus.accept("CONNECTING");

        HttpRequest httpRequest = HttpRequest.newBuilder(restUri(normalizedSymbol, normalizedInterval))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> currentRequest = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
        request = currentRequest;
        return currentRequest.thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException("Binance REST warmup failed");
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
        publishWebSocketCandle(message, runId);
    }

    long currentRunId() {
        return runIds.get();
    }

    private void openWebSocket(long runId) {
        URI uri = URI.create(webSocketBaseUrl + symbol.toLowerCase(Locale.ROOT) + "@kline_" + interval);
        openingSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new Listener(runId));
    }

    private int publishRestCandles(String body, String symbol, String interval, long runId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isObject() && root.has("code")) {
                throw new IllegalStateException("Binance REST warmup failed");
            }
            int count = 0;
            for (JsonNode row : root) {
                if (!isCurrent(runId)) {
                    return count;
                }
                onCandle.accept(newEvent(
                        symbol,
                        interval,
                        decimal(row.get(1)),
                        decimal(row.get(2)),
                        decimal(row.get(3)),
                        decimal(row.get(4)),
                        decimal(row.get(5)),
                        row.get(0).asLong(),
                        row.get(6).asLong(),
                        row.get(6).asLong() < Instant.now().toEpochMilli(),
                        row.get(6).asLong()
                ));
                count++;
            }
            return count;
        } catch (Exception error) {
            throw new IllegalStateException("Binance REST warmup failed", error);
        }
    }

    private void publishWebSocketCandle(String body, long runId) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.path("data") : root;
            JsonNode kline = data.path("k");
            if (!"kline".equals(data.path("e").asText()) || kline.isMissingNode()) {
                return;
            }
            if (isCurrent(runId)) {
                onCandle.accept(newEvent(
                        kline.path("s").asText(symbol),
                        kline.path("i").asText(interval),
                        decimal(kline.path("o")),
                        decimal(kline.path("h")),
                        decimal(kline.path("l")),
                        decimal(kline.path("c")),
                        decimal(kline.path("v")),
                        kline.path("t").asLong(),
                        kline.path("T").asLong(),
                        kline.path("x").asBoolean(false),
                        data.path("E").asLong(Instant.now().toEpochMilli())
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
        return URI.create(restBaseUrl + "/fapi/v1/klines?symbol=" + encode(symbol)
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
}
