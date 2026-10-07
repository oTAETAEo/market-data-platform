@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.compose.ui.InternalComposeUiApi::class
)

package com.marketdata.desktop

import com.marketdata.desktop.application.DesktopCompositionRoot
import com.marketdata.desktop.model.WorkspacePage
import com.marketdata.desktop.ui.layout.WorkspaceLayout
import com.marketdata.desktop.ui.layout.sidebarWidth
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.workspace.MarketWorkspace

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import javax.imageio.ImageIO
fun main(args: Array<String>) = runBlocking {
    val output = File(args.single()).apply {
        mkdirs()
    }
    for (
    (width, height) in listOf(
        1920 to 1080,
        1440 to 940,
        1280 to 800,
        1180 to 800,
        920 to 680,
        800 to 620,
        640 to 680,
        420 to 560
    )
    ) {
        val state = DesktopCompositionRoot.createWorkspace()
        val layout = WorkspaceLayout.forWidth(width.toFloat())
        val narrow = !layout.hasSidebar
        val compact = !layout.hasDetailPanel || height < 800
        val scene = ImageComposeScene(
            width,
            height,
            density = Density(1f),
            coroutineContext = coroutineContext
        ) {
            MarketTheme {
                MarketWorkspace(
                    state,
                    titleBar = {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                        )
                    }
                )
            }
        }

        try {
            var frameTime = 0L

            fun render() =
                scene.render(frameTime).also {
                    frameTime += 100_000_000L
                }
            suspend fun settle() {
                repeat(4) {
                    render().close()
                    delay(20)
                }
            }
            suspend fun click(
                x: Float,
                y: Float
            ) {
                val position = Offset(x, y)
                scene.sendPointerEvent(
                    PointerEventType.Press,
                    position,
                    type = PointerType.Mouse
                )
                scene.sendPointerEvent(
                    PointerEventType.Release,
                    position,
                    type = PointerType.Mouse
                )
                settle()
            }
            suspend fun drag(
                fromX: Float,
                fromY: Float,
                toX: Float,
                toY: Float
            ) {
                scene.sendPointerEvent(
                    PointerEventType.Press,
                    Offset(fromX, fromY),
                    type = PointerType.Mouse
                )
                repeat(4) { step ->
                    val amount = (step + 1) / 4f
                    scene.sendPointerEvent(
                        PointerEventType.Move,
                        Offset(
                            fromX + (toX - fromX) * amount,
                            fromY + (toY - fromY) * amount
                        ),
                        type = PointerType.Mouse
                    )
                }
                scene.sendPointerEvent(
                    PointerEventType.Release,
                    Offset(toX, toY),
                    type = PointerType.Mouse
                )
                settle()
            }
            suspend fun capture(
                name: String,
                viewportWidth: Int = width,
                viewportHeight: Int = height
            ): Int {
                settle()
                val file = File(output, "$name.png")
                val image = render().use { frame ->
                    frame.encodeToData()!!.use { data ->
                        ImageIO.read(
                            data.bytes.inputStream()
                        ).getSubimage(
                            0,
                            0,
                            viewportWidth,
                            viewportHeight
                        )
                    }
                }
                ImageIO.write(
                    image,
                    "png",
                    file
                )
                check(
                    image.width == viewportWidth &&
                            image.height == viewportHeight
                )
                val palette = mutableSetOf<Int>()
                for (y in 0 until viewportHeight step 4) {
                    for (x in 0 until viewportWidth step 4) {
                        palette.add(
                            image.getRGB(x, y)
                        )
                    }
                }
                check(palette.size > 80) {
                    "Blank or incomplete render: $name"
                }
                val chromeColor =
                    java.awt.Color(8, 9, 9).rgb
                for (x in 0 until viewportWidth step 8) {
                    check(
                        image.getRGB(x, 16) == chromeColor
                    ) {
                        "Title strip color mismatch"
                    }
                }
                return file
                    .readBytes()
                    .contentHashCode()
            }
            val mainLeft =
                if (layout.hasSidebar) {
                    16f + sidebarWidth(width.toFloat())
                } else {
                    8f
                }
            val initialHash =
                capture("workspace-$width")
            if (width == 1440) {
                drag(
                    1110f,
                    450f,
                    950f,
                    450f
                )
                check(
                    capture("detail-expanded-$width") != initialHash
                ) {
                    "Report pane did not resize"
                }
            }
            click(
                mainLeft + 142f,
                126f
            )
            check(
                state.page == WorkspacePage.HISTORY
            ) {
                "History tab is not clickable"
            }
            click(
                mainLeft + 55f,
                126f
            )
            check(
                state.page == WorkspacePage.ANALYSIS
            )
            click(
                if (narrow) {
                    width - 78f
                } else {
                    width / 2f
                },
                height - if (narrow) 36f else 42f
            )
            check(
                state.activeRun != null &&
                        state.history.size == 1
            )
            capture("analysis-$width")
            click(
                mainLeft + 70f,
                if (compact) 480f else 630f
            )
            check(
                state.selectedAgent == "Technical Analyst"
            ) {
                "Agent card is not clickable"
            }
            capture("report-$width")
            click(
                mainLeft + 142f,
                126f
            )
            check(
                state.page == WorkspacePage.HISTORY
            )
            capture("history-$width")
            click(
                mainLeft + 100f,
                292f
            )
            check(
                state.page == WorkspacePage.ANALYSIS &&
                        state.selectedAgent == null
            )
            click(
                width / 2f,
                64f
            )
            val eventSource = Canvas()
            for (character in "sol") {
                scene.sendKeyEvent(
                    KeyEvent(
                        key = Key.Unknown,
                        type = KeyEventType.Unknown,
                        codePoint = character.code,
                        nativeEvent = AwtKeyEvent(
                            eventSource,
                            AwtKeyEvent.KEY_TYPED,
                            System.currentTimeMillis(),
                            0,
                            AwtKeyEvent.VK_UNDEFINED,
                            character
                        )
                    )
                )
                settle()
            }
            settle()
            check(
                state.query == "sol"
            ) {
                "Search field did not receive text"
            }
            check(
                state.visibleAssets.single().ticker == "SOL"
            )
            if (narrow) {
                capture("markets-$width")
            }
            click(
                110f,
                274f
            )
            check(
                state.selection.asset.ticker == "SOL" &&
                        state.activeRun == null
            )
            if (narrow) {
                check(
                    state.query.isEmpty()
                ) {
                    "Narrow market selection should return to analysis"
                }
            }
            capture("search-$width")
            if (narrow) {
                click(
                    32f,
                    64f
                )
                capture("watchlist-$width")
                click(
                    110f,
                    340f
                )
                check(
                    state.selection.asset.ticker == "ETH"
                )
                capture("selected-$width")
            }
            if (width == 1920) {
                state.updateQuery("")
                state.analyze()
                state.showAgent("Technical Analyst")
                val selection = state.selection
                val report = state.activeRun
                for (
                (resizeWidth, resizeHeight) in listOf(
                    1279 to 800,
                    1280 to 800,
                    800 to 620,
                    799 to 620,
                    420 to 560,
                    640 to 680,
                    1440 to 940
                )
                ) {
                    scene.constraints = Constraints.fixed(
                        resizeWidth,
                        resizeHeight
                    )
                    capture(
                        "resize-$resizeWidth",
                        resizeWidth,
                        resizeHeight
                    )
                    check(
                        state.selection == selection &&
                                state.activeRun == report
                    )
                    check(
                        state.selectedAgent ==
                                "Technical Analyst"
                    )
                }
                println(
                    "Continuous scene resizing passed: " +
                            "panel transitions and selected report " +
                            "retained across breakpoints."
                )
            }
            println(
                "Desktop UI checks passed at " +
                        "${width}x$height: rendering, tab clicks, " +
                        "analysis button, report selection, history, " +
                        "text input, market selection."
            )
        } finally {
            scene.close()
        }
    }
}