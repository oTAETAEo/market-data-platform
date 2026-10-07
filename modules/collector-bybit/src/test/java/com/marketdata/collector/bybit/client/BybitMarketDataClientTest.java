package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketdata.core.event.MarketCandleEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BybitMarketDataClientTest {

    private final List<MarketCandleEvent> events = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private HttpServer server;
    private BybitMarketDataClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        scheduler.shutdownNow();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void warmsUpBybitRestCandlesOldestFirstWithLimit300() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = server("""
                {
                  "retCode":0,
                  "result":{
                    "list":[
                      ["1700000060000","1.5","2.5","1.0","2.0","11.0","22.0"],
                      ["1700000000000","1.0","2.0","0.5","1.5","10.0","15.0"]
                    ]
                  }
                }
                """, query);
        client = client("http://127.0.0.1:" + server.getAddress().getPort(), "ws://127.0.0.1:1/ws");

        int count = client.start("ethusdt", "5m").get(2, TimeUnit.SECONDS);

        assertThat(count).isEqualTo(2);
        assertThat(query.get()).contains("category=linear", "symbol=ETHUSDT", "interval=5", "limit=300");
        assertThat(statuses).containsExactly("CONNECTING");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).venue()).isEqualTo("BYBIT_LINEAR");
        assertThat(events.get(0).symbol()).isEqualTo("ETHUSDT");
        assertThat(events.get(0).interval()).isEqualTo("5m");
        assertThat(events.get(0).openTime()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        assertThat(events.get(1).openTime()).isEqualTo(Instant.ofEpochMilli(1700000060000L));
    }

    @Test
    void rejectsUnsupportedInputBeforeOpeningCollection() {
        client = client("http://127.0.0.1:1", "ws://127.0.0.1:1/ws");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.start("BTC-USDT", "1m"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.start("BTCUSDT", "30m"));
        assertThat(statuses).isEmpty();
    }

    @Test
    void handlesFragmentedWebSocketMessagesAndSuppressesAfterClose() throws Exception {
        server = server("{\"retCode\":0,\"result\":{\"list\":[]}}", new AtomicReference<>());
        client = client("http://127.0.0.1:" + server.getAddress().getPort(), "ws://127.0.0.1:1/ws");
        client.start("BTCUSDT", "1h").get(2, TimeUnit.SECONDS);
        long runId = client.currentRunId();

        client.handleWebSocketText("""
                {"topic":"kline.60.BTCUSDT","type":"snapshot","ts":1700000123456,"data":[{"start":1700000100000,"end":1700003699999,"interval":"60","open":"1.0","close":"1.2","high":"1.3","low":"0.9","volume":"12.0","confirm":false
                """, false, runId);
        assertThat(events).isEmpty();
        client.handleWebSocketText(",\"timestamp\":1700000123456}]}", true, runId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).interval()).isEqualTo("1h");
        assertThat(events.get(0).closed()).isFalse();
        client.close();
        client.handleWebSocketText("""
                {"topic":"kline.60.BTCUSDT","ts":1700000123456,"data":[{"start":1700000100000,"end":1700003699999,"interval":"60","open":"1.0","close":"1.2","high":"1.3","low":"0.9","volume":"12.0","confirm":true}]}
                """, true, runId);
        assertThat(events).hasSize(1);
        assertThat(statuses).contains("STOPPED");
    }

    private BybitMarketDataClient client(String restUrl, String wsUrl) {
        return new BybitMarketDataClient(
                events::add,
                statuses::add,
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                scheduler,
                restUrl,
                wsUrl
        );
    }

    private HttpServer server(String body, AtomicReference<String> query) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v5/market/kline", exchange -> {
            URI uri = exchange.getRequestURI();
            query.set(uri.getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }
}
