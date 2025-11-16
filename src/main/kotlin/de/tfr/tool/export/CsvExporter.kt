package de.tfr.tool.export

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.percentOf
import de.tfr.tool.model.toTBString
import de.tfr.tool.ui.I18n
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
            2 -> p.sizeTB.toTBString()                 // Size
            3 -> p.usedTB.toTBString()                 // Used
            4 -> (p.sizeTB - p.usedTB).coerceAtLeast(0.0).toTBString() // Free
            5 -> {
                val pct = p.usedTB.percentOf(p.sizeTB)
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
            val needsQuotes = field.contains(';') || field.contains('\n') || field.contains('\r') || field.contains('"')
            var f = field
            if (field.contains('"')) {
                f = field.replace("\"", "\"\"")
            }
            return if (needsQuotes) "\"${f}\"" else f
        }

        val cols = table.columns
        // Header: additional first column "Disk" before the visible table columns
        val header = buildString {
            append(I18n.s("csv.header.disk"))
            if (cols.isNotEmpty()) append(';')
            append(cols.joinToString(";") { csvEscape(it.text ?: "") })
        }

        val lines = mutableListOf<String>()
        lines += header

        disks.forEach { d ->
            if (!showHidden && d.hidden) return@forEach
            d.partitions.forEach { p ->
                if (!showHidden && p.hidden) return@forEach
                val values = mutableListOf<String>()
                values += csvEscape(d.name) // first column: disk
                // Then the values for each visible table column (in current order)
                values += cols.mapIndexed { idx, _ -> csvEscape(cellTextForPartition(p, idx)) }
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
            Alert(AlertType.INFORMATION, I18n.s("alert.export.success", file.absolutePath)).showAndWait()
        } catch (ex: Exception) {
            Alert(AlertType.ERROR, I18n.s("alert.export.error", ex.message ?: "")).showAndWait()
        }
    }
}
