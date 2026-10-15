package com.marketdata.collector.bybit.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketdata.core.market.MarketInstrument;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BybitInstrumentClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void filtersNonCryptoAndInactiveContractsPreservesSymbolsAndUsesFullName() throws Exception {
        String body = page("",
                instrument("1000PEPEUSDT", "1000PEPE").put("fullName", "Pepe"),
                instrument("BTCUSDT", "BTC").put("fullName", " "),
                instrument("1000PEPEUSDT", "duplicate").put("fullName", "duplicate"),
                instrument("PREUSDT", "PRE").put("status", "PreLaunch"),
                instrument("PENDINGUSDT", "PENDING").put("status", "PendingOpen"),
                instrument("CLOSEDUSDT", "CLOSED").put("status", "Closed"),
                instrument("AUCTIONUSDT", "AUCTION").put("isPreListing", true),
                instrument("BTCUSDT-261225", "BTC").put("contractType", "LinearFutures"),
                instrument("ETHUSDC", "ETH").put("quoteCoin", "USDC"),
                instrument("ETHUSDT", "ETH").put("settleCoin", "USDC"),
                instrument("STOCKUSDT", "STOCK").put("marketRegion", "US"),
                instrument("ETFUSDT", "ETF").put("underlyingTicker", "ETF"),
                instrument("", "EMPTY"), instrument("BLANKUSDT", " "),
                instrument("MISSINGUSDT", "MISSING").without("baseCoin"),
                instrument("NUMERICUSDT", "NUMERIC").put("symbol", 123));
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200, body));

        List<MarketInstrument> instruments = client.fetchInstruments().get(2, TimeUnit.SECONDS);

        assertThat(instruments).containsExactly(
                new MarketInstrument("1000PEPEUSDT", "1000PEPE", "USDT", "Pepe"),
                new MarketInstrument("BTCUSDT", "BTC", "USDT", "BTC"));
        assertThatThrownBy(() -> instruments.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fetchesSecondPageOnlyInstrumentEncodesOpaqueCursorAndDeduplicatesAcrossPages() throws Exception {
        String cursor = "first%3DBTC&last=ETH +/?";
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> firstQuery = new AtomicReference<>();
        AtomicReference<String> secondQuery = new AtomicReference<>();
        BybitInstrumentClient client = client(exchange -> {
            if (requests.incrementAndGet() == 1) {
                firstQuery.set(exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, page(cursor, instrument("BTCUSDT", "BTC")));
            } else {
                secondQuery.set(exchange.getRequestURI().getRawQuery());
                respond(exchange, 200, page("", instrument("BTCUSDT", "duplicate"),
                        instrument("1000BONKUSDT", "1000BONK")));
            }
        });

        List<MarketInstrument> instruments = client.fetchInstruments().get(3, TimeUnit.SECONDS);

        assertThat(instruments).containsExactly(
                new MarketInstrument("BTCUSDT", "BTC", "USDT", "BTC"),
                new MarketInstrument("1000BONKUSDT", "1000BONK", "USDT", "1000BONK"));
        assertThat(requests).hasValue(2);
        assertThat(firstQuery.get()).isEqualTo("category=linear&status=Trading&limit=1000");
        assertThat(secondQuery.get()).isEqualTo(firstQuery.get() + "&cursor="
                + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
    }

    @Test
    void continuesPaginationWhenFirstPageHasNoEligibleInstruments() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200,
                requests.incrementAndGet() == 1 ? page("next") : page("", instrument("ETHUSDT", "ETH"))));

        assertThat(client.fetchInstruments().get(3, TimeUnit.SECONDS))
                .containsExactly(new MarketInstrument("ETHUSDT", "ETH", "USDT", "ETH"));
        assertThat(requests).hasValue(2);
    }

    @Test
    void acceptsExplicitEmptyListAndCursor() throws Exception {
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200, page("")));

        assertThat(client.fetchInstruments().get(2, TimeUnit.SECONDS)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not json", "null", "[]", "{}",
            "{\"retCode\":10001,\"result\":{\"category\":\"linear\",\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"result\":{\"category\":\"linear\",\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":\"0\",\"result\":{\"category\":\"linear\",\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0.1,\"result\":{\"category\":\"linear\",\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":null}",
            "{\"retCode\":0,\"result\":{\"category\":\"spot\",\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"list\":[],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"list\":{},\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"list\":null,\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"list\":[null],\"nextPageCursor\":\"\"}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"list\":[]}}",
            "{\"retCode\":0,\"result\":{\"category\":\"linear\",\"list\":[],\"nextPageCursor\":123}}"
    })
    void rejectsMalformedSchemasAndApiErrors(String body) throws Exception {
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200, body));

        assertFailure(client);
    }

    @Test
    void rejectsMalformedPreListingFlag() throws Exception {
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200,
                page("", instrument("BTCUSDT", "BTC").put("isPreListing", "true"))));

        assertFailure(client);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 429, 500})
    void rejectsUnsuccessfulHttpEvenWithValidBody(int status) throws Exception {
        BybitInstrumentClient client = client(exchange -> respond(exchange, status, page("")));

        assertFailure(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http", "api", "schema", "json"})
    void laterPageFailureCannotReturnPartialSuccess(String failure) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        BybitInstrumentClient client = client(exchange -> {
            if (requests.incrementAndGet() == 1) {
                respond(exchange, 200, page("next", instrument("BTCUSDT", "BTC")));
            } else {
                switch (failure) {
                    case "http" -> respond(exchange, 503, page(""));
                    case "api" -> respond(exchange, 200, "{\"retCode\":10001}");
                    case "schema" -> respond(exchange, 200, "{\"retCode\":0,\"result\":{}}");
                    default -> respond(exchange, 200, "invalid json");
                }
            }
        });

        assertFailure(client);
        assertThat(requests).hasValue(2);
    }

    @Test
    void rejectsCursorCycleBeforeRequestingAPageAgain() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        BybitInstrumentClient client = client(exchange -> {
            int request = requests.incrementAndGet();
            respond(exchange, 200, page(request == 2 ? "second" : "first", instrument("BTCUSDT", "BTC")));
        });

        assertThatThrownBy(() -> client.fetchInstruments().get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasRootCauseMessage("Bybit instruments cursor loop");
        assertThat(requests).hasValue(3);
    }

    @Test
    void boundsPaginationEvenWhenEveryCursorIsDifferent() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        BybitInstrumentClient client = client(exchange -> respond(exchange, 200,
                page("cursor-" + requests.incrementAndGet())));

        assertThatThrownBy(() -> client.fetchInstruments().get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasRootCauseMessage("Bybit instruments exceeded page limit");
        assertThat(requests).hasValue(100);
    }

    private void assertFailure(BybitInstrumentClient client) {
        assertThatThrownBy(() -> client.fetchInstruments().get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
    }

    private ObjectNode instrument(String symbol, String baseCoin) {
        return mapper.createObjectNode().put("symbol", symbol).put("baseCoin", baseCoin)
                .put("quoteCoin", "USDT").put("settleCoin", "USDT")
                .put("status", "Trading").put("contractType", "LinearPerpetual").put("isPreListing", false);
    }

    private String page(String cursor, ObjectNode... instruments) {
        ObjectNode root = mapper.createObjectNode().put("retCode", 0);
        ObjectNode result = root.putObject("result").put("category", "linear").put("nextPageCursor", cursor);
        result.putArray("list").addAll(List.of(instruments));
        return root.toString();
    }

    private BybitInstrumentClient client(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v5/market/instruments-info", handler);
        server.start();
        return new BybitInstrumentClient(HttpClient.newHttpClient(), mapper,
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
