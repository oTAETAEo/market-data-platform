package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.core.market.MarketInstrument;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BybitInstrumentClient {

    private static final int MAX_PAGES = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String restBaseUrl;

    public BybitInstrumentClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper(), "https://api.bybit.com");
    }

    BybitInstrumentClient(HttpClient httpClient, ObjectMapper objectMapper, String restBaseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.restBaseUrl = restBaseUrl;
    }

    public CompletableFuture<List<MarketInstrument>> fetchInstruments() {
        return fetchPage("", new LinkedHashMap<>(), new HashSet<>(), 0);
    }

    private CompletableFuture<List<MarketInstrument>> fetchPage(
            String cursor, Map<String, MarketInstrument> instruments, Set<String> cursors, int pageCount
    ) {
        String url = restBaseUrl + "/v5/market/instruments-info?category=linear&status=Trading&limit=1000";
        if (!cursor.isEmpty()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Bybit instruments HTTP " + response.statusCode());
            }
            JsonNode result = parseResult(response.body());
            for (JsonNode row : result.path("list")) {
                addInstrument(row, instruments);
            }
            String nextCursor = result.path("nextPageCursor").textValue();
            if (nextCursor.isEmpty()) {
                return CompletableFuture.completedFuture(List.copyOf(instruments.values()));
            }
            if (!cursors.add(nextCursor)) {
                throw new IllegalStateException("Bybit instruments cursor loop");
            }
            if (pageCount + 1 >= MAX_PAGES) {
                throw new IllegalStateException("Bybit instruments exceeded page limit");
            }
            return fetchPage(nextCursor, instruments, cursors, pageCount + 1);
        });
    }

    private JsonNode parseResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()
                    || !root.path("retCode").isIntegralNumber() || root.path("retCode").longValue() != 0) {
                throw new IllegalStateException("Invalid Bybit instruments API response");
            }
            JsonNode result = root.path("result");
            if (!result.isObject() || !"linear".equals(text(result, "category"))
                    || !result.path("list").isArray() || !result.path("nextPageCursor").isTextual()) {
                throw new IllegalStateException("Invalid Bybit instruments result");
            }
            return result;
        } catch (IOException error) {
            throw new IllegalStateException("Invalid Bybit instruments JSON", error);
        }
    }

    private static void addInstrument(JsonNode row, Map<String, MarketInstrument> instruments) {
        if (!row.isObject()) {
            throw new IllegalStateException("Invalid Bybit instrument row");
        }
        JsonNode preListing = row.path("isPreListing");
        if (!preListing.isMissingNode() && !preListing.isBoolean()) {
            throw new IllegalStateException("Invalid Bybit isPreListing");
        }
        String symbol = text(row, "symbol");
        String baseCoin = text(row, "baseCoin");
        if (!"Trading".equals(text(row, "status"))
                || !"LinearPerpetual".equals(text(row, "contractType"))
                || !"USDT".equals(text(row, "quoteCoin")) || !"USDT".equals(text(row, "settleCoin"))
                || preListing.asBoolean(false) || !text(row, "marketRegion").isBlank()
                || !text(row, "underlyingTicker").isBlank() || symbol.isBlank() || baseCoin.isBlank()) {
            return;
        }
        String displayName = text(row, "fullName");
        if (displayName.isBlank()) {
            displayName = baseCoin;
        }
        instruments.putIfAbsent(symbol, new MarketInstrument(symbol, baseCoin, "USDT", displayName));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue() : "";
    }
}
