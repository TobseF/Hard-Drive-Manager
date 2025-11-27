package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.de.tfr.tool.ui.i18n.Language
import de.tfr.tool.de.tfr.tool.ui.theme.ThemeHelper
import de.tfr.tool.model.DisplayUnit
import de.tfr.tool.persist.Database
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import javafx.stage.Stage
import mu.KotlinLogging
import org.kordamp.ikonli.feather.Feather

data class AppSettings(
    val equalCardHeights: Boolean,
    val fixedCardHeightEnabled: Boolean,
    val fixedCardHeightPx: Double,
    val theme: Theme,
    val language: Language,
    val displayUnit: DisplayUnit,
    val dbPath: String?,
    val showHidden: Boolean
)

data class SettingsResult(
    val ok: Boolean,
    val settings: AppSettings,
    val dbPathChanged: Boolean,
    val dbCleared: Boolean,
    val columnVisibilityChanged: Boolean = false
)

object SettingsDialog {
    private val logger = KotlinLogging.logger {}

    fun show(current: AppSettings): SettingsResult {
        val dialog = Dialog<ButtonType>()
        val currentTheme = ThemeManager.currentTheme
        dialog.title = I18n.s("settings.title")
        dialog.headerText = null
        DialogHelper.setWindowIcon(dialog.dialogPane, "settings.png")

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

        val themeLabel = Label(I18n.s("settings.theme"))
        val themeBox = ComboBox<String>().apply {
            items.addAll(I18n.s("settings.theme.light"), I18n.s("settings.theme.dark"))
            selectionModel.select(if (current.theme == Theme.DARK) 1 else 0)
        }
        val rowTheme = HBox(8.0, themeLabel, themeBox).apply { alignment = Pos.CENTER_LEFT }

        val langLabel = Label(I18n.s("settings.language"))
        val langBox = ComboBox<String>().apply {
            items.addAll(I18n.s("settings.language.de"), I18n.s("settings.language.en"))
            selectionModel.select(if (current.language == Language.EN) 1 else 0)
        }
        val rowLang = HBox(8.0, langLabel, langBox).apply { alignment = Pos.CENTER_LEFT }

        val unitLabel = Label(I18n.s("settings.displayUnit"))
        val unitBox = ComboBox<String>().apply {
            items.addAll(
                I18n.s("settings.displayUnit.tb"),
                I18n.s("settings.displayUnit.gb"),
                I18n.s("settings.displayUnit.mb")
            )
            selectionModel.select(
                when (current.displayUnit) {
                    DisplayUnit.MB -> 2
                    DisplayUnit.GB -> 1
                    else -> 0 // TB
                }
            )
        }
        val rowUnit = HBox(8.0, unitLabel, unitBox).apply { alignment = Pos.CENTER_LEFT }

        var dbClearedFlag = false

        val dbRow = HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            val lbl = Label(I18n.s("settings.dbPath"))
            val pathField = TextField(current.dbPath ?: "")
            pathField.isEditable = false
            pathField.prefColumnCount = 18

            val btnBrowse = Button()
            btnBrowse.tooltip = Tooltip(I18n.s("settings.db.browse"))
            btnBrowse.graphic = currentTheme.createIcon(Feather.FOLDER)
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

            val btnClear = Button()
            btnClear.graphic = currentTheme.createIcon(Feather.TRASH_2)
            btnClear.tooltip = Tooltip(I18n.s("settings.db.clear"))
            btnClear.setOnAction {
                val alert = Alert(Alert.AlertType.CONFIRMATION)
                alert.title = I18n.s("alert.db.clear.confirm.title")
                alert.headerText = null
                alert.contentText = I18n.s("alert.db.clear.confirm.text")

                val confirmButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
                val cancelClearButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
                alert.buttonTypes.setAll(confirmButtonType, cancelClearButtonType)

                val resClear = DialogHelper.showDialog(alert, currentTheme == Theme.DARK)
                if (resClear.isPresent && resClear.get() == confirmButtonType) {
                    val dbPathStr = try { Database.getCurrentDbPath().toString() } catch (_: Exception) { "<unknown>" }
                    logger.info { "User confirmed clearing database at path: $dbPathStr" }
                    try {
                        val (disksDeleted, partsDeleted) = Database.clearAllData()
                        logger.info { "Database cleared successfully. Disks deleted=$disksDeleted, Partitions deleted=$partsDeleted" }
                        dbClearedFlag = true
                        DialogHelper.showDialog(
                            Alert(Alert.AlertType.INFORMATION, I18n.s("alert.db.clear.success", disksDeleted, partsDeleted)),
                            currentTheme == Theme.DARK
                        )
                    } catch (ex: Exception) {
                        logger.error(ex) { "Error while clearing database" }
                        DialogHelper.showDialog(
                            Alert(Alert.AlertType.ERROR, I18n.s("alert.db.clear.error", ex.message ?: "")),
                            currentTheme == Theme.DARK
                        )
                    }
                }
            }

            children += listOf(lbl, pathField, btnBrowse, btnClear)
        }

