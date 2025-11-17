package de.tfr.tool.ui

import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
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
        // Show the dialog non-modally first
        dialog.show()

        // Apply dark mode immediately after showing
        Platform.runLater {
            val window = dialog.dialogPane.scene?.window
            if (window is Stage) {
                CustomCaption.setImmersiveDarkMode(window, isDark)
            }
        }

        // Now wait for user interaction
        return dialog.showAndWait()
    }

    /**
     * Shows an Alert with correct dark mode title bar.
     * Returns the result of showAndWait().
     */
    fun showAlert(alert: Alert, isDark: Boolean): Optional<ButtonType> {
        // Show the alert non-modally first
        alert.show()

        // Apply dark mode immediately after showing
        Platform.runLater {
            val window = alert.dialogPane.scene?.window
            if (window is Stage) {
                CustomCaption.setImmersiveDarkMode(window, isDark)
            }
        }

        // Now wait for user interaction
        return alert.showAndWait()
    }
}

