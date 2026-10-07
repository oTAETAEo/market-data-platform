package com.marketdata.desktop

import com.marketdata.ai.StubTradingAgentsAdapter
import com.marketdata.ai.TradingAgentsAdapter
import com.marketdata.ai.AiSettings
import com.marketdata.desktop.application.AnalysisRequest
import com.marketdata.desktop.application.AnalyzeMarketUseCase
import com.marketdata.desktop.model.AgentGroup
import com.marketdata.desktop.model.MarketCatalog
import com.marketdata.desktop.model.WorkspacePage
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.desktop.ui.layout.WorkspaceLayout
import com.marketdata.desktop.ui.layout.adjustedDetailPanelWidth
import com.marketdata.desktop.ui.layout.agentColumnCount
import com.marketdata.engine.MarketContext
import com.marketdata.engine.MarketEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.Instant

fun main() = runBlocking {
    checkAnalysisUseCase()
    check(WorkspaceLayout.forWidth(420f) == WorkspaceLayout.NARROW)
    check(WorkspaceLayout.forWidth(799f) == WorkspaceLayout.NARROW)
    check(WorkspaceLayout.forWidth(800f) == WorkspaceLayout.MEDIUM)
    check(WorkspaceLayout.forWidth(1279f) == WorkspaceLayout.MEDIUM)
    check(WorkspaceLayout.forWidth(1280f) == WorkspaceLayout.WIDE)
    check(agentColumnCount(380f, AgentGroup.ANALYST) == 1)
    check(agentColumnCount(420f, AgentGroup.ANALYST) == 2)
    check(agentColumnCount(760f, AgentGroup.ANALYST) == 4)
    check(agentColumnCount(660f, AgentGroup.EXECUTION) == 3)
    check(adjustedDetailPanelWidth(null, 300f, -120f, 500f) == 420f)
    check(adjustedDetailPanelWidth(300f, 300f, 100f, 500f) == 272f)
    check(adjustedDetailPanelWidth(450f, 300f, -100f, 500f) == 500f)
    val state = viewModelForChecks()
    check(state.activeRun == null && state.history.isEmpty())
    state.updateQuery("btc / usdt")
    check(state.visibleAssets.single().ticker == "BTC")
    state.updateQuery("ethereum")
    check(state.visibleAssets.single().ticker == "ETH")
    state.updateQuery("missing-market")
    check(state.visibleAssets.isEmpty())
    state.addAsset(MarketCatalog.assets.last())
    state.addAsset(MarketCatalog.assets.last())
    check(state.watchlist.count { it.ticker == "LINK" } == 1)
    check(state.query.isEmpty() && state.selection.asset.ticker == "LINK")
    state.filterFavorites(true)
    check(state.visibleAssets.none { it.ticker == "LINK" })
    state.toggleFavorite("LINK")
    check(state.visibleAssets.any { it.ticker == "LINK" })
    check(MarketCatalog.fromInput("ada/usdt")?.symbol == "ADAUSDT")
    check(MarketCatalog.fromInput("BTC-USDT")?.name == "Bitcoin")
    check(MarketCatalog.fromInput("USDT") == null)
    check(!state.addSymbol("$"))
    check(state.addSymbol("adausdt"))
    check(state.selection.asset.symbol == "ADAUSDT")
    check(state.watchlist.any { it.ticker == "ADA" })

    state.analyze()
    val first = state.activeRun!!
    check(first.result.symbol() == "ADAUSDT" && state.history.size == 1)
    state.select(exchange = "BYBIT")
    check(state.activeRun == null)
    state.select(exchange = "BINANCE")
    check(state.activeRun == first)
    state.select(timeframe = "1h")
    check(state.activeRun == null)
    state.openRun(first)
    check(state.selection == first.selection && state.activeRun == first)
    check(state.page == WorkspacePage.ANALYSIS)

    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val delayed = viewModelForChecks(adapter = TradingAgentsAdapter { context, settings, progress ->
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        StubTradingAgentsAdapter().analyze(context, settings, progress)
    })
    val job = launch { delayed.analyze() }
    check(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
    check(delayed.busy)
    delayed.analyze()
    delayed.select(asset = MarketCatalog.assets[1])
    release.countDown()
    job.join()
    check(!delayed.busy && delayed.activeRun == null)
    check(delayed.history.single().selection.asset.ticker == "BTC")
    check(delayed.selection.asset.ticker == "ETH")

    val failing =
        viewModelForChecks(adapter = TradingAgentsAdapter { _, _, _ -> throw IllegalStateException("test failure") })
    failing.analyze()
    check(!failing.busy && failing.error != null && failing.history.isEmpty())
    failing.select(timeframe = "1m")
    check(failing.error == null)

    repeat(52) { state.analyze() }
    check(state.history.size == 50)
    println("Workspace checks passed: use-case contract, search, favorites, selection isolation, history, in-flight navigation, duplicate requests, failure recovery, retention.")
}

private fun viewModelForChecks(adapter: TradingAgentsAdapter = StubTradingAgentsAdapter()) =
    WorkspaceViewModel(AnalyzeMarketUseCase(object : MarketEngine() {
        override fun currentContext(exchange: String, symbol: String, timeframe: String) =
            MarketContext(exchange, symbol, timeframe, 200, Instant.now(), listOf("test"), emptyList())
    }, adapter)).apply { updateAiSettings("openai", "quick", "deep", "key") }

private fun checkAnalysisUseCase() {
    val context = MarketContext("BYBIT", "ETHUSDT", "1h", 200, Instant.EPOCH, listOf("test context"), emptyList())
    val settings = AiSettings("openai", "quick", "deep", "key")
    val expected = StubTradingAgentsAdapter().analyze(context, settings) { }
    var contextCalls = 0
    var adapterCalls = 0
    val engine = object : MarketEngine() {
        override fun currentContext(exchange: String, symbol: String, timeframe: String): MarketContext {
            check(exchange == "BYBIT" && symbol == "ETHUSDT" && timeframe == "1h")
            contextCalls++
            return context
        }
    }
    val adapter = TradingAgentsAdapter { actualContext, actualSettings, _ ->
        check(actualContext === context)
        check(actualSettings === settings)
        adapterCalls++
        expected
    }
    val request = AnalysisRequest("BYBIT", "ETHUSDT", "1h")
    check(AnalyzeMarketUseCase(engine, adapter).execute(request, settings) === expected)
    check(contextCalls == 1 && adapterCalls == 1)
    val failure = IllegalStateException("adapter failure")
    val failing = AnalyzeMarketUseCase(engine, TradingAgentsAdapter { _, _, _ -> throw failure })
    check(runCatching { failing.execute(request, settings) }.exceptionOrNull() === failure)
}
