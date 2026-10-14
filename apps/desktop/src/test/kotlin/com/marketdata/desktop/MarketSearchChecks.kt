@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package com.marketdata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.application.DesktopCompositionRoot
import com.marketdata.desktop.ui.theme.DeskColors
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.theme.deskPalette
import com.marketdata.desktop.ui.watchlist.MarketSearch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) = runBlocking {
    val output = File(args.single()).apply { mkdirs() }
    for (width in listOf(1440, 420)) for (dark in listOf(true, false)) {
        val state = DesktopCompositionRoot.createWorkspace()
        val originalWatchlist = state.watchlist
        var anchor = Rect.Zero
        val scene = ImageComposeScene(width, 560, Density(1f), coroutineContext) {
            MarketTheme(dark) {
                Column(Modifier.fillMaxSize().background(DeskColors.background).padding(24.dp)) {
                    Box(Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
                        MarketSearch(state) { state.selectSearchResult(it) }
                    }
                }
            }
        }
        try {
            var time = 0L
            fun render() = scene.render(time).also { time += 33_333_333L }
            suspend fun settle() { repeat(4) { render().close(); delay(20) } }
            suspend fun click(x: Float, y: Float) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                settle()
            }
            suspend fun key(key: Key) {
                scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
                scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
                settle()
            }
            suspend fun type(value: String) {
                val before = state.query
                for (character in value) {
                    scene.sendKeyEvent(KeyEvent(Key.Unknown, KeyEventType.Unknown, codePoint = character.code,
                        nativeEvent = AwtKeyEvent(Canvas(), AwtKeyEvent.KEY_TYPED, System.currentTimeMillis(),
                            0, AwtKeyEvent.VK_UNDEFINED, character)))
                    settle()
                }
                check(state.query == before + value) { "Typing focus was lost: expected ${before + value}, got ${state.query}" }
            }
            suspend fun capture(name: String) {
                settle()
                render().use { frame -> frame.encodeToData()!!.use { data ->
                    ImageIO.write(ImageIO.read(data.bytes.inputStream()), "png", File(output, "$name-$width-${if (dark) "dark" else "light"}.png"))
                } }
            }
            fun menuPixel() = render().use { frame -> frame.encodeToData()!!.use { data ->
                ImageIO.read(data.bytes.inputStream()).getRGB(anchor.left.toInt() + 3, anchor.bottom.toInt() + 18)
            } }
            fun checkBorderless() = render().use { frame -> frame.encodeToData()!!.use { data ->
                val palette = deskPalette(dark)
                val background = if (dark) palette.elevated else palette.panel
                check(ImageIO.read(data.bytes.inputStream()).getRGB(anchor.center.x.toInt(), anchor.top.toInt()) == background.toArgb()) {
                    "Search field still has an outline"
                }
            } }
            settle()
            checkBorderless()
            capture("search-idle")
            val closedPixel = menuPixel()
            click(anchor.left + 70, anchor.center.y)
            type("sol")
            checkBorderless()
            check(state.searchResults.single().ticker == "SOL")
            check(state.visibleAssets == originalWatchlist)
            check(menuPixel() != closedPixel) { "Suggestions were not rendered below the field" }
            capture("search-results")
            key(Key.Enter)
            check(state.selection.asset.ticker == "SOL" && state.query.isEmpty())
            check(menuPixel() == closedPixel) { "Selection did not close suggestions" }
            type("avax")
            click(anchor.left + 80, anchor.bottom + 43)
            check(state.selection.asset.ticker == "AVAX" && state.query.isEmpty()) { "Short result click did not select" }
            check(state.watchlist == originalWatchlist)
            click(anchor.left + 70, anchor.center.y)
            type("ada/usdt")
            check(state.searchResults.isEmpty())
            capture("search-custom")
            key(Key.Enter)
            check(state.selection.asset.symbol == "ADAUSDT" && state.query.isEmpty())
            check(state.watchlist == originalWatchlist)
            type("$")
            val selection = state.selection
            capture("search-empty")
            key(Key.Enter)
            check(state.selection == selection)
            click(anchor.right - 28, anchor.center.y)
            check(state.query.isEmpty()) { "Clear button did not reset search" }
            type("btc")
            key(Key.Escape)
            check(state.watchlist == originalWatchlist && state.visibleAssets == originalWatchlist)
            check(menuPixel() == closedPixel)
            state.updateQuery("btc")
            settle()
            check(menuPixel() == closedPixel) { "Dismissed suggestions reopened without input" }
            click(anchor.left + 70, anchor.center.y)
            check(menuPixel() != closedPixel) { "Clicking the focused field did not reopen suggestions" }
            click(width - 8f, 500f)
            check(menuPixel() == closedPixel) { "Outside click did not dismiss suggestions" }
            state.updateQuery("t")
            click(anchor.left + 70, anchor.center.y)
            val last = state.searchResults.last()
            repeat(state.searchResults.size - 1) { key(Key.DirectionDown) }
            capture("search-keyboard")
            key(Key.Enter)
            check(state.selection.asset == last && state.query.isEmpty())
            type("eth")
            key(Key.Tab)
            key(Key.Enter)
            check(state.query.isEmpty() && state.selection.asset == last) { "Keyboard clear changed selection instead of clearing" }
            type("btc")
            key(Key.Escape)
            println("Market search $width ${if (dark) "dark" else "light"} passed: typing focus, mouse/keyboard selection, custom ticker, invalid input, dismissal and scrolling")
        } finally {
            scene.close()
        }
    }
}
