package com.marketdata.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.marketdata.desktop.ui.chart.LightweightChartPanel
import com.marketdata.desktop.ui.components.NativeDropdownMenu
import com.marketdata.desktop.ui.theme.MarketTheme
import javafx.application.Platform
import java.awt.Toolkit
import java.awt.AWTEvent
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import javax.swing.JMenuItem
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities

fun main() {
    System.setProperty("compose.interop.blending", "true")
    lateinit var window: ComposeWindow
    lateinit var chart: LightweightChartPanel
    lateinit var menu: NativeDropdownMenu
    var selected = "15m"
    val firstVisibleBounds = mutableListOf<Rectangle>()
    SwingUtilities.invokeAndWait {
        chart = LightweightChartPanel()
        window = ComposeWindow()
        window.setSize(640, 480)
        window.setLocation(200, 150)
        window.setContent {
            MarketTheme {
                SwingPanel(factory = { chart }, modifier = Modifier.size(600.dp, 400.dp))
            }
        }
        window.isVisible = true
        menu = NativeDropdownMenu()
    }
    val listener = AWTEventListener { event ->
        if (event is WindowEvent && event.id == WindowEvent.WINDOW_OPENED && event.window.owner == window) {
            firstVisibleBounds.add(Rectangle(event.window.bounds))
        }
    }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
    try {
        check(chart.awaitReady(30)) { "Chart did not initialize" }
        val expected = Point()
        repeat(3) {
            val bounds = Rectangle()
            SwingUtilities.invokeAndWait {
                menu.setOptions(selected, listOf("1m", "5m", "15m")) { selected = it }
                expected.setLocation(window.contentPane.locationOnScreen)
                expected.translate(160, 100)
                menu.show(window.contentPane, 160, 100)
                check(menu.locationOnScreen == expected) { "Menu opened away from its anchor" }
                check(chart.isShowing) { "Menu hid the chart" }
                check(MenuSelectionManager.defaultManager().selectedPath.contains(menu))
                bounds.setBounds(SwingUtilities.getWindowAncestor(menu).bounds)
            }
            repeat(10) {
                Thread.sleep(50)
                SwingUtilities.invokeAndWait {
                    check(SwingUtilities.getWindowAncestor(menu).bounds == bounds) { "Popup moved or resized" }
                }
            }
            SwingUtilities.invokeAndWait {
                (menu.getComponent(0) as JMenuItem).doClick(0)
                check(selected == "1m" && !menu.isVisible) { "Menu selection did not update and dismiss" }
            }
        }
        SwingUtilities.invokeAndWait {
            check(firstVisibleBounds.isNotEmpty()) { "No native popup opening was observed" }
            check(firstVisibleBounds.all { it.location == expected && it.width > 0 && it.height > 0 }) {
                "Popup first appeared at an uninitialized location: $firstVisibleBounds"
            }
        }
        println("Chart popup smoke passed: initial position, repeated openings, stable bounds, selection and chart visibility")
    } finally {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        SwingUtilities.invokeAndWait { menu.isVisible = false; window.dispose(); chart.dispose() }
        Platform.exit()
    }
}
