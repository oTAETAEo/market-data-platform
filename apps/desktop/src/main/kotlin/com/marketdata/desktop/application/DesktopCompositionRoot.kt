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
        val viewModel = WorkspaceViewModel(AnalyzeMarketUseCase(engine, adapter), marketData)
        return DesktopApplication(viewModel, marketData)
    }

    // UI checks use the same presentation layer without opening network connections or paid AI calls.
    fun createWorkspace(): WorkspaceViewModel {
        val previewEngine = object : MarketEngine() {
            override fun currentContext(exchange: String, symbol: String, timeframe: String) =
                MarketContext(exchange, symbol, timeframe, 200, Instant.now(), listOf("UI check"), emptyList())
        }
        return WorkspaceViewModel(AnalyzeMarketUseCase(previewEngine, StubTradingAgentsAdapter()))
            .apply { updateAiSettings("openai", "test-quick", "test-deep", "test-key") }
    }

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