        val cbShowHidden = CheckBox(I18n.s("btn.showHidden")).apply {
            id = "showHiddenCheckBox"
            isSelected = current.showHidden
        }

        var columnVisibilityChanged = false
        val btnConfigureColumns = Button(I18n.s("table.columnVisibility.button")).apply {
            setOnAction {
                val result = ColumnVisibilityDialog.show()
                if (result != null) {
                    columnVisibilityChanged = true
                }
            }
        }
        val rowConfigColumns = HBox(8.0, btnConfigureColumns).apply { alignment = Pos.CENTER_LEFT }

        content.children += dbRow
        content.children += cbEqual
        content.children += rowFixed
        content.children += rowTheme
        content.children += rowLang
        content.children += rowUnit
        content.children += cbShowHidden
        content.children += Separator()
        content.children += rowConfigColumns
        dialog.dialogPane.content = content

        themeBox.selectionModel.selectedIndexProperty().addListener { _, _, newIdx ->
            val previewTheme = if (newIdx.toInt() == 1) Theme.DARK else Theme.LIGHT
            ThemeManager.setTheme(previewTheme)

            Platform.runLater {
                val window = dialog.dialogPane.scene?.window
                if (window is Stage) {
                    ThemeHelper.setDarkTitleBar(window, previewTheme)
                }
            }
        }

        langBox.selectionModel.selectedIndexProperty().addListener { _, _, newIdx ->
            val previewLang = if (newIdx.toInt() == 1) Language.EN else Language.DE
            I18n.setLanguage(previewLang)

            dialog.title = I18n.s("settings.title")
            cbEqual.text = I18n.s("settings.equalHeight")
            cbFixed.text = I18n.s("settings.fixedHeight")
            (rowFixed.children[1] as Label).text = I18n.s("settings.heightPx")
            themeLabel.text = I18n.s("settings.theme")
            val currentThemeSelection = themeBox.selectionModel.selectedIndex
            themeBox.items.setAll(I18n.s("settings.theme.light"), I18n.s("settings.theme.dark"))
            themeBox.selectionModel.select(currentThemeSelection)
            langLabel.text = I18n.s("settings.language")
            langBox.items.setAll(I18n.s("settings.language.de"), I18n.s("settings.language.en"))
            langBox.selectionModel.select(newIdx.toInt())
            unitLabel.text = I18n.s("settings.displayUnit")
            unitBox.items.setAll(
                I18n.s("settings.displayUnit.tb"),
                I18n.s("settings.displayUnit.gb"),
                I18n.s("settings.displayUnit.mb")
            )
            val currentUnitSelection = unitBox.selectionModel.selectedIndex
            unitBox.selectionModel.select(currentUnitSelection)
            (dbRow.children[0] as Label).text = I18n.s("settings.dbPath")
            (dbRow.children[2] as Button).text = I18n.s("settings.db.browse")
            (dbRow.children[3] as Button).text = I18n.s("settings.db.clear")
            cbShowHidden.text = I18n.s("btn.showHidden")
            btnConfigureColumns.text = I18n.s("table.columnVisibility.button")

            (dialog.dialogPane.lookupButton(okButtonType) as? Button)?.text = I18n.s("btn.ok")
            (dialog.dialogPane.lookupButton(cancelButtonType) as? Button)?.text = I18n.s("btn.cancel")
        }

        val res = DialogHelper.showDialog(dialog, current.theme == Theme.DARK)
        if (!res.isPresent || res.get() != okButtonType) {
            ThemeManager.setTheme(current.theme)
            I18n.setLanguage(current.language)
            return SettingsResult(false, current, false, dbClearedFlag, columnVisibilityChanged)
        }

        val raw = tfPx.text.trim().replace(',', '.')
        val px = raw.toDoubleOrNull()?.coerceAtLeast(50.0) ?: current.fixedCardHeightPx
        val selectedTheme = if (themeBox.selectionModel.selectedIndex == 1) Theme.DARK else Theme.LIGHT
        val selectedLang = if (langBox.selectionModel.selectedIndex == 1) Language.EN else Language.DE
        val selectedUnit = when (unitBox.selectionModel.selectedIndex) {
            1 -> DisplayUnit.GB
            2 -> DisplayUnit.MB
            else -> DisplayUnit.TB
        }

        val newDbPath = (dbRow.children[1] as TextField).text.trim()
        val dbChanged = (current.dbPath ?: "") != newDbPath

        val newSettings = AppSettings(
            equalCardHeights = cbEqual.isSelected,
            fixedCardHeightEnabled = cbFixed.isSelected,
            fixedCardHeightPx = px,
            theme = selectedTheme,
            language = selectedLang,
            displayUnit = selectedUnit,
            dbPath = newDbPath.ifBlank { null },
            showHidden = cbShowHidden.isSelected
        )
        return SettingsResult(true, newSettings, dbChanged, dbClearedFlag, columnVisibilityChanged)
    }
}
