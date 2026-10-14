package com.marketdata.desktop.platform.settings

import java.util.prefs.Preferences

internal class AppearancePreferences(
    private val preferences: Preferences = Preferences.userRoot().node("com/marketdata/desktop/appearance")
) {
    fun isDark(): Boolean = preferences.getBoolean("dark", true)

    fun save(dark: Boolean) {
        preferences.putBoolean("dark", dark)
        preferences.flush()
    }
}
