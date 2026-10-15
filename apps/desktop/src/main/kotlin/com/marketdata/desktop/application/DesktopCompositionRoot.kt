package com.marketdata.desktop.application

import com.marketdata.ai.AiSettings
import com.marketdata.ai.LocalTradingAgentsAdapter
import com.marketdata.ai.StubTradingAgentsAdapter
import com.marketdata.desktop.presentation.WorkspaceViewModel
import com.marketdata.engine.MarketContext
import com.marketdata.engine.MarketEngine
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import com.marketdata.collector.binance.client.BinanceInstrumentClient
import com.marketdata.collector.bybit.client.BybitInstrumentClient
import com.marketdata.core.market.MarketInstrument
import com.marketdata.desktop.model.MarketCatalog
import java.util.concurrent.CompletableFuture

internal class DesktopApplication(
    val viewModel: WorkspaceViewModel,
    private val marketData: MarketDataRuntime?
) : AutoCloseable {
    override fun close() {
        marketData?.close()
    }
}

internal object DesktopCompositionRoot {
    fun createApplication(): DesktopApplication {
        val engine = MarketEngine()
        val marketData = MarketDataRuntime(engine)
        val adapter = LocalTradingAgentsAdapter(findPython(), findTradingAgents())
        val binance = BinanceInstrumentClient()
        val bybit = BybitInstrumentClient()
        val catalog = InstrumentCatalog(fetch = { exchange ->
            when (exchange) {
                "BINANCE" -> binance.fetchInstruments()
                "BYBIT" -> bybit.fetchInstruments()
                else -> CompletableFuture.failedFuture(IllegalArgumentException("Unsupported exchange"))
            }
        })
        val viewModel = WorkspaceViewModel(AnalyzeMarketUseCase(engine, adapter), marketData, catalog)
        return DesktopApplication(viewModel, marketData)
    }

    // UI checks use the same presentation layer without opening network connections or paid AI calls.
    fun createWorkspace(catalog: InstrumentCatalog = previewCatalog()): WorkspaceViewModel {
        val previewEngine = object : MarketEngine() {
            override fun currentContext(exchange: String, symbol: String, timeframe: String) =
                MarketContext(exchange, symbol, timeframe, 200, Instant.now(), listOf("UI check"), emptyList())
        }
        return WorkspaceViewModel(AnalyzeMarketUseCase(previewEngine, StubTradingAgentsAdapter()), instrumentCatalog = catalog)
            .apply { updateAiSettings("openai", "test-quick", "test-deep", "test-key") }
    }

    fun previewCatalog(): InstrumentCatalog = InstrumentCatalog(fetch = {
        val assets = MarketCatalog.assets + MarketCatalog.fromInput("ADA")!!
        CompletableFuture.completedFuture(assets.map { MarketInstrument(it.symbol, it.ticker, "USDT", it.name) })
    })

    private fun findPython(): Path {
        val configured = System.getenv("MARKET_DATA_PYTHON")?.let(Path::of)
        if (configured != null) return configured
        return projectRoot().resolve(".venv-tradingagents/bin/python")
    }

    private fun findTradingAgents(): Path = System.getenv("TRADINGAGENTS_REPO")?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), "Desktop", "TradingAgents")

    private fun projectRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        repeat(8) {
            if (Files.exists(current.resolve("settings.gradle"))) return current
            current = current.parent ?: return@repeat
        }
        return Path.of(System.getProperty("user.home"), "Desktop", "market-data-platform")
    }
}
