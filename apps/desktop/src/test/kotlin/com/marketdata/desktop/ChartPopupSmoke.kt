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
import java.awt.event.MouseEvent
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
            menu.toggle(window.contentPane, 160, 100) {}
            check(menu.isVisible) { "Button did not open the dropdown" }
            menu.captureButtonPress()
            menu.isVisible = false
            menu.toggle(window.contentPane, 160, 100) {}
            check(!menu.isVisible) { "Button release reopened the dismissed dropdown" }
            menu.captureButtonPress()
            menu.toggle(window.contentPane, 160, 100) {}
            check(menu.isVisible) { "Next button click did not reopen the dropdown" }
            menu.toggle(window.contentPane, 160, 100) {}
            check(!menu.isVisible) { "Button did not close the open dropdown" }
        }
        val pressTime = System.currentTimeMillis()
        SwingUtilities.invokeAndWait {
            menu.toggle(window.contentPane, 160, 100) {}
            val queue = Toolkit.getDefaultToolkit().systemEventQueue
            queue.postEvent(MouseEvent(window.contentPane, MouseEvent.MOUSE_PRESSED,
                pressTime, MouseEvent.BUTTON1_DOWN_MASK, 160, 90, 1, false, MouseEvent.BUTTON1))
            queue.postEvent(MouseEvent(window.contentPane, MouseEvent.MOUSE_RELEASED,
                pressTime + 1, 0, 160, 90, 1, false, MouseEvent.BUTTON1))
        }
        SwingUtilities.invokeAndWait {
            check(!menu.isVisible) { "Native outside press did not dismiss the dropdown" }
            menu.captureButtonPress(pressTime)
            menu.toggle(window.contentPane, 160, 100) {}
            check(!menu.isVisible) { "Delayed short press reopened the dropdown after release" }
            menu.captureButtonPress(pressTime + 2)
            menu.toggle(window.contentPane, 160, 100) {}
            check(menu.isVisible) { "New press did not reopen the dropdown" }
            menu.isVisible = false
        }
        println("Chart popup smoke passed: initial position, repeated openings, stable bounds, selection and chart visibility")
    } finally {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        SwingUtilities.invokeAndWait { menu.isVisible = false; window.dispose(); chart.dispose() }
        Platform.runLater { Platform.exit() }
    }
}
