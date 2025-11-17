package de.tfr.tool.ui

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.GridPane
import java.lang.module.ModuleDescriptor
import java.util.function.Function


/**
 * Simple system information dialog that lists key environment details.
 * The dialog presents a two-column table (Key / Value) and offers a "Copy"
 * action to copy the full list as plain text to the clipboard.
 */
class SystemInfoDialog : Dialog<ButtonType>(){
    init {
        title = I18n.s("dialog.sysinfo.title")
        headerText = null

        val systemInfo = loadSystemInfo()

        // Build a compact two-column grid with labels on the left and values on the right
        val grid = GridPane().apply {
            hgap = 12.0
            vgap = 8.0
            padding = Insets(10.0, 10.0, 0.0, 10.0)
        }

        val rows = listOf(
            I18n.s("sysinfo.os") to "${systemInfo.osName} ${systemInfo.osVersion} (${systemInfo.osArch})",
            I18n.s("sysinfo.java") to systemInfo.javaVersion,
            I18n.s("sysinfo.javafx") to systemInfo.javafxVersion
        )

        rows.forEachIndexed { index, (k, v) ->
            grid.add(Label(k).apply { style = "-fx-font-weight: bold;" }, 0, index)
            grid.add(Label(v), 1, index)
        }

        val copyButtonType = ButtonType(I18n.s("dialog.sysinfo.copy"), ButtonBar.ButtonData.LEFT)
        // Use explicit CLOSE button to underline close semantics
        val closeButtonType = ButtonType.CLOSE
        dialogPane.buttonTypes.setAll(copyButtonType, closeButtonType)
        dialogPane.content = grid

        // Style dialog according to current theme and keep it in sync
        styleDialogPane(dialogPane, ThemeManager.currentTheme)
        val themeListener: (Theme) -> Unit = { theme -> styleDialogPane(dialogPane, theme) }
        ThemeManager.addListener(themeListener)
        // Ensure we don't leak the listener after the dialog is closed
        setOnHidden { ThemeManager.removeListener(themeListener) }


        // Copy handler: join as lines "Key: Value" to clipboard
        (dialogPane.lookupButton(copyButtonType) as? Button)?.setOnAction {
            val text = rows.joinToString("\n") { (k, v) -> "$k: $v" }
            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(text)
            clipboard.setContent(content)
            Alert(AlertType.INFORMATION, I18n.s("dialog.sysinfo.copied")).apply {
                styleDialogPane(this.dialogPane, ThemeManager.currentTheme)
            }.showAndWait()
        }

        // Ensure that closing via window 'X' works even without selecting a button
        // (showAndWait will just return Optional.empty in that case)
        setResultConverter { dialogButton: ButtonType? -> dialogButton }
    }

    class SystemInfo(val osName: String, val osVersion: String, val osArch: String, val javaVersion: String, val javafxVersion: String)

    private fun loadSystemInfo(): SystemInfo{
        val osName = System.getProperty("os.name") ?: ""
        val osVersion = System.getProperty("os.version") ?: ""
        val osArch = System.getProperty("os.arch") ?: ""
        val javaVersion = System.getProperty("java.runtime.version") ?: System.getProperty("java.version") ?: ""
        val javafxVersion = Platform::class.java.module
            .descriptor
            .version()
            .map(Function { obj: ModuleDescriptor.Version? -> obj.toString() }) // Konvertiert java.lang.module.ModuleDescriptor.Version zu String
            .orElse("")
        return SystemInfo(osName, osVersion, osArch, javaVersion, javafxVersion)
    }

    private fun styleDialogPane(pane: DialogPane, theme: Theme) {
        val darkUrl = javaClass.getResource("/theme/dark.css")?.toExternalForm()
        if (darkUrl != null) {
            if (theme == Theme.DARK) {
                if (!pane.stylesheets.contains(darkUrl)) pane.stylesheets.add(darkUrl)
            } else {
                pane.stylesheets.remove(darkUrl)
            }
        }
    }
}