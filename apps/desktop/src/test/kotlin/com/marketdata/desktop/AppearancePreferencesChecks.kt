package com.marketdata.desktop

import com.marketdata.desktop.platform.settings.AppearancePreferences
import com.marketdata.desktop.ui.theme.deskPalette
import androidx.compose.ui.graphics.luminance
import java.util.UUID
import java.util.prefs.Preferences

fun main() {
    val node = Preferences.userRoot().node("com/marketdata/tests/appearance-${UUID.randomUUID()}")
    try {
        val appearance = AppearancePreferences(node)
        check(appearance.isDark())
        appearance.save(false)
        check(!AppearancePreferences(node).isDark())
        appearance.save(true)
        check(AppearancePreferences(node).isDark())
        for (dark in listOf(true, false)) {
            val palette = deskPalette(dark)
            for (foreground in listOf(palette.text, palette.muted, palette.green, palette.blue, palette.coral, palette.gold)) {
                for (background in listOf(palette.panel, palette.elevated)) {
                    val luminances = listOf(foreground.luminance(), background.luminance())
                    check((luminances.max() + 0.05) / (luminances.min() + 0.05) >= 4.5) {
                        "Insufficient text contrast in ${if (dark) "dark" else "light"} mode"
                    }
                }
            }
        }
        println("Appearance checks passed: saved preference, default dark mode and text contrast in both palettes")
    } finally {
        node.removeNode()
        Preferences.userRoot().flush()
    }
}
