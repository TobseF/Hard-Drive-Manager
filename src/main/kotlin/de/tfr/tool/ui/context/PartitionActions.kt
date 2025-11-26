package de.tfr.tool.ui.context

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.formatSize
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import de.tfr.tool.ui.util.TabTableNameFormatter
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.util.StringConverter

object PartitionActions {
    fun confirmDeletePartition(partition: Partition, onRefresh: () -> Unit) {
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = I18n.s("alert.delete.title")
        alert.headerText = null
        alert.contentText = I18n.s("alert.delete.askPartition", partition.name)

        val confirmButton = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButton = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        alert.buttonTypes.setAll(confirmButton, cancelButton)

        val result = DialogHelper.showDialog(alert, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent && result.get() == confirmButton) {
            DiskRepository.deletePartition(partition.id)
            onRefresh()
        }
    }

    fun movePartition(partition: Partition, onRefresh: () -> Unit) {
        val allDisks = DiskRepository.loadAll()
        if (allDisks.isEmpty()) {
            Alert(Alert.AlertType.WARNING, I18n.s("alert.add.partition.selectDisk")).showAndWait()
            return
        }

        val dialog = Dialog<Disk>()
        dialog.title = I18n.s("alert.move.partition.title")
        dialog.headerText = I18n.s("alert.move.partition.label")

        val diskCombo = ComboBox<Disk>()
        diskCombo.items.setAll(allDisks)
        diskCombo.converter = object : StringConverter<Disk>() {
            override fun toString(disk: Disk?): String {
                if (disk == null) return ""
                val partitionNames = disk.partitions.joinToString(", ") { it.name }
                val size = disk.sizeMB.formatSize()
                return if (partitionNames.isNotEmpty()) {
                    "${disk.name} - $size ($partitionNames)"
                } else {
                    disk.name + " - " + size
                }
            }

            override fun fromString(string: String?): Disk? {
                val diskName = string?.substringBefore(" (")?.trim() ?: return null
                return allDisks.find { it.name == diskName }
            }
        }

        val currentDisk = allDisks.find { it.id == partition.diskId }
        diskCombo.value = currentDisk ?: allDisks.firstOrNull()

        val content = VBox(10.0)
        content.padding = Insets(10.0)
        content.children.add(diskCombo)
        dialog.dialogPane.content = content

        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        dialog.setResultConverter { buttonType ->
            if (buttonType == okButtonType) diskCombo.value else null
        }

        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val targetDisk = result.get()
            if (targetDisk.id != partition.diskId) {
                try {
                    val partitionName = partition.name
                    val targetDiskName = targetDisk.name
                    partition.diskId = targetDisk.id
                    DiskRepository.updatePartition(partition)
                    onRefresh()

                    val successAlert = Alert(Alert.AlertType.INFORMATION)
                    successAlert.title = I18n.s("alert.move.partition.title")
                    successAlert.headerText = null
                    successAlert.contentText = I18n.s("alert.move.partition.success", partitionName, targetDiskName)
                    DialogHelper.showDialog(successAlert, ThemeManager.currentTheme == Theme.DARK)
                } catch (e: Exception) {
                    val errorAlert = Alert(Alert.AlertType.ERROR)
                    errorAlert.title = I18n.s("alert.move.partition.title")
                    errorAlert.headerText = null
                    errorAlert.contentText = I18n.s("alert.move.partition.error", e.message ?: "Unknown error")
                    DialogHelper.showDialog(errorAlert, ThemeManager.currentTheme == Theme.DARK)
                }
            }
        }
    }

    fun renamePartition(partition: Partition, onRefresh: () -> Unit) {
        val dialog = TextInputDialog(partition.name)
        dialog.title = I18n.s("dialog.rename.partition.title")
        dialog.headerText = null
        dialog.contentText = I18n.s("dialog.rename.partition.prompt")
        dialog.editor.textFormatter = TabTableNameFormatter.create()
        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.disableProperty().bind(dialog.editor.textProperty().isEmpty)
        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val newName = result.get().trim()
            if (newName.isNotEmpty() && newName != partition.name) {
                partition.name = newName
                DiskRepository.updatePartition(partition)
                onRefresh()
            }
        }
    }
}
