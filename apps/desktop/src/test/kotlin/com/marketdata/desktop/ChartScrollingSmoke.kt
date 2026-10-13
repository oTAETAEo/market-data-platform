package com.marketdata.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightCandlestickChart
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.theme.MarketTheme
import javafx.application.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Component
import java.awt.Container
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import kotlin.math.abs

private fun findScrollingChart(component: Component): LightweightChartPanel? =
    if (component is LightweightChartPanel) component
    else (component as? Container)?.components?.firstNotNullOfOrNull(::findScrollingChart)

private fun hasChartPixels(frame: ImageBitmap?): Boolean {
    if (frame == null) return false
    val pixels = frame.toPixelMap()
    val colors = mutableSetOf<Int>()
    for (y in 0 until frame.height step 8) for (x in 0 until frame.width step 8) {
        colors.add(pixels[x, y].toArgb())
    }
    return colors.size > 8
}

private suspend fun awaitScrollingCondition(message: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 5_000_000_000L
    while (!condition()) {
        check(System.nanoTime() < deadline) { message }
        delay(20)
    }
}

fun main() = runBlocking {
    System.setProperty("compose.interop.blending", "true")
    val scroll = ScrollState(0)
    val candles = mutableStateOf(listOf(ChartCandle(1_700_000_000, 100.0, 120.0, 99.0, 102.0, false)))
    val chartTop = AtomicReference(0f)
    lateinit var window: ComposeWindow
    var chart: LightweightChartPanel? = null
    SwingUtilities.invokeAndWait {
        window = ComposeWindow().apply {
            chart = LightweightChartPanel()
            setSize(640, 480)
            setContent {
                MarketTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Spacer(Modifier.height(120.dp))
                        Box(Modifier.onGloballyPositioned { chartTop.set(it.positionInRoot().y) }) {
                            LightweightCandlestickChart("BINANCE:BTCUSDT:15m", candles.value, 220.dp, renderer = chart)
                        }
                        Spacer(Modifier.height(800.dp))
                    }
                }
            }
            isVisible = true
        }
    }
    try {
        val panel = chart!!
        check(panel.awaitReady(30))
        awaitScrollingCondition("Chart snapshot was blank") { hasChartPixels(panel.captureFrame()) }
        val initialTop = chartTop.get()
        repeat(4) {
            for (delta in listOf(30f, 80f, 110f, -55f, -120f)) {
                scroll.scroll {
                    scrollBy(delta)
                    awaitScrollingCondition("Scrolling did not use the Compose frame at the content position") {
                        var nativeAbsent = false
                        SwingUtilities.invokeAndWait { nativeAbsent = findScrollingChart(window.contentPane) == null }
                        nativeAbsent && abs(chartTop.get() - (initialTop - scroll.value)) < 1f
                    }
                    check(hasChartPixels(panel.captureFrame())) { "Scrolling cleared the rendered frame" }
                }
                check(hasChartPixels(panel.captureFrame())) { "Scroll stop cleared the rendered frame" }
            }
            candles.value = listOf(candles.value.single().copy(close = 113.0 + it))
            awaitScrollingCondition("Live data stopped updating while scrolling") { panel.lastCandleClose(5) == 113.0 + it }
        }
        check(panel.lastCandleClose(5) == 116.0)
        SwingUtilities.invokeAndWait { check(panel.parent == null) }
        check(hasChartPixels(panel.captureFrame()))
        println("Chart scrolling smoke passed: 20 scroll start/stop cycles, continuous nonblank frames and live data")
    } finally {
        SwingUtilities.invokeAndWait { chart?.dispose(); window.dispose() }
        Platform.runLater { Platform.exit() }
    }
}
