package com.marketdata.collector.binance.client;

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

class BinanceMarketDataClientTest {

    private final List<MarketCandleEvent> events = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private HttpServer server;
    private BinanceMarketDataClient client;

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
    void warmsUpRestCandlesWithCanonicalVenueAndLimit300() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = server("""
                [
                  [1700000000000,"1.0","2.0","0.5","1.5","10.0",1700000059999],
                  [1700000060000,"1.5","2.5","1.0","2.0","11.0",1700000119999]
                ]
                """, query);
        client = client("http://127.0.0.1:" + server.getAddress().getPort(), "ws://127.0.0.1:1/");

        int count = client.start("btcusdt", "1m").get(2, TimeUnit.SECONDS);

        assertThat(count).isEqualTo(2);
        assertThat(query.get()).contains("symbol=BTCUSDT", "interval=1m", "limit=300");
        assertThat(statuses).containsExactly("CONNECTING");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).venue()).isEqualTo("BINANCE_USDM_FUTURES");
        assertThat(events.get(0).symbol()).isEqualTo("BTCUSDT");
        assertThat(events.get(0).interval()).isEqualTo("1m");
        assertThat(events.get(0).closed()).isTrue();
        assertThat(events.get(0).eventTime()).isEqualTo(Instant.ofEpochMilli(1700000059999L));
    }

    @Test
    void rejectsUnsupportedInputBeforeOpeningCollection() {
        client = client("http://127.0.0.1:1", "ws://127.0.0.1:1/");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.start("BTC-USDT", "1m"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> client.start("BTCUSDT", "3m"));
        assertThat(statuses).isEmpty();
    }

    @Test
    void handlesFragmentedWebSocketMessagesAndSuppressesAfterClose() throws Exception {
        server = server("[]", new AtomicReference<>());
        client = client("http://127.0.0.1:" + server.getAddress().getPort(), "ws://127.0.0.1:1/");
        client.start("BTCUSDT", "15m").get(2, TimeUnit.SECONDS);
        long runId = client.currentRunId();

        client.handleWebSocketText("""
                {"e":"kline","E":1700000123456,"s":"BTCUSDT","k":{"t":1700000100000,"T":1700000999999,"s":"BTCUSDT","i":"15m","o":"1.0","c":"1.2","h":"1.3","l":"0.9","v":"12.0","x":false
                """, false, runId);
        assertThat(events).isEmpty();
        client.handleWebSocketText("}}", true, runId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).closed()).isFalse();
        client.close();
        client.handleWebSocketText("""
                {"e":"kline","E":1700000123456,"s":"BTCUSDT","k":{"t":1700000100000,"T":1700000999999,"s":"BTCUSDT","i":"15m","o":"1.0","c":"1.2","h":"1.3","l":"0.9","v":"12.0","x":true}}
                """, true, runId);
        assertThat(events).hasSize(1);
        assertThat(statuses).contains("STOPPED");
    }

    private BinanceMarketDataClient client(String restUrl, String wsUrl) {
        return new BinanceMarketDataClient(
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
        httpServer.createContext("/fapi/v1/klines", exchange -> {
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
