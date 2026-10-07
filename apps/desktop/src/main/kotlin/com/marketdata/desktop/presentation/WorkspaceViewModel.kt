package com.marketdata.desktop.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marketdata.ai.AiSettings
import com.marketdata.desktop.application.AnalysisRequest
import com.marketdata.desktop.application.AnalyzeMarketUseCase
import com.marketdata.desktop.application.MarketDataRuntime
import com.marketdata.desktop.model.AgentGroup
import com.marketdata.desktop.model.AnalysisRun
import com.marketdata.desktop.model.AnalysisSelection
import com.marketdata.desktop.model.MarketAsset
import com.marketdata.desktop.model.MarketCatalog
import com.marketdata.desktop.model.WorkspacePage
import com.marketdata.engine.MarketStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WorkspaceViewModel(
    private val analyzeMarket: AnalyzeMarketUseCase,
    private val marketData: MarketDataRuntime? = null
) {
    var selection by mutableStateOf(AnalysisSelection(MarketCatalog.assets.first(), "BINANCE", "15m"))
        private set
    var query by mutableStateOf("")
        private set
    var page by mutableStateOf(WorkspacePage.ANALYSIS)
        private set
    var group by mutableStateOf(AgentGroup.ALL)
        private set
    var selectedAgent by mutableStateOf<String?>(null)
        private set
    var favoritesOnly by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var activeRun by mutableStateOf<AnalysisRun?>(null)
        private set
    var marketStatus by mutableStateOf(MarketStatus.empty())
        private set
    var analysisStage by mutableStateOf("")
        private set
    var aiProvider by mutableStateOf("openai")
        private set
    var quickModel by mutableStateOf("gpt-5.4-mini")
        private set
    var deepModel by mutableStateOf("gpt-5.5")
        private set
    var apiKey by mutableStateOf("")
        private set

    private val watchedAssets = mutableStateListOf<MarketAsset>().apply { addAll(MarketCatalog.assets.take(6)) }
    private val favoriteTickers = mutableStateListOf("BTC", "ETH", "SOL")
    private val analysisHistory = mutableStateListOf<AnalysisRun>()

    val watchlist: List<MarketAsset> get() = watchedAssets.toList()
    val favorites: List<String> get() = favoriteTickers.toList()
    val history: List<AnalysisRun> get() = analysisHistory.toList()
    val visibleAssets: List<MarketAsset> get() = watchedAssets.filter {
        (!favoritesOnly || it.ticker in favoriteTickers) && MarketCatalog.matches(it, query)
    }
    val aiConfigured: Boolean get() = AiSettings(aiProvider, quickModel, deepModel, apiKey).configured()
    val canAnalyze: Boolean get() = !busy && marketStatus.analysisReady() && aiConfigured

    init {
        marketData?.start(selection)
        refreshMarketStatus()
    }

    fun updateQuery(value: String) { query = value }
    fun showPage(value: WorkspacePage) { page = value }
    fun filterByGroup(value: AgentGroup) { group = value }
    fun showAgent(value: String?) { selectedAgent = value }
    fun filterFavorites(value: Boolean) { favoritesOnly = value }
    fun updateAiSettings(provider: String, quickModel: String, deepModel: String, apiKey: String) {
        aiProvider = provider
        this.quickModel = quickModel
        this.deepModel = deepModel
        this.apiKey = apiKey
        error = null
    }

    fun refreshMarketStatus() {
        marketStatus = marketData?.status(selection) ?: MarketStatus("LIVE", 200, null, true)
    }

    fun select(asset: MarketAsset = selection.asset, exchange: String = selection.exchange,
               timeframe: String = selection.timeframe) {
        val next = AnalysisSelection(asset, exchange, timeframe)
        if (next != selection) {
            selection = next
            activeRun = analysisHistory.firstOrNull { it.selection == next }
            selectedAgent = null
            error = null
            marketData?.let {
                it.start(next)
                marketStatus = MarketStatus("CONNECTING", 0, null, false)
            }
        }
        page = WorkspacePage.ANALYSIS
    }

    fun toggleFavorite(ticker: String) {
        if (ticker in favoriteTickers) favoriteTickers.remove(ticker) else favoriteTickers.add(ticker)
    }

    fun addAsset(asset: MarketAsset) {
        if (watchedAssets.none { it.ticker == asset.ticker }) watchedAssets.add(asset)
        query = ""
        favoritesOnly = false
        select(asset)
    }

    fun addSymbol(input: String): Boolean {
        val asset = MarketCatalog.fromInput(input) ?: return false
        addAsset(asset)
        return true
    }

    fun openRun(run: AnalysisRun) {
        selection = run.selection
        activeRun = run
        selectedAgent = null
        page = WorkspacePage.ANALYSIS
    }

    suspend fun analyze() {
        if (busy) return
        val request = selection
        busy = true
        error = null
        analysisStage = "분석 컨텍스트 준비"
        try {
            if (!marketStatus.analysisReady()) throw IllegalStateException("시장 데이터가 준비될 때까지 기다려 주세요.")
            val settings = AiSettings(aiProvider, quickModel, deepModel, apiKey)
            val result = withContext(Dispatchers.Default) {
                analyzeMarket.execute(AnalysisRequest(request.exchange, request.asset.symbol, request.timeframe), settings)
            }
            val run = AnalysisRun(request, result)
            analysisHistory.add(0, run)
            if (analysisHistory.size > HISTORY_LIMIT) analysisHistory.removeAt(analysisHistory.lastIndex)
            // Navigation cannot attach an in-flight result to a different market selection.
            if (selection == request) activeRun = run
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (selection == request) error = failure.message ?: "분석을 완료하지 못했습니다. 다시 시도해 주세요."
        } finally {
            busy = false
            analysisStage = ""
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 50
    }
}
