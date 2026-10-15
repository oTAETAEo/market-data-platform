package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.core.market.MarketInstrument;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BinanceInstrumentClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String restBaseUrl;

    public BinanceInstrumentClient() {
        this(HttpClient.newHttpClient(), new ObjectMapper(), "https://fapi.binance.com");
    }

    BinanceInstrumentClient(HttpClient httpClient, ObjectMapper objectMapper, String restBaseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.restBaseUrl = restBaseUrl;
    }

    public CompletableFuture<List<MarketInstrument>> fetchInstruments() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(restBaseUrl + "/fapi/v1/exchangeInfo"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Binance instruments HTTP " + response.statusCode());
            }
            return parseInstruments(response.body());
        });
    }

    private List<MarketInstrument> parseInstruments(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject() || root.has("code") || !root.path("symbols").isArray()) {
                throw new IllegalStateException("Invalid Binance instruments response");
            }
            Map<String, MarketInstrument> instruments = new LinkedHashMap<>();
            for (JsonNode row : root.path("symbols")) {
                if (!row.isObject()) {
                    throw new IllegalStateException("Invalid Binance instrument row");
                }
                String symbol = text(row, "symbol");
                String baseAsset = text(row, "baseAsset");
                if ("TRADING".equals(text(row, "status"))
                        && "PERPETUAL".equals(text(row, "contractType"))
                        && "USDT".equals(text(row, "quoteAsset"))
                        && "USDT".equals(text(row, "marginAsset"))
                        && !symbol.isBlank() && !baseAsset.isBlank()) {
                    instruments.putIfAbsent(symbol, new MarketInstrument(symbol, baseAsset, "USDT", baseAsset));
                }
            }
            return List.copyOf(instruments.values());
        } catch (IOException error) {
            throw new IllegalStateException("Invalid Binance instruments JSON", error);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue() : "";
    }
}
