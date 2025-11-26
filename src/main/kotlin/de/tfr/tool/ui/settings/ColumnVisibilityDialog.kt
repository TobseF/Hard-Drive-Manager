package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.persist.Settings
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * Dialog to configure which columns are visible in the table and their order using drag & drop.
 */
object ColumnVisibilityDialog {

    data class ColumnConfig(
        val id: String,
        val labelKey: String,
        var visible: Boolean
    )

    private var draggedItem: ColumnItemBox? = null

    fun show(): Map<String, Boolean>? {
        val dialog = Dialog<ButtonType>()
        dialog.title = I18n.s("table.columnVisibility.title")
        dialog.headerText = null
        DialogHelper.setWindowIcon(dialog.dialogPane, "settings.png")

        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        // Define all columns with default order
        val columns = mutableListOf(
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

        val checkboxes = mutableMapOf<String, CheckBox>()
        val mainVBox = VBox(6.0).apply {
            padding = Insets(12.0)
        }

        fun createColumnItem(col: ColumnConfig): ColumnItemBox {
            val checkbox = CheckBox(I18n.s(col.labelKey)).apply {
                isSelected = col.visible
            }
            checkboxes[col.id] = checkbox

            return ColumnItemBox(col, checkbox, columns, mainVBox)
        }

        // Create all column items
        columns.forEach { col ->
            mainVBox.children.add(createColumnItem(col))
        }

        // Wrap in ScrollPane
        val scrollPane = ScrollPane(mainVBox).apply {
            isFitToWidth = true
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            prefHeight = 500.0
            prefWidth = 240.0

            // Apply theme-aware background color
            val bgColor = if (ThemeManager.currentTheme == Theme.DARK) "#1e1e1e" else "#ffffff"
            style = "-fx-control-inner-background: $bgColor;"
        }

        dialog.dialogPane.content = scrollPane

        val res = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (!res.isPresent || res.get() != okButtonType) {
            return null
        }

        // Convert checkbox states to map and save to settings immediately
        val visibilityMap = mutableMapOf<String, Boolean>()
        columns.forEach { col ->
            visibilityMap[col.id] = (checkboxes[col.id]?.isSelected ?: col.visible)
        }

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

        // Save the column order
        saveColumnOrder(columns)

        return visibilityMap
    }

    private fun saveColumnOrder(columns: List<ColumnConfig>) {
        val order = columns.joinToString(",") { it.id }
        val prefs = java.util.prefs.Preferences.userRoot().node("de/tfr/tool/harddrivemanager")
        prefs.put("table.columnOrder", order)
    }

    fun getColumnOrder(): List<String> {
        val prefs = java.util.prefs.Preferences.userRoot().node("de/tfr/tool/harddrivemanager")
        val order = prefs.get("table.columnOrder", null)
        return if (order != null) {
            order.split(",")
        } else {
            // Default order
            listOf(
                "name", "type", "letter", "size", "used", "free", "percentText",
                "partOfDiskBar", "bar", "tag", "model", "manufacturer", "serial",
                "uuid", "fsType", "encrypted", "cloud", "virtual", "hidden"
            )
        }
    }

    /**
     * Custom draggable column item box for the dialog
     */
    private class ColumnItemBox(
        private val columnConfig: ColumnConfig,
        private val checkbox: CheckBox,
        private val allColumns: MutableList<ColumnConfig>,
        private val parentVBox: VBox
    ) : VBox(6.0) {

        init {
            padding = Insets(2.0)

            // Apply theme-aware colors
            val isDarkTheme = ThemeManager.currentTheme == Theme.DARK
            val backgroundColor = if (isDarkTheme) "#2a2a2a" else "#fafafa"
            val borderColor = if (isDarkTheme) "#404040" else "#e0e0e0"
            val handleColor = if (isDarkTheme) "#666666" else "#999999"

            style =
                "-fx-border-color: $borderColor; -fx-border-radius: 4; -fx-background-color: $backgroundColor; -fx-cursor: move;"

            // Drag handle zone
            val dragHandle = Label("≡").apply {
                font = javafx.scene.text.Font.font("Arial", 16.0)
                style = "-fx-text-fill: $handleColor; -fx-padding: 0 8 0 0;"
            }

            // Content area
            val contentBox = HBox(8.0).apply {
                alignment = Pos.CENTER_LEFT
                children.addAll(dragHandle, checkbox)
                HBox.setHgrow(checkbox, Priority.ALWAYS)
            }

            children.add(contentBox)

            // Setup drag & drop
            setupDragAndDrop()
        }

        private fun setupDragAndDrop() {
            // On drag started
            setOnDragDetected { event ->
                draggedItem = this@ColumnItemBox
                val db = startDragAndDrop(TransferMode.MOVE)
                val content = ClipboardContent()
                content.putString(columnConfig.id)
                db.setContent(content)
                event.consume()
            }

            // Allow drop
            setOnDragOver { event ->
                if (event.dragboard.hasString() && draggedItem != null) {
                    event.acceptTransferModes(TransferMode.MOVE)
                    event.consume()
                }
            }

            // Handle drop
            setOnDragDropped { event ->
                val db = event.dragboard
                if (db.hasString() && draggedItem != null) {
                    val draggedId = db.string
                    val draggedConfig = allColumns.find { it.id == draggedId }
                    val thisIndex = allColumns.indexOf(columnConfig)
                    val draggedIndex = allColumns.indexOf(draggedConfig)

                    if (draggedIndex != -1 && thisIndex != -1 && draggedIndex != thisIndex) {
                        // Swap elements
                        allColumns[draggedIndex] = columnConfig
                        allColumns[thisIndex] = draggedConfig!!

                        // Rebuild the UI
                        parentVBox.children.clear()
                        allColumns.forEach { col ->
                            val newCheckbox = CheckBox(I18n.s(col.labelKey)).apply {
                                isSelected = col.visible
                                // Set text color for Dark Theme
                                if (ThemeManager.currentTheme == Theme.DARK) {
                                    style = "-fx-text-fill: #e0e0e0;"
                                }
                            }
                            parentVBox.children.add(ColumnItemBox(col, newCheckbox, allColumns, parentVBox))
                        }
                    }

                    event.isDropCompleted = true
                    event.consume()
                }
            }

            setOnDragDone { event ->
                draggedItem = null
                event.consume()
            }
        }
    }
}

