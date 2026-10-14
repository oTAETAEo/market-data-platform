package com.marketdata.desktop.platform.window

import androidx.compose.ui.graphics.toArgb
import com.marketdata.desktop.ui.theme.deskPalette
import java.awt.Color
import java.awt.Dimension
import javax.swing.JFrame

internal val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

internal fun configureDesktopWindow(window: JFrame, dark: Boolean = true) {
    window.title = ""
    window.minimumSize = Dimension(420, 560)
    val background = Color(deskPalette(dark).background.toArgb())
    window.background = background
    window.contentPane.background = background
    window.rootPane.background = background
    if (isMacOs) {
        // Extend the themed client area behind the native traffic lights and title bar.
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
    }
}
