package com.marketdata.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.components.StableDropdownMenu
import com.marketdata.desktop.ui.theme.MarketTheme
import javafx.application.Platform
import javax.swing.SwingUtilities

fun main() {
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.layers.type", "WINDOW")
    lateinit var window: ComposeWindow
    lateinit var chart: LightweightChartPanel
    SwingUtilities.invokeAndWait {
        chart = LightweightChartPanel()
        window = ComposeWindow()
        window.setSize(640, 480)
        window.setContent {
            MarketTheme {
                Box {
                    SwingPanel(factory = { chart }, modifier = Modifier.size(600.dp, 400.dp))
                    StableDropdownMenu(expanded = true, onDismissRequest = {}) {
                        DropdownMenuItem(onClick = {}) { Text("1m") }
                        DropdownMenuItem(onClick = {}) { Text("5m") }
                    }
                }
            }
        }
        window.isVisible = true
    }
    try {
        check(chart.awaitReady(30)) { "Chart did not initialize with a native popup" }
        var verified = false
        repeat(50) {
            SwingUtilities.invokeAndWait {
                val popup = window.ownedWindows.firstOrNull { it.isShowing }
                if (popup != null) {
                    check(popup.focusableWindowState) { "Dropdown cannot receive focus" }
                    check(chart.isShowing) { "Chart was hidden when the dropdown opened" }
                    verified = true
                }
            }
            if (!verified) Thread.sleep(100)
        }
        check(verified) { "Dropdown was not created as a separate native window" }
        val bounds = java.awt.Rectangle()
        SwingUtilities.invokeAndWait {
            bounds.setBounds(window.ownedWindows.first { it.isShowing }.bounds)
        }
        repeat(10) {
            Thread.sleep(50)
            SwingUtilities.invokeAndWait {
                check(window.ownedWindows.first { it.isShowing }.bounds == bounds) {
                    "Dropdown window moved or resized while open"
                }
            }
        }
        println("Chart popup smoke passed: focusable native dropdown and visible JavaFX chart")
    } finally {
        SwingUtilities.invokeAndWait { window.dispose(); chart.dispose() }
        Platform.exit()
    }
}
