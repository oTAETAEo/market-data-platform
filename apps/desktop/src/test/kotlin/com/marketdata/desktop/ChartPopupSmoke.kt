@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package com.marketdata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.model.ChartCandle
import com.marketdata.desktop.ui.chart.LightweightCandlestickChart
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.components.DropdownControl
import com.marketdata.desktop.ui.theme.DeskColors
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.theme.deskPalette
import javafx.application.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

fun main(args: Array<String>) = runBlocking {
    val output = File(args.single()).apply { mkdirs() }
    try {
        for ((width, height) in listOf(640 to 420, 320 to 360)) {
            lateinit var chart: LightweightChartPanel
            SwingUtilities.invokeAndWait { chart = LightweightChartPanel() }
            check(chart.awaitReady(30))
            val selected = mutableStateOf("15m")
            val dark = mutableStateOf(true)
            val candles = mutableStateOf(List(40) { index ->
                ChartCandle(1_700_000_000 + index * 60L, 100.0, 120.0, 99.0, 102.0 + index * 0.1, false)
            })
            var anchor = Rect.Zero
            val scene = ImageComposeScene(width, height, Density(1f), coroutineContext) {
                MarketTheme(dark.value) {
                    Column(Modifier.fillMaxSize().background(DeskColors.panel).padding(24.dp)) {
                        Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
                            DropdownControl(selected.value, listOf("1m", "5m", "15m", "1h", "4h"), "타임프레임") {
                                selected.value = it
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LightweightCandlestickChart("BINANCE:BTCUSDT:15m", candles.value, 220.dp, renderer = chart)
                    }
                }
            }
            try {
                var time = 0L
                fun render(): BufferedImage = scene.render(time).use { frame ->
                    time += 16_666_667L
                    frame.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
                }
                suspend fun settle() { repeat(6) { render(); delay(20) } }
                fun click(x: Float, y: Float) {
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                }
                fun trigger() = click(anchor.center.x, anchor.center.y)
                suspend fun openAndCheck() {
                    trigger()
                    // Inspect every opening frame, not just the final settled screenshot.
                    repeat(18) { index ->
                        val image = render()
                        val x = anchor.left.toInt() + 3
                        val y = anchor.bottom.toInt() + 16
                        val rgb = image.getRGB(x, y) and 0xFFFFFF
                        if (rgb != 0x121414) {
                            check(rgb == 0x1C1F1E) { "Menu appeared before its dark surface was ready: $rgb, frame $index" }
                            check((image.getRGB(x, y + 160) and 0xFFFFFF) == 0x1C1F1E) {
                                "Dropdown opening scaled or exposed an incomplete surface"
                            }
                        }
                        delay(10)
                    }
                    ImageIO.write(render(), "png", File(output, "dropdown-$width.png"))
                    check((render().getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 16) and 0xFFFFFF) == 0x1C1F1E)
                    check(chart.captureFrame() != null && chart.parent == null) { "Popup detached or cleared the chart" }
                }
                settle()
                repeat(3) {
                    openAndCheck()
                    trigger()
                    settle()
                    check((render().getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 16) and 0xFFFFFF) == 0x121414) {
                        "A short button click did not close the menu"
                    }
                }
                openAndCheck()
                click(anchor.left + 32, anchor.bottom + 30)
                settle()
                check(selected.value == "1m") { "Short item click did not select" }
                openAndCheck()
                scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
                settle()
                check((render().getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 16) and 0xFFFFFF) == 0x121414) {
                    "Escape did not dismiss the menu"
                }
                openAndCheck()
                scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
                settle()
                check(selected.value == "5m") { "Arrow/Enter keyboard selection did not work" }
                openAndCheck()
                candles.value = candles.value.dropLast(1) + candles.value.last().copy(close = 111.0)
                settle()
                check(chart.lastCandleClose(5) == 111.0) { "Live chart updates stopped while the dropdown was open" }
                click(width - 8f, height - 8f)
                settle()
                check((render().getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 16) and 0xFFFFFF) == 0x121414) {
                    "Outside click did not dismiss the menu"
                }
                openAndCheck()
                val span = chart.visibleRangeSpan(5)
                val position = chart.scrollPosition(5)
                dark.value = false
                val deadline = System.nanoTime() + 5_000_000_000L
                while (true) {
                    settle()
                    val frame = chart.captureFrame()!!
                    if (frame.toPixelMap()[frame.width - 2, frame.height - 2].toArgb() == -1) break
                    check(System.nanoTime() < deadline) { "Chart pixels did not switch to light mode" }
                }
                val light = render()
                check(light.getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 16) == deskPalette(false).elevated.toArgb()) {
                    "Open dropdown did not adopt the light theme"
                }
                check(selected.value == "5m" && chart.lastCandleClose(5) == 111.0)
                check(kotlin.math.abs(chart.visibleRangeSpan(5) - span) < 0.001 && kotlin.math.abs(chart.scrollPosition(5) - position) < 0.001) {
                    "Chart theme switching reset zoom or scroll"
                }
                ImageIO.write(light, "png", File(output, "light-dropdown-$width.png"))
                click(anchor.left + 32, anchor.bottom + 30)
                settle()
                check(selected.value == "1m") { "Light dropdown was not clickable" }
                dark.value = true
                settle()
                println("Dropdown $width passed: opening frames, short clicks, keyboard selection, dismissal, live updates and theme switching without range resets")
            } finally {
                scene.close()
                chart.dispose()
            }
        }
    } finally {
        Platform.runLater { Platform.exit() }
    }
}
