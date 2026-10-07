package com.marketdata.desktop

import com.marketdata.collector.binance.client.BinanceMarketDataClient
import com.marketdata.engine.MarketEngine
import java.util.concurrent.TimeUnit

fun main() {
    val engine = MarketEngine()
    BinanceMarketDataClient(engine::accept) {
        engine.updateConnection("BINANCE", "BTCUSDT", "15m", it)
    }.use { client ->
        val loaded = client.start("BTCUSDT", "15m").get(20, TimeUnit.SECONDS)
        val status = engine.status("BINANCE", "BTCUSDT", "15m")
        check(loaded >= 200 && status.analysisReady())
        check(engine.currentContext("BINANCE", "BTCUSDT", "15m").candles().size >= 200)
        println("Live market smoke passed: Binance BTCUSDT 15m, candles=$loaded")
    }
}
