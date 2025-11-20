package de.tfr.tool.ui.util

import de.tfr.tool.de.tfr.tool.ui.util.ThemeHelper
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.Dialog
import javafx.scene.image.Image
import javafx.stage.Stage
import javafx.stage.Window
import mu.KotlinLogging
import java.util.*

/**
 * Helper object for applying dark mode to dialogs.
 * Uses a reliable method with Platform.runLater after showing.
 */
object DialogHelper {

    private val logger = KotlinLogging.logger {}

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


    fun setWindowIcon(node: Node, iconName: String) {
        setWindowIcon(node.scene?.window, iconName)
    }

    fun setWindowIcon(windowStage: Window?, iconName: String) {
        // Set icon for dialog window
        windowStage?.let { window ->
            if (window is Stage) {
                try {
                    val iconStream = javaClass.getResourceAsStream("/$iconName")
                    if (iconStream != null) {
                        window.icons.add(Image(iconStream))
                    }
                } catch (_: Exception) {
                    logger.error("Failed to load icon: $iconName")
                }
            }
        }
    }

}