package com.marketdata.desktop

import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import javafx.application.Platform
import javax.swing.SwingUtilities

fun main() {
    lateinit var panel: LightweightChartPanel
    SwingUtilities.invokeAndWait { panel = LightweightChartPanel() }
    try {
        check(panel.awaitReady(30)) { "Lightweight Charts runtime did not initialize" }
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(ChartCandle(1_700_000_000, 100.0, 103.0, 99.0, 102.0, false))
        )
        check(panel.dataPointCount(5) == 1) { "Candle data did not reach Lightweight Charts" }
        check(!panel.lastCandleClosed(5)) { "An in-progress candle was marked as closed" }
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(ChartCandle(1_700_000_000, 100.0, 104.0, 99.0, 103.0, true))
        )
        check(panel.lastCandleClosed(5)) { "Confirmed candle data did not replace the in-progress candle" }
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(
                ChartCandle(1_700_000_000, 100.0, 104.0, 99.0, 103.0, true),
                ChartCandle(1_700_000_900, 103.0, 105.0, 102.0, 104.0, false)
            )
        )
        check(panel.dataPointCount(5) == 2) { "New in-progress candle did not append to Lightweight Charts" }
        check(!panel.lastCandleClosed(5)) { "New in-progress candle was marked as closed" }
        println("Lightweight Charts runtime smoke passed")
    } finally {
        panel.dispose()
        Platform.exit()
    }
}
