package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.ThemeHelper
import de.tfr.tool.de.tfr.tool.ui.util.DialogHelper
import de.tfr.tool.persist.Database
import de.tfr.tool.ui.I18n
import de.tfr.tool.ui.Language
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import mu.KotlinLogging

data class AppSettings(
    val equalCardHeights: Boolean,
    val fixedCardHeightEnabled: Boolean,
    val fixedCardHeightPx: Double,
    val theme: Theme,
    val language: Language,
    val dbPath: String?,
    val showHidden: Boolean
)

data class SettingsResult(
    val ok: Boolean,
    val settings: AppSettings,
    val dbPathChanged: Boolean,
    val dbCleared: Boolean
)

object SettingsDialog {
    // Logger for settings-related actions
    private val logger = KotlinLogging.logger {}

    fun show(current: AppSettings): SettingsResult {
        val dialog = Dialog<ButtonType>()
        dialog.title = I18n.s("settings.title")
        dialog.headerText = null
        DialogHelper.setWindowIcon(dialog.dialogPane, "settings.png")

        // Create custom button types with translated texts
        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        val content = VBox(12.0).apply { padding = Insets(10.0) }


        val cbEqual = CheckBox(I18n.s("settings.equalHeight")).apply { isSelected = current.equalCardHeights }
        val cbFixed = CheckBox(I18n.s("settings.fixedHeight")).apply { isSelected = current.fixedCardHeightEnabled }
        val tfPx = TextField(String.format("%.0f", current.fixedCardHeightPx)).apply {
            prefColumnCount = 6
            isDisable = !cbFixed.isSelected
        }
        cbFixed.selectedProperty().addListener { _, _, new -> tfPx.isDisable = !new }

        // Exclusivity of the two checkboxes (UI level)
        var guard = false
        cbEqual.selectedProperty().addListener { _, _, new ->
            if (guard) return@addListener
            if (new) { guard = true; cbFixed.isSelected = false; guard = false }
        }
        cbFixed.selectedProperty().addListener { _, _, new ->
            if (guard) return@addListener
            if (new) { guard = true; cbEqual.isSelected = false; guard = false }
        }

        val rowFixed = HBox(8.0, cbFixed, Label(I18n.s("settings.heightPx")), tfPx).apply { alignment = Pos.CENTER_LEFT }

        // Theme selection with live preview in the dialog
        val themeLabel = Label(I18n.s("settings.theme"))
        val themeBox = ComboBox<String>().apply {
            items.addAll(I18n.s("settings.theme.light"), I18n.s("settings.theme.dark"))
            selectionModel.select(if (current.theme == Theme.DARK) 1 else 0)
        }
        val rowTheme = HBox(8.0, themeLabel, themeBox).apply { alignment = Pos.CENTER_LEFT }

        // Language selection
        val langLabel = Label(I18n.s("settings.language"))
        val langBox = ComboBox<String>().apply {
            items.addAll(I18n.s("settings.language.de"), I18n.s("settings.language.en"))
            selectionModel.select(if (current.language == Language.EN) 1 else 0)
        }
        val rowLang = HBox(8.0, langLabel, langBox).apply { alignment = Pos.CENTER_LEFT }

        // Tracks whether user cleared the DB while dialog was open
        var dbClearedFlag = false

        // DB file selection (moved to top) and clear button
        val dbRow = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            val lbl = Label(I18n.s("settings.dbPath"))
            val pathField = TextField(current.dbPath ?: "")
            pathField.isEditable = false
            pathField.prefColumnCount = 18

            // Browse button to pick a different DB file
            val btnBrowse = Button(I18n.s("settings.db.browse"))
            btnBrowse.setOnAction {
                val chooser = FileChooser()
                chooser.title = I18n.s("settings.db.browse")
                chooser.extensionFilters.addAll(
                    FileChooser.ExtensionFilter(I18n.s("file.filter.sqlite"), "*.db", "*.sqlite"),
                    FileChooser.ExtensionFilter(I18n.s("file.filter.all"), "*.*")
                )
                val file = chooser.showOpenDialog(dialog.dialogPane.scene?.window)
                if (file != null) pathField.text = file.absolutePath
            }

            // Clear DB button to remove all data from the current database
            val btnClear = Button(I18n.s("settings.db.clear"))
            btnClear.setOnAction {
                val alert = Alert(Alert.AlertType.CONFIRMATION)
                alert.title = I18n.s("alert.db.clear.confirm.title")
                alert.headerText = null
                alert.contentText = I18n.s("alert.db.clear.confirm.text")

                // Replace default buttons with translated ones
                val confirmButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
                val cancelClearButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
                alert.buttonTypes.setAll(confirmButtonType, cancelClearButtonType)

                // Adapt dialog styling to the current theme
                val resClear = DialogHelper.showDialog(alert, ThemeManager.currentTheme == Theme.DARK)
                if (resClear.isPresent && resClear.get() == confirmButtonType) {
                    // Log user-confirmed database clear action
                    val dbPathStr = try { Database.getCurrentDbPath().toString() } catch (_: Exception) { "<unknown>" }
                    logger.info { "User confirmed clearing database at path: $dbPathStr" }
                    try {
                        val (disksDeleted, partsDeleted) = Database.clearAllData()
                        logger.info { "Database cleared successfully. Disks deleted=$disksDeleted, Partitions deleted=$partsDeleted" }
                        // Mark that DB has been cleared so caller can react after OK
                        dbClearedFlag = true
                        // Note: We cannot reload UI from here; caller should update UI if needed
                        DialogHelper.showDialog(
                            Alert(Alert.AlertType.INFORMATION, I18n.s("alert.db.clear.success", disksDeleted, partsDeleted)),
                            ThemeManager.currentTheme == Theme.DARK
                        )
                        // Path remains unchanged; do not auto-seed here
                    } catch (ex: Exception) {
                        logger.error(ex) { "Error while clearing database" }
                        DialogHelper.showDialog(
                            Alert(Alert.AlertType.ERROR, I18n.s("alert.db.clear.error", ex.message ?: "")),
                            ThemeManager.currentTheme == Theme.DARK
                        )
                    }
                }
            }

            children += listOf(lbl, pathField, btnBrowse, btnClear)
        }

