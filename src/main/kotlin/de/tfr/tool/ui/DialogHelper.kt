package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.ThemeHelper
import javafx.application.Platform
import javafx.scene.control.Dialog
import javafx.stage.Stage
import net.yetihafen.javafx.customcaption.CustomCaption
import java.util.*

/**
 * Helper object for applying dark mode to dialogs.
 * Uses a reliable method with Platform.runLater after showing.
 */
object DialogHelper {

    /**
     * Shows a Dialog with correct dark mode title bar.
     * Returns the result of showAndWait().
     */
    fun <R> showDialog(dialog: Dialog<R>, isDark: Boolean): Optional<R> {
        // Apply dark mode immediately after showing
        Platform.runLater {
            val window = dialog.dialogPane.scene?.window
            if (window is Stage) {
                ThemeHelper.setDarkTitleBar(window, isDark)
            }
        }

        // Now wait for user interaction
        return dialog.showAndWait()
    }

}

