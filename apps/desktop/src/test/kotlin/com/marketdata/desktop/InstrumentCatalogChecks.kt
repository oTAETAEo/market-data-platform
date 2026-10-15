package com.marketdata.desktop

import com.marketdata.ai.StubTradingAgentsAdapter
import com.marketdata.core.market.MarketInstrument
import com.marketdata.desktop.application.AnalyzeMarketUseCase
import com.marketdata.desktop.application.InstrumentCatalog
import com.marketdata.desktop.model.MarketCatalog
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.engine.MarketEngine
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

fun main() = runBlocking {
    class CatalogClock(var now: Instant = Instant.EPOCH) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
    val clock = CatalogClock()
    val calls = mutableListOf<String>()
    val requests = ArrayDeque<CompletableFuture<List<MarketInstrument>>>()
    val catalog = InstrumentCatalog(fetch = { exchange ->
        calls.add(exchange)
        requests.removeFirst()
    }, clock = clock, ttl = Duration.ofMinutes(1))
    val state = WorkspaceViewModel(AnalyzeMarketUseCase(MarketEngine(), StubTradingAgentsAdapter()), instrumentCatalog = catalog)
    val watchlist = state.watchlist
    val ada = MarketInstrument("ADAUSDT", "ADA", "USDT", "Cardano")
    val pepe = MarketInstrument("1000PEPEUSDT", "PEPE", "USDT", "Pepe")
    val first = CompletableFuture<List<MarketInstrument>>()
    requests.add(first)
    val loading = launch { state.refreshInstrumentCatalog() }
    yield()
    state.updateQuery("ada")
    check(state.catalogState.loading && !state.catalogReady && state.searchResults.isEmpty())
    check(!state.selectSearchResult(MarketCatalog.fromInput("ADA")!!))
    first.complete(listOf(ada, pepe))
    loading.join()
    check(state.catalogReady && state.searchResults.single().name == "Cardano")
    check(state.selectSearchResult(state.searchResults.single()))
    check(state.selection.asset.symbol == "ADAUSDT" && state.watchlist == watchlist)
    state.updateQuery("pepe")
    val multiplied = state.searchResults.single()
    check(multiplied.ticker == "1000PEPE" && multiplied.symbol == "1000PEPEUSDT")
    check(state.selectSearchResult(multiplied))
    repeat(20) {
        state.updateQuery("a$it")
        state.refreshInstrumentCatalog()
    }
    check(calls == listOf("BINANCE")) { "Fresh catalog triggered requests while typing" }

    val refreshing = CompletableFuture<List<MarketInstrument>>()
    requests.add(refreshing)
    val refreshJob = launch { state.refreshInstrumentCatalog(force = true) }
    yield()
    val callsDuringRefresh = calls.size
    state.refreshInstrumentCatalog()
    check(state.catalogState.loading && !state.catalogReady && calls.size == callsDuringRefresh) {
        "Background cache read overrode an in-flight refresh"
    }
    refreshing.complete(listOf(ada, pepe))
    refreshJob.join()

    val oldSnapshot = catalog.cached("BINANCE")
    requests.add(CompletableFuture.failedFuture(IllegalStateException("HTTP 429")))
    state.updateQuery("ada")
    state.refreshInstrumentCatalog(force = true)
    check(state.catalogState.failed && !state.catalogReady)
    check(catalog.cached("BINANCE") == oldSnapshot && state.searchResults.single().symbol == "ADAUSDT")
    check(!state.selectSearchResult(state.searchResults.single()))
    requests.add(CompletableFuture.completedFuture(listOf(ada, pepe)))
    state.refreshInstrumentCatalog(force = true)
    check(state.catalogReady && !state.catalogState.failed)

    clock.now = clock.now.plusSeconds(60)
    check(!state.catalogReady)
    check(!state.selectSearchResult(state.searchResults.single()))
    requests.add(CompletableFuture.failedFuture(IllegalStateException("timeout")))
    state.refreshInstrumentCatalog()
    check(state.catalogState.failed && !state.catalogReady && catalog.cached("BINANCE") == oldSnapshot)
    requests.add(CompletableFuture.completedFuture(emptyList()))
    state.refreshInstrumentCatalog(force = true)
    check(state.catalogReady && state.searchResults.isEmpty()) { "Valid empty list was confused with failure" }
    check(!state.selectSearchResult(MarketCatalog.fromInput("NOTLISTED")!!))

    val lateBinance = CompletableFuture<List<MarketInstrument>>()
    val bybit = CompletableFuture<List<MarketInstrument>>()
    requests.add(lateBinance)
    requests.add(bybit)
    val oldLoad = launch { state.refreshInstrumentCatalog(force = true) }
    yield()
    state.select(exchange = "BYBIT")
    val newLoad = launch { state.refreshInstrumentCatalog() }
    yield()
    lateBinance.complete(listOf(ada))
    oldLoad.join()
    yield()
    check(state.catalogState.exchange == "BYBIT" && !state.catalogReady)
    bybit.complete(listOf(MarketInstrument("BTCUSDT", "BTC", "USDT", "Bitcoin")))
    newLoad.join()
    check(state.catalogReady && state.catalogAssets.single().symbol == "BTCUSDT")
    state.updateQuery("ada")
    check(state.searchResults.isEmpty()) { "Previous exchange results leaked into the selected exchange" }
    check(state.watchlist == watchlist)

    val cancelledRequest = CompletableFuture<List<MarketInstrument>>()
    val cancelledCatalog = InstrumentCatalog(fetch = { cancelledRequest })
    val cancelledLoad = launch { cancelledCatalog.load("BINANCE") }
    yield()
    cancelledLoad.cancelAndJoin()
    check(cancelledRequest.isCancelled && cancelledCatalog.cached("BINANCE") == null)
    println("Instrument catalog checks passed: cache/TTL, retry, failure vs absence, exact symbols, no watchlist mutation, exchange races and cancellation")
}
