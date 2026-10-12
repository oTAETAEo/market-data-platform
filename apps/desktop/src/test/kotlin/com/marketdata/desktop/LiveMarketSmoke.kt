package com.marketdata.desktop

import com.marketdata.collector.binance.client.BinanceMarketDataClient
import com.marketdata.engine.MarketEngine
import com.marketdata.core.event.MarketCandleEvent
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

fun main() {
    val engine = MarketEngine()
    val updates = LinkedBlockingQueue<MarketCandleEvent>()
    BinanceMarketDataClient({ candle ->
        engine.accept(candle)
        if (!candle.closed()) updates.offer(candle)
    }) {
        engine.updateConnection("BINANCE", "BTCUSDT", "15m", it)
    }.use { client ->
        val loaded = client.start("BTCUSDT", "15m").get(20, TimeUnit.SECONDS)
        val status = engine.status("BINANCE", "BTCUSDT", "15m")
        check(loaded >= 200 && status.analysisReady())
        check(engine.currentContext("BINANCE", "BTCUSDT", "15m").candles().size >= 200)
        updates.clear()
        val first = updates.poll(20, TimeUnit.SECONDS) ?: error("No live WebSocket candle arrived")
        val second = updates.poll(10, TimeUnit.SECONDS) ?: error("WebSocket candles stopped updating")
        val current = engine.recentCandles("BINANCE", "BTCUSDT", "15m", 1).single()
        check(!current.receivedAt().isBefore(second.receivedAt())) {
            "Engine discarded live candle updates after REST warmup"
        }
        println("Live market smoke passed: Binance BTCUSDT 15m, candles=$loaded, " +
            "WebSocket close=${first.close()} -> ${second.close()}, engine received=${current.receivedAt()}")
    }
}
