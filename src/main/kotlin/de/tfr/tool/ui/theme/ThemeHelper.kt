package de.tfr.tool.de.tfr.tool.ui.theme

import de.tfr.tool.ui.Theme
import javafx.stage.Stage
import net.yetihafen.javafx.customcaption.CustomCaption

object ThemeHelper {
    fun setDarkTitleBar(stage: Stage, theme: Theme) {
        setDarkTitleBar(stage, theme == Theme.DARK)
    }

    fun setDarkTitleBar(stage: Stage, dark: Boolean) {
        if ((System.getProperty("os.name") ?: "").lowercase().contains("windows")) {
            CustomCaption.setImmersiveDarkMode(stage, dark)
        }
    }
}