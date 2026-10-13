package com.marketdata.desktop

import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import javafx.application.Platform
import javax.swing.SwingUtilities
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

private fun frameHash(panel: LightweightChartPanel): Int? {
    val frame = panel.captureFrame() ?: return null
    val pixels = frame.toPixelMap()
    var hash = 1
    for (y in 0 until frame.height step 4) for (x in 0 until frame.width step 4) {
        hash = 31 * hash + pixels[x, y].toArgb()
    }
    return hash
}

private fun awaitFrame(message: String, condition: () -> Boolean) {
    val deadline = System.nanoTime() + 5_000_000_000L
    while (!condition()) {
        check(System.nanoTime() < deadline) { message }
        Thread.sleep(20)
    }
}

fun main(args: Array<String>) {
    check(Platform::class.java.module.name == "javafx.graphics") {
        "JavaFX graphics must be loaded as a named module"
    }
    check(LightweightChartPanel::class.java.superclass.module.name == "javafx.swing") {
        "JavaFX Swing integration must be loaded as a named module"
    }
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
        check(panel.lastCandleClose(5) == 102.0)
        awaitFrame("Offscreen chart did not render") { frameHash(panel) != null }
        Thread.sleep(150)
        val initialFrame = frameHash(panel)
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(ChartCandle(1_700_000_000, 100.0, 103.0, 99.0, 102.5, false))
        )
        check(panel.dataPointCount(5) == 1 && panel.lastCandleClose(5) == 102.5) {
            "Live price did not update the existing candle in the chart series"
        }
        check(!panel.lastCandleClosed(5)) { "Live price update prematurely closed the candle" }
        awaitFrame("Price changed in data but not in the rendered pixels") { frameHash(panel) != initialFrame }
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(ChartCandle(1_700_000_000, 100.0, 104.0, 99.0, 103.0, true))
        )
        check(panel.lastCandleClosed(5)) { "Confirmed candle data did not replace the in-progress candle" }
        check(panel.lastCandleClose(5) == 103.0)
        panel.update(
            "BINANCE:BTCUSDT:15m",
            listOf(
                ChartCandle(1_700_000_000, 100.0, 104.0, 99.0, 103.0, true),
                ChartCandle(1_700_000_900, 103.0, 105.0, 102.0, 104.0, false)
            )
        )
        check(panel.dataPointCount(5) == 2) { "New in-progress candle did not append to Lightweight Charts" }
        check(!panel.lastCandleClosed(5)) { "New in-progress candle was marked as closed" }
        panel.update("BINANCE:BTCUSDT:1m", List(40) { index ->
            ChartCandle(1_700_000_000 + index * 60L, 100.0, 120.0, 99.0, 102.0 + index * 0.1, true)
        })
        check(panel.dataPointCount(5) == 40)
        awaitFrame("New data did not finish fitting the visible range") { panel.visibleRangeSpan(5) > 30 }
        val originalPosition = panel.scrollPosition(5)
        panel.pointer("mouseenter", 200.0, 80.0, 0)
        panel.pointer("mousemove", 200.0, 80.0, 0)
        panel.pointer("mousedown", 200.0, 80.0, 1)
        panel.pointer("mousemove", 100.0, 80.0, 1)
        panel.pointer("mousemove", 50.0, 80.0, 1)
        panel.pointer("mouseup", 50.0, 80.0, 0)
        awaitFrame("Chart drag input did not reach Lightweight Charts") { panel.scrollPosition(5) != originalPosition }
        val dragFrame = frameHash(panel)
        val originalSpan = panel.visibleRangeSpan(5)
        panel.wheel(200.0, 80.0, 100.0)
        awaitFrame("Wheel input did not zoom the chart range") { panel.visibleRangeSpan(5) > originalSpan }
        awaitFrame("Wheel input did not update the rendered chart") { frameHash(panel) != dragFrame }
        args.firstOrNull()?.let { directory ->
            val output = File(directory).apply { mkdirs() }
            Image.makeFromBitmap(panel.captureFrame()!!.asSkiaBitmap()).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File(output, "chart-frame.png").writeBytes(data.bytes)
                }
            }
        }
        println("Lightweight Charts runtime smoke passed")
    } finally {
        panel.dispose()
        Platform.runLater { Platform.exit() }
    }
}
