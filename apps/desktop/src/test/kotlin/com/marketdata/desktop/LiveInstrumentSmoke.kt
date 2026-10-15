package com.marketdata.desktop

import com.marketdata.collector.binance.client.BinanceInstrumentClient
import com.marketdata.collector.bybit.client.BybitInstrumentClient
import java.util.concurrent.TimeUnit

fun main() {
    val sources = listOf(
        "BINANCE" to { BinanceInstrumentClient().fetchInstruments() },
        "BYBIT" to { BybitInstrumentClient().fetchInstruments() }
    )
    for ((exchange, fetch) in sources) {
        val instruments = fetch().get(45, TimeUnit.SECONDS)
        check(instruments.isNotEmpty() && instruments.any { it.symbol() == "BTCUSDT" })
        check(instruments.map { it.symbol() }.distinct().size == instruments.size)
        check(instruments.all { it.quoteAsset() == "USDT" && it.symbol().endsWith("USDT") })
        println("$exchange live metadata passed: ${instruments.size} tradable crypto USDT perpetual instruments")
    }
}
