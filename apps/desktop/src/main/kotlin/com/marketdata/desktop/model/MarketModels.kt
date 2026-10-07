package com.marketdata.desktop.model

import com.marketdata.ai.AnalysisResult

internal data class MarketAsset(val ticker: String, val name: String, val mark: String) {
    val symbol get() = "${ticker}USDT"
}

internal data class AnalysisSelection(
    val asset: MarketAsset,
    val exchange: String,
    val timeframe: String
)

internal data class AnalysisRun(val selection: AnalysisSelection, val result: AnalysisResult)

internal enum class WorkspacePage { ANALYSIS, HISTORY }

internal enum class AgentGroup(val label: String) {
    ALL("전체"), ANALYST("애널리스트"), RESEARCH("리서치"), EXECUTION("트레이딩")
}
