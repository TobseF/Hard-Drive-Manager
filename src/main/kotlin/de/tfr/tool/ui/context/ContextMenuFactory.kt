package de.tfr.tool.ui.context

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import javafx.scene.control.*

object ContextMenuFactory {
    data class DiskCallbacks(
        val onDelete: () -> Unit,
        val onToggleHidden: (Boolean) -> Unit
    )

    data class PartitionCallbacks(
        val onDelete: () -> Unit,
        val onMove: () -> Unit,
        val onToggleEncrypted: (Boolean) -> Unit,
        val onToggleCloud: (Boolean) -> Unit,
        val onToggleVirtual: (Boolean) -> Unit,
        val onToggleHidden: (Boolean) -> Unit
    )

    fun createDiskMenu(disk: Disk, onDelete: () -> Unit, onToggleHidden: (Boolean) -> Unit): ContextMenu {
        val deleteItem = MenuItem(I18n.s("menu.context.delete")).apply {
            setOnAction { onDelete() }
        }

        val hiddenItem = CheckMenuItem(I18n.s("menu.context.hidden")).apply {
            isSelected = disk.hidden
            setOnAction {
                val newValue = isSelected
                if (disk.hidden != newValue) {
                    onToggleHidden(newValue)
                }
            }
        }

        val changeMenu = Menu(I18n.s("menu.context.change")).apply {
            items.setAll(hiddenItem)
        }

        return ContextMenu(deleteItem, SeparatorMenuItem(), changeMenu)
    }

    fun createPartitionMenu(partition: Partition, callbacks: PartitionCallbacks): ContextMenu {
        val deleteItem = MenuItem(I18n.s("menu.context.delete")).apply {
            setOnAction { callbacks.onDelete() }
        }

        val moveItem = MenuItem(I18n.s("menu.context.move")).apply {
            setOnAction { callbacks.onMove() }
        }

        val encryptedItem = CheckMenuItem(I18n.s("menu.context.encrypted")).apply {
            isSelected = partition.encrypted
            setOnAction {
                val newValue = isSelected
                if (partition.encrypted != newValue) {
                    callbacks.onToggleEncrypted(newValue)
                }
            }
        }

        val cloudItem = CheckMenuItem(I18n.s("menu.context.cloudBackup")).apply {
            isSelected = partition.cloudBackup
            setOnAction {
                val newValue = isSelected
                if (partition.cloudBackup != newValue) {
                    callbacks.onToggleCloud(newValue)
                }
            }
        }

        val virtualItem = CheckMenuItem(I18n.s("menu.context.virtual")).apply {
            isSelected = partition.virtual
            setOnAction {
                val newValue = isSelected
                if (partition.virtual != newValue) {
                    callbacks.onToggleVirtual(newValue)
                }
            }
        }

        val hiddenItem = CheckMenuItem(I18n.s("menu.context.hidden")).apply {
            isSelected = partition.hidden
            setOnAction {
                val newValue = isSelected
                if (partition.hidden != newValue) {
                    callbacks.onToggleHidden(newValue)
                }
            }
        }

        val changeMenu = Menu(I18n.s("menu.context.change")).apply {
            items.setAll(encryptedItem, cloudItem, virtualItem, hiddenItem)
        }

        return ContextMenu(deleteItem, moveItem, SeparatorMenuItem(), changeMenu)
    }
}

