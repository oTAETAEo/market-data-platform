package com.marketdata.desktop

import com.marketdata.desktop.application.DesktopCompositionRoot
import com.marketdata.desktop.platform.window.configureDesktopWindow
import com.marketdata.desktop.platform.window.isMacOs
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.workspace.MarketWorkspace

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    // Allow Compose menus and clipping to render over the embedded JavaFX chart.
    System.setProperty("compose.interop.blending", "true")
    // Give popups their own input and focus surface above the JavaFX WebView.
    System.setProperty("compose.layers.type", "WINDOW")
    if (isMacOs) System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
    application {
        val desktop = remember { DesktopCompositionRoot.createApplication() }
        DisposableEffect(desktop) {
            onDispose { desktop.close() }
        }
        Window(
            onCloseRequest = {
                desktop.close()
                exitApplication()
            },
            title = "",
            state = rememberWindowState(width = 1440.dp, height = 940.dp)
        ) {
            DisposableEffect(window) {
                configureDesktopWindow(window)
                onDispose { }
            }
            MarketTheme {
                MarketWorkspace(desktop.viewModel, popupOwner = window.contentPane, titleBar = {
                    if (isMacOs) WindowDraggableArea {
                        Spacer(Modifier.fillMaxWidth().height(32.dp))
                    }
                })
            }
        }
    }
}
