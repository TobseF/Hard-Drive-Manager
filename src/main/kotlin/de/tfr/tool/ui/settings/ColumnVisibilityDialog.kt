package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.persist.Settings
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.VBox

/**
 * Dialog to configure which columns are visible in the table.
 */
object ColumnVisibilityDialog {

    data class ColumnConfig(
        val id: String,
        val labelKey: String,
        var visible: Boolean
    )

    fun show(): Map<String, Boolean>? {
        val dialog = Dialog<ButtonType>()
        dialog.title = I18n.s("table.columnVisibility.title")
        dialog.headerText = null
        DialogHelper.setWindowIcon(dialog.dialogPane, "settings.png")

        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        // Define all columns
        val columns = listOf(
            ColumnConfig("name", "col.name", Settings.Table.showName),
            ColumnConfig("type", "col.type", Settings.Table.showType),
            ColumnConfig("letter", "col.letter", Settings.Table.showLetter),
            ColumnConfig("size", "col.size", Settings.Table.showSize),
            ColumnConfig("used", "col.used", Settings.Table.showUsed),
            ColumnConfig("free", "col.free", Settings.Table.showFree),
            ColumnConfig("percentText", "col.percentUsed", Settings.Table.showPercentText),
            ColumnConfig("partOfDiskBar", "col.sizeOfDiskBar", Settings.Table.showPartOfDiskBar),
            ColumnConfig("bar", "col.usedBar", Settings.Table.showBar),
            ColumnConfig("tag", "col.tags", Settings.Table.showTag),
            ColumnConfig("model", "col.model", Settings.Table.showModel),
            ColumnConfig("manufacturer", "col.manufacturer", Settings.Table.showManufacturer),
            ColumnConfig("serial", "col.serial", Settings.Table.showSerial),
            ColumnConfig("uuid", "col.uuid", Settings.Table.showUuid),
            ColumnConfig("fsType", "col.fsType", Settings.Table.showFsType),
            ColumnConfig("encrypted", "col.encrypted", Settings.Table.showEncrypted),
            ColumnConfig("cloud", "col.cloud", Settings.Table.showCloud),
            ColumnConfig("virtual", "col.virtual", Settings.Table.showVirtual),
            ColumnConfig("hidden", "col.hidden", Settings.Table.showHiddenCol)
        )

        // Create checkboxes
        val checkboxes = mutableMapOf<String, CheckBox>()
        val vbox = VBox(6.0).apply {
            padding = Insets(12.0)
        }

        columns.forEach { col ->
            val checkbox = CheckBox(I18n.s(col.labelKey)).apply {
                isSelected = col.visible
            }
            checkboxes[col.id] = checkbox
            vbox.children.add(checkbox)
        }

        // Wrap in ScrollPane
        val scrollPane = ScrollPane(vbox).apply {
            isFitToWidth = true
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            prefHeight = 400.0
        }

        dialog.dialogPane.content = scrollPane

        val res = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (!res.isPresent || res.get() != okButtonType) {
            return null
        }

        // Convert checkbox states to map and save to settings immediately
        val visibilityMap = checkboxes.mapValues { it.value.isSelected }

        Settings.Table.showName = visibilityMap["name"] ?: true
        Settings.Table.showType = visibilityMap["type"] ?: true
        Settings.Table.showLetter = visibilityMap["letter"] ?: true
        Settings.Table.showSize = visibilityMap["size"] ?: true
        Settings.Table.showUsed = visibilityMap["used"] ?: true
        Settings.Table.showFree = visibilityMap["free"] ?: true
        Settings.Table.showPercentText = visibilityMap["percentText"] ?: true
        Settings.Table.showPartOfDiskBar = visibilityMap["partOfDiskBar"] ?: true
        Settings.Table.showBar = visibilityMap["bar"] ?: true
        Settings.Table.showTag = visibilityMap["tag"] ?: true
        Settings.Table.showModel = visibilityMap["model"] ?: false
        Settings.Table.showManufacturer = visibilityMap["manufacturer"] ?: false
        Settings.Table.showSerial = visibilityMap["serial"] ?: false
        Settings.Table.showUuid = visibilityMap["uuid"] ?: false
        Settings.Table.showFsType = visibilityMap["fsType"] ?: false
        Settings.Table.showEncrypted = visibilityMap["encrypted"] ?: true
        Settings.Table.showCloud = visibilityMap["cloud"] ?: true
        Settings.Table.showVirtual = visibilityMap["virtual"] ?: false
        Settings.Table.showHiddenCol = visibilityMap["hidden"] ?: true

        return visibilityMap
    }
}

