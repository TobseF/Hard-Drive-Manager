package de.tfr.tool.ui.util

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.de.tfr.tool.ui.theme.ThemeHelper
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.VBox
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

    fun showCommentDialog(
        initial: String,
        titleKey: String,
        promptKey: String,
        validator: (String) -> String? = { null },
        maxLength: Int = 500
    ): String? {
        val dialog = Dialog<String>()
        dialog.title = I18n.s(titleKey)
        dialog.headerText = null
        val okButton = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        dialog.dialogPane.buttonTypes.setAll(okButton, ButtonType.CANCEL)
        val textArea = TextArea(initial).apply {
            promptText = I18n.s(promptKey)
            prefRowCount = 5
            isWrapText = true
        }
        val errorLabel = Label().apply {
            style = "-fx-text-fill: #cc3333;"
            isVisible = false
        }
        val container = VBox(8.0, textArea, errorLabel)
        container.padding = Insets(10.0)
        dialog.dialogPane.content = container
        val okNode = dialog.dialogPane.lookupButton(okButton)
        fun validateInput() {
            val trimmed = textArea.text.trim()
            val lengthError = if (trimmed.length > maxLength) I18n.s("validation.comment.tooLong", maxLength) else null
            val customError = if (lengthError == null) validator(trimmed) else null
            val msg = lengthError ?: customError
            if (msg == null) {
                errorLabel.isVisible = false
                okNode.isDisable = false
            } else {
                errorLabel.text = msg
                errorLabel.isVisible = true
                okNode.isDisable = true
            }
        }
        textArea.textProperty().addListener { _, _, _ -> validateInput() }
        validateInput()
        dialog.setResultConverter { button -> if (button == okButton) textArea.text.trim() else null }
        val result = showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        return if (result.isPresent) result.get() else null
    }
}