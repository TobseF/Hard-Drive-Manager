package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.util.DialogHelper
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.GridPane
import java.lang.module.ModuleDescriptor
import java.util.*
import java.util.function.Function


/**
 * Simple system information dialog that lists key environment details.
 * The dialog presents a two-column table (Key / Value) and offers a "Copy"
 * action to copy the full list as plain text to the clipboard.
 */
class SystemInfoDialog : Dialog<ButtonType>(){
    private lateinit var copyButtonType: ButtonType
    private lateinit var closeButtonType: ButtonType
    private lateinit var grid: GridPane
    private val systemInfo: SystemInfo = loadSystemInfo()

    init {
        updateContent()

        // Listen to language changes and update the dialog content
        I18n.addListener { updateContent() }
    }

    private fun updateContent() {
        title = I18n.s("dialog.sysinfo.title")
        headerText = null

        // Build a compact two-column grid with labels on the left and values on the right
        grid = GridPane().apply {
            hgap = 12.0
            vgap = 8.0
            padding = Insets(10.0, 10.0, 0.0, 10.0)
        }

        val appVersion = System.getProperty("app.version") ?: ""
        val appTitle = I18n.s("app.title")

        val rows = listOf(
            I18n.s("app.version") to appVersion,
            I18n.s("sysinfo.os") to "${systemInfo.osName} ${systemInfo.osVersion} (${systemInfo.osArch})",
            I18n.s("sysinfo.java") to systemInfo.javaVersion,
            I18n.s("sysinfo.javafx") to systemInfo.javafxVersion
        )

        // Icon + App-Name in erster Zeile nebeneinander
        val iconStream = javaClass.getResourceAsStream("/icon.png")
        val titleLabel = Label(appTitle).apply { style = "-fx-font-size: 16px; -fx-font-weight: bold;" }
        if (iconStream != null) {
            val imageView = ImageView(Image(iconStream)).apply {
                fitWidth = 64.0
                fitHeight = 64.0
                isPreserveRatio = true
            }
            grid.add(imageView, 0, 0)
            grid.add(titleLabel, 1, 0)
        } else {
            // Fallback: nur Titel falls Icon fehlt
            grid.add(titleLabel, 0, 0, 2, 1)
        }


        // Check if buttons already exist, otherwise create them
        if (!::copyButtonType.isInitialized) {
            copyButtonType = ButtonType(I18n.s("dialog.sysinfo.copy"), ButtonBar.ButtonData.LEFT)
            closeButtonType = ButtonType(I18n.s("btn.close"), ButtonBar.ButtonData.CANCEL_CLOSE)
            dialogPane.buttonTypes.setAll(copyButtonType, closeButtonType)

            // Copy handler: join as lines "Key: Value" to clipboard
            (dialogPane.lookupButton(copyButtonType) as? Button)?.setOnAction {
                val text = rows.joinToString("\n") { (k, v) -> "$k: $v" }
                val clipboard = Clipboard.getSystemClipboard()
                val content = ClipboardContent()
                content.putString(text)
                clipboard.setContent(content)
                DialogHelper.showDialog(
                    Alert(AlertType.INFORMATION, I18n.s("dialog.sysinfo.copied")),
                    ThemeManager.currentTheme == Theme.DARK
                )
            }

            // Ensure that closing via window 'X' works even without selecting a button
            setResultConverter { dialogButton: ButtonType? -> dialogButton }
        } else {
            // Update button texts
            (dialogPane.lookupButton(copyButtonType) as? Button)?.text = I18n.s("dialog.sysinfo.copy")
            (dialogPane.lookupButton(closeButtonType) as? Button)?.text = I18n.s("btn.close")
        }

        rows.forEachIndexed { index, (k, v) ->
            val keyLabel = Label(k).apply { style = "-fx-font-weight: bold;" }
            grid.add(keyLabel, 0, 1 + index)
            grid.add(Label(v), 1, 1 + index)
        }

        dialogPane.content = grid
    }


    fun showDialog(): Optional<ButtonType> {
        DialogHelper.setWindowIcon(dialogPane, "info.png")
        return DialogHelper.showDialog(this, ThemeManager.currentTheme == Theme.DARK)
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
            .map(Function { obj: ModuleDescriptor.Version? -> obj.toString() })
            .orElse("")
        return SystemInfo(osName, osVersion, osArch, javaVersion, javafxVersion)
    }
}