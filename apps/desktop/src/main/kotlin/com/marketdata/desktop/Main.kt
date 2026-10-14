package com.marketdata.desktop

import com.marketdata.desktop.application.DesktopCompositionRoot
import com.marketdata.desktop.platform.window.configureDesktopWindow
import com.marketdata.desktop.platform.window.isMacOs
import com.marketdata.desktop.platform.settings.AppearancePreferences
import com.marketdata.desktop.ui.theme.MarketTheme
import com.marketdata.desktop.ui.workspace.MarketWorkspace

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    val appearance = AppearancePreferences()
    if (isMacOs) System.setProperty("apple.awt.application.appearance",
        if (appearance.isDark()) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua")
    application {
        var dark by remember { mutableStateOf(appearance.isDark()) }
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
            DisposableEffect(window, dark) {
                configureDesktopWindow(window, dark)
                onDispose { }
            }
            MarketTheme(dark) {
                MarketWorkspace(desktop.viewModel, onToggleTheme = {
                    dark = !dark
                    runCatching { appearance.save(dark) }
                        .onFailure { System.err.println("Could not save appearance preference: ${it.message}") }
                }, titleBar = {
                    if (isMacOs) WindowDraggableArea {
                        Spacer(Modifier.fillMaxWidth().height(32.dp))
                    }
                })
            }
        }
    }
}