        // Toggle "Show hidden" (optionally show/change it in the dialog)
        val cbShowHidden = CheckBox(I18n.s("btn.showHidden")).apply { isSelected = current.showHidden }

        // Put DB selection at the very top
        content.children += dbRow
        content.children += cbEqual
        content.children += rowFixed
        content.children += rowTheme
        content.children += rowLang
        content.children += cbShowHidden
        dialog.dialogPane.content = content

        // Live preview on theme change inside the dialog
        themeBox.selectionModel.selectedIndexProperty().addListener { _, _, newIdx ->
            val previewTheme = if (newIdx.toInt() == 1) Theme.DARK else Theme.LIGHT
            ThemeManager.setTheme(previewTheme)

            // Update dialog title bar for the new theme
            Platform.runLater {
                val window = dialog.dialogPane.scene?.window
                if (window is Stage) {
                    ThemeHelper.setDarkTitleBar(window, previewTheme)
                }
            }
        }

        // Live preview on language change inside the dialog
        langBox.selectionModel.selectedIndexProperty().addListener { _, _, newIdx ->
            val previewLang = if (newIdx.toInt() == 1) Language.EN else Language.DE
            I18n.setLanguage(previewLang)

            // Update all labels and buttons with new language
            dialog.title = I18n.s("settings.title")
            cbEqual.text = I18n.s("settings.equalHeight")
            cbFixed.text = I18n.s("settings.fixedHeight")
            (rowFixed.children[1] as Label).text = I18n.s("settings.heightPx")
            themeLabel.text = I18n.s("settings.theme")
            val currentThemeSelection = themeBox.selectionModel.selectedIndex
            themeBox.items.setAll(I18n.s("settings.theme.light"), I18n.s("settings.theme.dark"))
            themeBox.selectionModel.select(currentThemeSelection) // Keep current selection
            langLabel.text = I18n.s("settings.language")
            langBox.items.setAll(I18n.s("settings.language.de"), I18n.s("settings.language.en"))
            langBox.selectionModel.select(newIdx.toInt()) // Keep current selection
            (dbRow.children[0] as Label).text = I18n.s("settings.dbPath")
            (dbRow.children[2] as Button).text = I18n.s("settings.db.browse")
            (dbRow.children[3] as Button).text = I18n.s("settings.db.clear")
            cbShowHidden.text = I18n.s("btn.showHidden")

            // Update button texts
            (dialog.dialogPane.lookupButton(okButtonType) as? Button)?.text = I18n.s("btn.ok")
            (dialog.dialogPane.lookupButton(cancelButtonType) as? Button)?.text = I18n.s("btn.cancel")
        }

        val res = DialogHelper.showDialog(dialog, current.theme == Theme.DARK)
        if (!res.isPresent || res.get() != okButtonType) {
            // Restore original theme and language if user cancelled
            ThemeManager.setTheme(current.theme)
            I18n.setLanguage(current.language)
            return SettingsResult(false, current, false, dbClearedFlag)
        }

        val raw = tfPx.text.trim().replace(',', '.')
        val px = raw.toDoubleOrNull()?.coerceAtLeast(50.0) ?: current.fixedCardHeightPx
        val selectedTheme = if (themeBox.selectionModel.selectedIndex == 1) Theme.DARK else Theme.LIGHT
        val selectedLang = if (langBox.selectionModel.selectedIndex == 1) Language.EN else Language.DE

        val newDbPath = (dbRow.children[1] as TextField).text.trim()
        val dbChanged = (current.dbPath ?: "") != newDbPath

        val newSettings = AppSettings(
            equalCardHeights = cbEqual.isSelected,
            fixedCardHeightEnabled = cbFixed.isSelected,
            fixedCardHeightPx = px,
            theme = selectedTheme,
            language = selectedLang,
            dbPath = newDbPath.ifBlank { null },
            showHidden = cbShowHidden.isSelected
        )
        return SettingsResult(true, newSettings, dbChanged, dbClearedFlag)
    }

}
