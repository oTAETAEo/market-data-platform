package com.marketdata.desktop

import com.marketdata.desktop.platform.window.configureDesktopWindow
import com.marketdata.desktop.platform.window.isMacOs

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Color
import java.awt.Dimension
import javax.swing.SwingUtilities

fun main() {
    SwingUtilities.invokeAndWait {
        val window = ComposeWindow()
        try {
            configureDesktopWindow(window)
            window.size = Dimension(420, 560)
            window.addNotify()
            check(window.title.isEmpty())
            check(window.minimumSize == Dimension(420, 560))
            check(window.isResizable && !window.isUndecorated)
            check(window.background == Color(8, 9, 9))
            check(window.contentPane.background == window.background)
            check(window.rootPane.background == window.background)
            if (isMacOs) {
                check(window.rootPane.getClientProperty("apple.awt.fullWindowContent") == true)
                check(window.rootPane.getClientProperty("apple.awt.transparentTitleBar") == true)
                check(window.rootPane.getClientProperty("apple.awt.windowTitleVisible") == false)
            }
            println("Desktop window checks passed: empty title, unified background, native controls, minimum size; insets=${window.insets}")
        } finally {
            window.dispose()
        }
    }
}
