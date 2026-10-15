@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightCandlestickChart
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.theme.DeskColors
import com.marketdata.desktop.ui.theme.MarketTheme
import javafx.application.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.abs

fun main(args: Array<String>) = runBlocking {
    val output = File(args.single()).apply { mkdirs() }
    try {
        for (scale in listOf(1f, 2f)) {
            val width = if (scale == 1f) 640 else 840
            val height = if (scale == 1f) 420 else 560
            lateinit var panel: LightweightChartPanel
            SwingUtilities.invokeAndWait { panel = LightweightChartPanel() }
            check(panel.awaitReady(30))
            val dark = mutableStateOf(true)
            val candles = mutableStateOf(List(40) { index ->
                ChartCandle(1_700_000_000 + index * 60L, 100.0, 120.0, 99.0, 102.0 + index * 0.1, true)
            })
            val scene = ImageComposeScene(width, height, Density(scale), coroutineContext) {
                MarketTheme(dark.value) {
                    Column(Modifier.fillMaxSize().background(DeskColors.panel)) {
                        Spacer(Modifier.height(40.dp))
                        LightweightCandlestickChart("BINANCE:BTCUSDT:1m", candles.value, 220.dp, renderer = panel)
                    }
                }
            }
            try {
                var time = 0L
                fun render(): BufferedImage = scene.render(time).use { frame ->
                    time += 16_666_667L
                    frame.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
                }
                suspend fun settle() { repeat(8) { render(); delay(20) } }
                val chartY = (48 * scale).toInt()
                fun background(image: BufferedImage) = image.getRGB(8, 8) and 0xFFFFFF
                fun chartBackground(image: BufferedImage) = image.getRGB((8 * scale).toInt(), chartY) and 0xFFFFFF
                fun checkSynchronized(image: BufferedImage, label: String) {
                    val appColor = background(image)
                    val chartColor = chartBackground(image)
                    if (appColor != chartColor) {
                        ImageIO.write(image, "png", File(output, "mixed-theme-$scale-$label.png"))
                    }
                    check(appColor == chartColor) {
                        "Theme frames were not atomic: app=${appColor.toString(16)}, chart=${chartColor.toString(16)}, scale=$scale"
                    }
                }
                val initialDeadline = System.nanoTime() + 5_000_000_000L
                while (chartBackground(render()) != 0x121414) {
                    check(System.nanoTime() < initialDeadline) { "Initial chart frame was not ready" }
                    delay(20)
                }
                settle()
                val originalSpan = panel.visibleRangeSpan(5)
                val originalPosition = panel.scrollPosition(5)
                repeat(8) { index ->
                    dark.value = !dark.value
                    val target = if (dark.value) 0x121414 else 0xFFFFFF
                    val deadline = System.nanoTime() + 3_000_000_000L
                    var rendered: BufferedImage
                    do {
                        rendered = render()
                        checkSynchronized(rendered, index.toString())
                        check(System.nanoTime() < deadline) { "Theme transition did not finish" }
                        delay(8)
                    } while (background(rendered) != target)
                    ImageIO.write(rendered, "png", File(output, "theme-$scale-$index.png"))
                    check(abs(panel.visibleRangeSpan(5) - originalSpan) < 0.001)
                    check(abs(panel.scrollPosition(5) - originalPosition) < 0.001)
                }
                repeat(20) { index ->
                    dark.value = !dark.value
                    checkSynchronized(render(), "rapid-$index")
                    delay(8)
                }
                dark.value = false
                val rapidDeadline = System.nanoTime() + 3_000_000_000L
                do {
                    val rendered = render()
                    checkSynchronized(rendered, "rapid-final")
                    check(System.nanoTime() < rapidDeadline) { "Latest theme request did not win" }
                    delay(8)
                } while (background(rendered) != 0xFFFFFF)
                check(abs(panel.visibleRangeSpan(5) - originalSpan) < 0.001)
                check(abs(panel.scrollPosition(5) - originalPosition) < 0.001)
                candles.value = candles.value.dropLast(1) + candles.value.last().copy(close = 115.0)
                settle()
                check(panel.lastCandleClose(5) == 115.0)
                println("Chart theme $scale passed: normal/rapid transition frames synchronized, zoom/scroll retained and live updates active")
            } finally {
                scene.close()
                panel.dispose()
            }
        }
    } finally {
        Platform.runLater { Platform.exit() }
    }
}
