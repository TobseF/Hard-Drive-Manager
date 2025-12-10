package de.tfr.tool.ui.context

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import javafx.scene.control.CheckMenuItem
import javafx.scene.control.ContextMenu
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem

object ContextMenuFactory {
    data class DiskCallbacks(
        val onDelete: () -> Unit,
        val onRename: () -> Unit,
        val onEditTags: () -> Unit,
        val onEditSize: () -> Unit,
        val onToggleHidden: (Boolean) -> Unit,
        val onEditComment: () -> Unit
    )

    data class PartitionCallbacks(
        val onDelete: () -> Unit,
        val onRename: () -> Unit,
        val onEditTags: () -> Unit,
        val onEditSize: () -> Unit,
        val onMove: () -> Unit,
        val onToggleEncrypted: (Boolean) -> Unit,
        val onToggleCloud: (Boolean) -> Unit,
        val onToggleVirtual: (Boolean) -> Unit,
        val onToggleHidden: (Boolean) -> Unit,
        val onEditComment: () -> Unit
    )

    fun createDiskMenu(disk: Disk, callbacks: DiskCallbacks): ContextMenu {
        val renameItem = MenuItem(I18n.s("menu.context.rename")).apply {
            setOnAction { callbacks.onRename() }
        }

        val editTagsItem = MenuItem(I18n.s("menu.context.editTags")).apply {
            setOnAction { callbacks.onEditTags() }
        }

        val editSizeItem = MenuItem(I18n.s("menu.context.editSize")).apply {
            setOnAction { callbacks.onEditSize() }
        }

        val editCommentItem = MenuItem(I18n.s("menu.context.editComment")).apply {
            setOnAction { callbacks.onEditComment() }
        }

        val hiddenItem = CheckMenuItem(I18n.s("menu.context.hidden")).apply {
            isSelected = disk.hidden
            setOnAction {
                val newValue = isSelected
                if (disk.hidden != newValue) {
                    callbacks.onToggleHidden(newValue)
                }
            }
        }

        val deleteItem = MenuItem(I18n.s("menu.context.delete")).apply {
            setOnAction { callbacks.onDelete() }
        }

        return ContextMenu(renameItem, editTagsItem, editSizeItem, editCommentItem, hiddenItem, deleteItem)
    }

    fun createPartitionMenu(partition: Partition, callbacks: PartitionCallbacks): ContextMenu {
        val renameItem = MenuItem(I18n.s("menu.context.rename")).apply {
            setOnAction { callbacks.onRename() }
        }

        val editTagsItem = MenuItem(I18n.s("menu.context.editTags")).apply {
            setOnAction { callbacks.onEditTags() }
        }

        val editSizeItem = MenuItem(I18n.s("menu.context.editSize")).apply {
            setOnAction { callbacks.onEditSize() }
        }

        val editCommentItem = MenuItem(I18n.s("menu.context.editComment")).apply {
            setOnAction { callbacks.onEditComment() }
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

        val optionsMenu = Menu(I18n.s("menu.context.options")).apply {
            items.setAll(encryptedItem, cloudItem, virtualItem)
        }

        val moveItem = MenuItem(I18n.s("menu.context.move")).apply {
            setOnAction { callbacks.onMove() }
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

        val deleteItem = MenuItem(I18n.s("menu.context.delete")).apply {
            setOnAction { callbacks.onDelete() }
        }

        return ContextMenu(
            renameItem,
            editTagsItem,
            editSizeItem,
            editCommentItem,
            optionsMenu,
            moveItem,
            hiddenItem,
            deleteItem
        )
    }
}
