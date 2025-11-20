package de.tfr.tool.export

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.formatSize
import de.tfr.tool.model.percentOf
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.TreeTableView
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File

/**
 * CSV export of the table view.
 */
object CsvExporter {

    fun exportTableAsCsv(
        table: TreeTableView<Any>,
        disks: List<Disk>,
        showHidden: Boolean,
        owner: Window?
    ) {
        if (disks.isEmpty()) {
            Alert(AlertType.INFORMATION, I18n.s("alert.info.noTable")).showAndWait()
            return
        }

        // Helper: determine cell content analogous to the UI (for partitions)
        fun cellTextForPartition(p: Partition, colIndex: Int): String = when (colIndex) {
            // Columns from the table (excluding the additionally inserted first column "Disk")
            0 -> p.name                                // Name
            1 -> p.type                                // Type
            2 -> p.sizeMB.formatSize()                 // Size
            3 -> p.usedMB.formatSize()                 // Used
            4 -> (p.sizeMB - p.usedMB).coerceAtLeast(0.0).formatSize() // Free
            5 -> {
                val pct = p.usedMB.percentOf(p.sizeMB)
                String.format("%d %%", Math.round(pct * 100))
            }                                         // % Used
            6 -> ""                                   // Size/Disk (bar)
            7 -> ""                                   // Used (bar)
            8 -> p.tags                               // Tag(s)
            9 -> p.letter                             // Letter
            10 -> ""                                  // Model (partitions: empty)
            11 -> ""                                  // Manufacturer (partitions: empty)
            12 -> ""                                  // Serial (partitions: empty)
            13 -> p.uuid                              // UUID
            14 -> p.fsType                            // Filesystem type
            15 -> p.encrypted.toString()              // Encrypted
            16 -> p.cloudBackup.toString()            // Cloud backup
            17 -> p.hidden.toString()                 // Hidden
            else -> ""
        }

        fun csvEscape(field: String): String {
            return if (field.contains(";") || field.contains("\"") || field.contains("\n")) {
                "\"" + field.replace("\"", "\"\"") + "\""
            } else {
                field
            }
        }

        // Build header row
        val cols = listOf(
            I18n.s("col.disk"), I18n.s("col.name"), I18n.s("col.type"), I18n.s("col.size"),
            I18n.s("col.used"), I18n.s("col.free"), I18n.s("col.percentUsed"), "",
            "", I18n.s("col.tag"), I18n.s("col.letter"), I18n.s("col.model"),
            I18n.s("col.manufacturer"), I18n.s("col.serial"), I18n.s("col.uuid"),
            I18n.s("col.fsType"), I18n.s("col.encrypted"), I18n.s("col.cloudBackup"), I18n.s("col.hidden")
        )
        val lines = mutableListOf<String>()
        lines += cols.map { csvEscape(it) }.joinToString(";")

        // Data rows
        disks.forEach { d ->
            if (!showHidden && d.hidden) return@forEach
            d.partitions.forEach { p ->
                if (!showHidden && p.hidden) return@forEach
                val values = mutableListOf<String>()
                values += csvEscape(d.name) // first column: disk
                // Then the values for each visible table column (in current order)
                values += cols.indices.drop(1).map { idx -> csvEscape(cellTextForPartition(p, idx - 1)) }
                lines += values.joinToString(";")
            }
        }

        val chooser = FileChooser()
        chooser.title = I18n.s("alert.export.table.title")
        chooser.extensionFilters.add(FileChooser.ExtensionFilter(I18n.s("file.filter.csv"), "*.csv"))
        chooser.initialFileName = I18n.s("file.name.table")
        val file: File = chooser.showSaveDialog(owner) ?: return
        try {
            file.writeText(lines.joinToString(System.lineSeparator()), Charsets.UTF_8)
            DialogHelper.showDialog(
                Alert(AlertType.INFORMATION, I18n.s("alert.export.success", file.absolutePath)),
                ThemeManager.currentTheme == Theme.DARK
            )
        } catch (ex: Exception) {
            DialogHelper.showDialog(
                Alert(AlertType.ERROR, I18n.s("alert.export.error", ex.message ?: "")),
                ThemeManager.currentTheme == Theme.DARK
            )
        }
    }
}

