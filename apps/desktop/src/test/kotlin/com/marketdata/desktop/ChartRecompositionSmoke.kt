package com.marketdata.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightCandlestickChart
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.theme.MarketTheme
import javafx.application.Platform
import java.awt.event.HierarchyEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

private fun awaitChartCondition(message: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 5_000_000_000L
    while (!condition()) {
        check(System.nanoTime() < deadline) { message }
        Thread.sleep(20)
    }
}

fun main() {
    System.setProperty("compose.interop.blending", "true")
    val candles = mutableStateOf(listOf(ChartCandle(1_700_000_000, 100.0, 120.0, 99.0, 102.0, false)))
    lateinit var window: ComposeWindow
    var chart: LightweightChartPanel? = null
    val reattachments = AtomicInteger()
    SwingUtilities.invokeAndWait {
        window = ComposeWindow().apply {
            chart = LightweightChartPanel()
            setSize(640, 480)
            setContent {
                MarketTheme {
                    LightweightCandlestickChart("BINANCE:BTCUSDT:15m", candles.value, 220.dp, renderer = chart)
                }
            }
            isVisible = true
        }
    }
    try {
        val panel = chart!!
        check(panel.awaitReady(30)) { "Chart did not initialize" }
        awaitChartCondition("Initial data did not reach the chart") { panel.dataPointCount(5) == 1 }
        awaitChartCondition("Initial rendered frame did not reach Compose") { panel.captureFrame() != null }
        SwingUtilities.invokeAndWait {
            panel.addHierarchyListener { event ->
                if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) {
                    reattachments.incrementAndGet()
                }
            }
        }
        repeat(10) { index ->
            val price = 103.0 + index
            SwingUtilities.invokeAndWait { candles.value = listOf(candles.value.single().copy(close = price)) }
            awaitChartCondition("Recomposition did not update the chart price") { panel.lastCandleClose(5) == price }
            check(panel.captureFrame() != null) { "A live update cleared the rendered frame" }
        }
        check(reattachments.get() == 0) {
            "Live updates detached and reattached the chart ${reattachments.get()} times"
        }
        SwingUtilities.invokeAndWait { check(panel.parent == null) }
        println("Chart recomposition smoke passed: 10 live price updates with a persistent Compose frame")
    } finally {
        SwingUtilities.invokeAndWait { chart?.dispose(); window.dispose() }
        Platform.runLater { Platform.exit() }
    }
}
