package com.marketdata.desktop.application

import com.marketdata.ai.AnalysisResult
import com.marketdata.ai.AiSettings
import com.marketdata.ai.TradingAgentsAdapter
import com.marketdata.engine.MarketEngine

internal data class AnalysisRequest(val exchange: String, val symbol: String, val timeframe: String)

internal class AnalyzeMarketUseCase(
    private val marketEngine: MarketEngine,
    private val aiAdapter: TradingAgentsAdapter
) {
    fun execute(request: AnalysisRequest, settings: AiSettings, onProgress: (String) -> Unit = {}): AnalysisResult {
        val context = marketEngine.currentContext(request.exchange, request.symbol, request.timeframe)
        return aiAdapter.analyze(context, settings, onProgress)
    }
}
