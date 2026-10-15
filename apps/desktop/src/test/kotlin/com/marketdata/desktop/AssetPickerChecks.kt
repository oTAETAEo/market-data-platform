@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.marketdata.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.marketdata.desktop.application.DesktopCompositionRoot
import com.marketdata.desktop.ui.theme.deskPalette
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.watchlist.AssetPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

fun main() = runBlocking {
    for (dark in listOf(true, false)) {
        val state = DesktopCompositionRoot.createWorkspace()
        val open = mutableStateOf(true)
        val scene = ImageComposeScene(640, 720, Density(1f), coroutineContext) {
            MarketTheme(dark) { if (open.value) AssetPicker(state) { open.value = false } }
        }
        try {
            var time = 0L
            fun render(): BufferedImage = scene.render(time).use { frame ->
                time += 16_666_667L
                frame.encodeToData()!!.use { ImageIO.read(it.bytes.inputStream()) }
            }
            repeat(4) { render(); delay(20) }
            val image = render()
            ImageIO.write(image, "png", File("build/reports/asset-picker-${if (dark) "dark" else "light"}.png"))
            // The middle of the query field must remain visibly distinct from the dialog panel.
            val fieldColor = image.getRGB(320, 224)
            val dialogColor = image.getRGB(320, 192)
            check(fieldColor != dialogColor) {
                "Asset picker search field blends into the ${if (dark) "dark" else "light"} dialog"
            }
            scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 224f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 224f))
            println("Asset picker ${if (dark) "dark" else "light"} passed: visible search surface and focus target")
        } finally {
            scene.close()
        }
    }
}
