package com.marketdata.desktop.application

import com.marketdata.collector.binance.client.BinanceMarketDataClient
import com.marketdata.collector.bybit.client.BybitMarketDataClient
import com.marketdata.desktop.model.AnalysisSelection
import com.marketdata.engine.MarketEngine
import com.marketdata.engine.MarketStatus
import java.util.concurrent.CompletableFuture

internal class MarketDataRuntime(private val engine: MarketEngine) : AutoCloseable {
    private var client: AutoCloseable? = null
    private var selection: AnalysisSelection? = null

    @Synchronized
    fun start(next: AnalysisSelection): CompletableFuture<Int> {
        if (selection == next && client != null) return CompletableFuture.completedFuture(status(next).candleCount())
        closeClient()
        selection = next
        val onStatus: (String) -> Unit = {
            engine.updateConnection(next.exchange, next.asset.symbol, next.timeframe, it)
        }
        val nextClient = when (next.exchange) {
            "BINANCE" -> BinanceMarketDataClient(engine::accept, onStatus)
            "BYBIT" -> BybitMarketDataClient(engine::accept, onStatus)
            else -> throw IllegalArgumentException("지원하지 않는 거래소입니다: ${next.exchange}")
        }
        client = nextClient
        return when (nextClient) {
            is BinanceMarketDataClient -> nextClient.start(next.asset.symbol, next.timeframe)
            is BybitMarketDataClient -> nextClient.start(next.asset.symbol, next.timeframe)
            else -> error("unsupported client")
        }.exceptionally {
            engine.updateConnection(next.exchange, next.asset.symbol, next.timeframe, "ERROR")
            0
        }
    }

    fun status(current: AnalysisSelection): MarketStatus =
        engine.status(current.exchange, current.asset.symbol, current.timeframe)

    @Synchronized
    override fun close() {
        closeClient()
        selection = null
    }

    private fun closeClient() {
        client?.close()
        client = null
    }
}
