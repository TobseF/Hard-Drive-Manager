package de.tfr.tool.de.tfr.tool.ui

import de.tfr.tool.ui.I18n
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.GridPane


/**
 * Simple system information dialog that lists key environment details.
 * The dialog presents a two-column table (Key / Value) and offers a "Copy"
 * action to copy the full list as plain text to the clipboard.
 */
class SystemInfoDialog : Dialog<Void>(){
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
        val closeButtonType = ButtonType.OK
        dialogPane.buttonTypes.setAll(copyButtonType, closeButtonType)
        dialogPane.content = grid

        // Copy handler: join as lines "Key: Value" to clipboard
        (dialogPane.lookupButton(copyButtonType) as? Button)?.setOnAction {
            val text = rows.joinToString("\n") { (k, v) -> "$k: $v" }
            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(text)
            clipboard.setContent(content)
            Alert(AlertType.INFORMATION, I18n.s("dialog.sysinfo.copied")).showAndWait()
        }
    }

    class SystemInfo(val osName: String, val osVersion: String, val osArch: String, val javaVersion: String, val javafxVersion: String)

    fun loadSystemInfo(): SystemInfo{
        val osName = System.getProperty("os.name") ?: ""
        val osVersion = System.getProperty("os.version") ?: ""
        val osArch = System.getProperty("os.arch") ?: ""
        val javaVersion = System.getProperty("java.runtime.version") ?: System.getProperty("java.version") ?: ""
        val javafxVersion = System.getProperty("javafx.runtime.version") ?: javafx.application.Platform::class.java.`package`?.implementationVersion ?: ""
        return SystemInfo(osName, osVersion, osArch, javaVersion, javafxVersion)
    }
}