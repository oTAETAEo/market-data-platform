package com.marketdata.desktop.ui.components

import androidx.compose.ui.graphics.toArgb
import com.marketdata.desktop.ui.theme.DeskColors
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Font
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JCheckBoxMenuItem
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI

internal class NativeDropdownMenu : JPopupMenu() {
    private var closeOnToggle = false
    private var dismissalPressTime = Long.MIN_VALUE

    init {
        // Native popups are positioned before showing and stay above the JavaFX chart.
        isLightWeightPopupEnabled = false
        background = Color(DeskColors.elevated.toArgb(), true)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(DeskColors.line.toArgb(), true)),
            BorderFactory.createEmptyBorder(6, 0, 6, 0)
        )
        addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit
            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                val mouseEvent = EventQueue.getCurrentEvent() as? MouseEvent
                if (mouseEvent?.id == MouseEvent.MOUSE_PRESSED) dismissalPressTime = mouseEvent.`when`
            }
        })
    }

    fun captureButtonPress(pressTime: Long? = null) {
        // Swing can dismiss the popup before Compose handles the same mouse press.
        closeOnToggle = isVisible || (pressTime != null && dismissalPressTime == pressTime)
    }

    fun toggle(owner: Component, x: Int, y: Int, prepare: () -> Unit) {
        val shouldClose = closeOnToggle || isVisible
        closeOnToggle = false
        if (shouldClose) {
            isVisible = false
        } else {
            prepare()
            show(owner, x, y)
        }
    }

    fun setOptions(value: String, values: List<String>, onChange: (String) -> Unit) {
        removeAll()
        values.forEach { option ->
            add(JCheckBoxMenuItem(option, option == value).apply {
                setUI(object : BasicCheckBoxMenuItemUI() {
                    override fun installDefaults() {
                        super.installDefaults()
                        selectionBackground = Color(DeskColors.hover.toArgb(), true)
                        selectionForeground = Color(DeskColors.text.toArgb(), true)
                    }
                })
                background = this@NativeDropdownMenu.background
                foreground = Color((if (option == value) DeskColors.green else DeskColors.text).toArgb(), true)
                font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
                border = BorderFactory.createEmptyBorder(10, 12, 10, 16)
                preferredSize = Dimension(preferredSize.width.coerceAtLeast(136), 42)
                addActionListener {
                    this@NativeDropdownMenu.isVisible = false
                    onChange(option)
                }
            })
        }
    }
}
