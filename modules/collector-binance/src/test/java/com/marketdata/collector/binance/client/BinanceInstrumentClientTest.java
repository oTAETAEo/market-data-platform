package com.marketdata.collector.binance.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketdata.core.market.MarketInstrument;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinanceInstrumentClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void filtersContractsPreservesActualSymbolsAndReturnsImmutableDeduplicatedList() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.putArray("symbols")
                .add(instrument("1000SHIBUSDT", "1000SHIB"))
                .add(instrument("BTCUSDT", "BTC"))
                .add(instrument("1000SHIBUSDT", "different duplicate base"))
                .add(instrument("PAUSEDUSDT", "PAUSED").put("status", "BREAK"))
                .add(instrument("BTCUSDT_261225", "BTC").put("contractType", "CURRENT_QUARTER"))
                .add(instrument("ETHUSDC", "ETH").put("quoteAsset", "USDC"))
                .add(instrument("ETHUSDT", "ETH").put("marginAsset", "USDC"))
                .add(instrument("", "EMPTY"))
                .add(instrument("BLANKUSDT", " "))
                .add(instrument("MISSINGUSDT", "MISSING").without("baseAsset"))
                .add(instrument("NUMERICUSDT", "NUMERIC").put("baseAsset", 123));
        BinanceInstrumentClient client = client(200, body.toString());

        List<MarketInstrument> instruments = client.fetchInstruments().get(2, TimeUnit.SECONDS);

        assertThat(instruments).containsExactly(
                new MarketInstrument("1000SHIBUSDT", "1000SHIB", "USDT", "1000SHIB"),
                new MarketInstrument("BTCUSDT", "BTC", "USDT", "BTC"));
        assertThatThrownBy(() -> instruments.add(instruments.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsAnExplicitlyEmptySymbolsArray() throws Exception {
        assertThat(client(200, "{\"symbols\":[]}").fetchInstruments().get(2, TimeUnit.SECONDS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not json", "null", "[]", "{}", "{\"symbols\":null}",
            "{\"symbols\":{}}", "{\"symbols\":\"invalid\"}",
            "{\"symbols\":[null]}", "{\"symbols\":[1]}",
            "{\"code\":-1121,\"msg\":\"error\",\"symbols\":[]}"
    })
    void rejectsMalformedSchemasAndApiErrors(String body) throws Exception {
        BinanceInstrumentClient client = client(200, body);

        assertThatThrownBy(() -> client.fetchInstruments().get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 429, 500})
    void rejectsUnsuccessfulHttpEvenWithValidBody(int status) throws Exception {
        BinanceInstrumentClient client = client(status, "{\"symbols\":[]}");

        assertThatThrownBy(() -> client.fetchInstruments().get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("Binance instruments HTTP " + status);
    }

    private ObjectNode instrument(String symbol, String baseAsset) {
        return mapper.createObjectNode().put("symbol", symbol).put("baseAsset", baseAsset)
                .put("quoteAsset", "USDT").put("marginAsset", "USDT")
                .put("status", "TRADING").put("contractType", "PERPETUAL");
    }

    private BinanceInstrumentClient client(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fapi/v1/exchangeInfo", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new BinanceInstrumentClient(HttpClient.newHttpClient(), mapper,
                "http://127.0.0.1:" + server.getAddress().getPort());
    }
}
