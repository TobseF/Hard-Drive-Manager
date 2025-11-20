package de.tfr.tool.ui

import javafx.application.Application
import java.util.concurrent.CopyOnWriteArrayList

object ThemeManager {
    private val listeners = CopyOnWriteArrayList<(Theme) -> Unit>()

    @Volatile
    var currentTheme: Theme = Theme.LIGHT
        private set

    fun setTheme(theme: Theme) {
        if (theme == currentTheme) return
        currentTheme = theme
        // Apply AtlantaFX theme globally
        Application.setUserAgentStylesheet(theme.atlantaFxTheme.userAgentStylesheet)
        listeners.forEach { it(theme) }
    }

    fun addListener(listener: (Theme) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (Theme) -> Unit) {
        listeners -= listener
    }
}
