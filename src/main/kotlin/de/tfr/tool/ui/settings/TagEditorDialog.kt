package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.util.DialogHelper
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.*

/**
 * Dialog for editing tags for hard drives or partitions.
 *
 * Features:
 * - Shows current tags as removable tokens
 * - Shows list of all available tags
 * - Allows renaming of tags (global)
 * - Allows adding new tags
 */
class TagEditorDialog {
    private val allDisks = DiskRepository.loadAll()
    private val usedTags = extractAllUsedTags()

    private var currentTags: MutableSet<String> = mutableSetOf()

    private fun extractAllUsedTags(): Set<String> {
        val tags = mutableSetOf<String>()
        allDisks.forEach { disk ->
            // Split disk tags like partition tags
            disk.tag.split(",").forEach { tag ->
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    tags += trimmed
                }
            }
            disk.partitions.forEach { partition ->
                partition.tags.split(",").forEach { tag ->
                    val trimmed = tag.trim()
                    if (trimmed.isNotEmpty()) {
                        tags += trimmed
                    }
                }
            }
        }
        return tags
    }

    fun showForDisk(disk: Disk, onApply: (tags: Set<String>) -> Unit) {
        currentTags = disk.tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        showDialog(true, onApply)
    }

    fun showForPartition(partition: Partition, onApply: (tags: Set<String>) -> Unit) {
        currentTags = partition.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        showDialog(false, onApply)
    }

    private fun showDialog(isDisk: Boolean, onApply: (tags: Set<String>) -> Unit) {
        val dialog = Dialog<Set<String>>()
        dialog.title = I18n.s("dialog.editTags.title")
        dialog.headerText =
            if (isDisk) I18n.s("dialog.editTags.headerDisk") else I18n.s("dialog.editTags.headerPartition")

        val dialogPane = dialog.dialogPane
        dialogPane.minWidth = 500.0
        dialogPane.minHeight = 700.0
        dialogPane.prefHeight = 700.0

        // Main content
        val content = VBox(8.0)
        content.padding = Insets(12.0)
        content.maxHeight = Double.MAX_VALUE

        // Section 1: Current Tags (as tokens)
        val currentTagsLabel = Label(I18n.s("dialog.editTags.currentTags"))
        currentTagsLabel.style = "-fx-font-weight: bold; -fx-font-size: 12px;"

        val tagsFlowPane = FlowPane()
        tagsFlowPane.hgap = 6.0
        tagsFlowPane.vgap = 6.0
        tagsFlowPane.style = "-fx-padding: 8;"
        tagsFlowPane.minHeight = Region.USE_COMPUTED_SIZE
        tagsFlowPane.prefHeight = Region.USE_COMPUTED_SIZE
        tagsFlowPane.maxHeight = Region.USE_COMPUTED_SIZE
        tagsFlowPane.prefWrapLength = 450.0
        tagsFlowPane.maxWidth = 450.0

        // Container with border around the tagsFlowPane
        val tagsContainer = VBox()
        tagsContainer.style = "-fx-border-color: #cccccc; -fx-border-radius: 4;"
        tagsContainer.minHeight = Region.USE_COMPUTED_SIZE
        tagsContainer.prefHeight = Region.USE_COMPUTED_SIZE
        tagsContainer.maxHeight = Region.USE_COMPUTED_SIZE
        tagsContainer.maxWidth = 466.0
        tagsContainer.children.add(tagsFlowPane)

        val updateTagsDisplay = {
            tagsFlowPane.children.clear()
            currentTags.sorted().forEach { tag ->
                tagsFlowPane.children += createTagToken(tag, tagsFlowPane, isRemovable = true)
            }
        }

        updateTagsDisplay()

        // Section 3: Available Tags (as tokens) - DEFINE FIRST before using in button handler
        val availableTagsLabel = Label(I18n.s("dialog.editTags.availableTags"))
        availableTagsLabel.style = "-fx-font-weight: bold; -fx-font-size: 12px;"

        val availableTagsFlowPane = FlowPane()
        availableTagsFlowPane.hgap = 6.0
        availableTagsFlowPane.vgap = 6.0
        availableTagsFlowPane.style = "-fx-padding: 8;"
        availableTagsFlowPane.minHeight = 0.0

        // Section 2: Add new tag (moved to top) - NOW CAN USE availableTagsFlowPane
        val addTagBox = VBox(2.0)
        addTagBox.style = "-fx-border-color: #e0e0e0; -fx-border-radius: 4; -fx-padding: 6;"
        addTagBox.prefHeight = 65.0

        val addTagLabel = Label(I18n.s("dialog.editTags.newTag"))
        addTagLabel.style = "-fx-font-size: 11px; -fx-text-fill: #666;"

        val addTagInputBox = HBox(6.0)
        addTagInputBox.alignment = Pos.CENTER_LEFT
        val newTagInput = TextField()
        newTagInput.promptText = I18n.s("dialog.editTags.newTag")
        newTagInput.prefWidth = 250.0

        // Function to add a new tag
        val addNewTag = {
            val newTag = newTagInput.text.trim()
            if (newTag.isNotEmpty() && !currentTags.contains(newTag)) {
                currentTags.add(newTag)
                // Check if tag already exists in available tags
                val tagExists = availableTagsFlowPane.children.any { child ->
                    child is VBox && child.children.any { grandchild ->
                        grandchild is HBox && grandchild.children.any { token ->
                            token is Label && token.text == newTag
                        }
                    }
                }

                if (!tagExists) {
                    availableTagsFlowPane.children.add(
                        createAvailableTagToken(
                            newTag, availableTagsFlowPane,
                            onAdd = {
                                if (!currentTags.contains(newTag)) {
                                    currentTags.add(newTag)
                                    updateTagsDisplay()
                                }
                            },
                            onRename = { renameTagGlobally(newTag) },
                            onDelete = { deleteTagGlobally(newTag) }
                        )
                    )
                    // Reorder
                    val sortedChildren = availableTagsFlowPane.children.sortedBy { child ->
                        if (child is VBox) {
                            val label = child.children.filterIsInstance<HBox>()
                                .firstOrNull()?.children?.filterIsInstance<Label>()?.firstOrNull()
                            label?.text ?: ""
                        } else ""
                    }
                    availableTagsFlowPane.children.setAll(sortedChildren)
                }
                newTagInput.clear()
                updateTagsDisplay()
            }
        }

        val addTagBtn = Button(I18n.s("dialog.editTags.addTag"))
        addTagBtn.style = "-fx-padding: 4 12 4 12;"
        addTagBtn.setOnAction { addNewTag.invoke() }

        // Press ENTER to add action
        newTagInput.setOnKeyPressed { event ->
            if (event.code == KeyCode.ENTER) {
                addNewTag.invoke()
                event.consume() // Prevents default behavior
            }
        }

        addTagInputBox.children.setAll(newTagInput, addTagBtn)
        addTagBox.children.setAll(addTagLabel, addTagInputBox)


        // Populate available tags
        val updateAvailableTagsDisplay = {
            availableTagsFlowPane.children.clear()
            usedTags.sorted().forEach { tag ->
                availableTagsFlowPane.children += createAvailableTagToken(
                    tag, availableTagsFlowPane,
                    onAdd = {
                        if (!currentTags.contains(tag)) {
                            currentTags.add(tag)
                            updateTagsDisplay()
                        }
                    },
                    onRename = { renameTagGlobally(tag) },
                    onDelete = { deleteTagGlobally(tag) }
                )
            }
        }

        updateAvailableTagsDisplay()

        // Wrap available tags in ScrollPane for scrolling
        val availableTagsScrollPane = ScrollPane(availableTagsFlowPane)
        availableTagsScrollPane.isFitToWidth = true
        availableTagsScrollPane.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        availableTagsScrollPane.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        availableTagsScrollPane.style =
            "-fx-control-inner-background: transparent; -fx-border-color: #e0e0e0; -fx-border-radius: 4; -fx-border-width: 1; -fx-padding: 0;"
        availableTagsScrollPane.minHeight = 100.0
        availableTagsScrollPane.maxHeight = Double.MAX_VALUE

        content.children.addAll(
            currentTagsLabel,
            tagsContainer,
            Separator(),
            addTagBox,
            Separator(),
            availableTagsLabel,
            availableTagsScrollPane
        )

        VBox.setVgrow(availableTagsScrollPane, Priority.ALWAYS)

        dialogPane.content = content

        // Buttons
        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        dialog.setResultConverter { buttonType ->
            if (buttonType == okButtonType) currentTags else null
        }

        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent && result.get() != null) {
            onApply(result.get())
        }
    }

    private fun createTagToken(tag: String, parent: FlowPane, isRemovable: Boolean = true): Node {
        val token = HBox(4.0)
        token.alignment = Pos.CENTER
        token.style =
            "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 12; -fx-background-radius: 12;"

        val label = Label(tag)
        label.style = "-fx-text-fill: white; -fx-font-size: 11px;"

        if (isRemovable) {
            val closeBtn = Button("✕")
            closeBtn.style =
                "-fx-padding: 0; -fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;"
            closeBtn.setOnAction {
                currentTags.remove(tag)
                parent.children.remove(token)
            }
            token.children.setAll(label, closeBtn)
        } else {
            token.children.setAll(label)
        }
        return token
    }

    private fun createAvailableTagToken(
        tag: String,
        parent: FlowPane,
        onAdd: () -> Unit,
        onRename: () -> Unit,
        onDelete: () -> Unit
    ): Node {
        val token = VBox(2.0)
        token.alignment = Pos.TOP_CENTER
        token.style = "-fx-padding: 4;"

        // Tag token
        val tokenChip = HBox(4.0)
        tokenChip.alignment = Pos.CENTER
        tokenChip.style =
            "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 12; -fx-background-radius: 12; -fx-cursor: hand;"

        val label = Label(tag)
        label.style = "-fx-text-fill: white; -fx-font-size: 11px;"

        tokenChip.children.setAll(label)

        // Click on token to add
        tokenChip.setOnMouseClicked { onAdd() }

        // Action buttons below token
        val buttonBox = HBox(2.0)
        buttonBox.alignment = Pos.CENTER
        buttonBox.style = "-fx-padding: 2 0 0 0;"

        val addBtn = Button("+")
        addBtn.style = "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 20;"
        addBtn.tooltip = Tooltip(I18n.s("dialog.editTags.addTooltip"))
        addBtn.setOnAction { onAdd() }

        val renameBtn = Button("✎")
        renameBtn.style = "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 20;"
        renameBtn.tooltip = Tooltip(I18n.s("dialog.editTags.renameTooltip"))
        renameBtn.setOnAction { onRename() }

        val deleteBtn = Button("✗")
        deleteBtn.style = "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 20;"
        deleteBtn.tooltip = Tooltip(I18n.s("dialog.editTags.deleteTooltip"))
        deleteBtn.setOnAction {
            // Call onDelete and then remove this token from the UI
            onDelete()
            parent.children.remove(token)
        }

        buttonBox.children.setAll(addBtn, renameBtn, deleteBtn)
        token.children.setAll(tokenChip, buttonBox)

        return token
    }

    private fun renameTagGlobally(oldTag: String) {
        val dialog = TextInputDialog(oldTag)
        dialog.title = I18n.s("dialog.renameTag.title")
        dialog.headerText = I18n.s("dialog.renameTag.message")
        dialog.contentText = I18n.s("dialog.renameTag.prompt")
        dialog.editor.selectAll()

        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.disableProperty().bind(dialog.editor.textProperty().isEmpty)

        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val newTag = result.get().trim()
            if (newTag.isNotEmpty() && newTag != oldTag) {
                // Apply rename globally
                val reloadedDisks = DiskRepository.loadAll()
                reloadedDisks.forEach { disk ->
                    if (disk.tag == oldTag) {
                        disk.tag = newTag
                        DiskRepository.updateDisk(disk)
                    }
                    disk.partitions.forEach { partition ->
                        val tagsSet = partition.tags.split(",").map { it.trim() }.toMutableSet()
                        if (tagsSet.remove(oldTag)) {
                            tagsSet.add(newTag)
                            partition.tags = tagsSet.joinToString(", ")
                            DiskRepository.updatePartition(partition)
                        }
                    }
                }
                // Update local state
                if (currentTags.contains(oldTag)) {
                    currentTags.remove(oldTag)
                    currentTags.add(newTag)
                }
                (usedTags as? MutableSet)?.remove(oldTag)
                (usedTags as? MutableSet)?.add(newTag)
            }
        }
    }

    private fun deleteTagGlobally(tagToDelete: String) {
        val confirmDialog = Alert(Alert.AlertType.CONFIRMATION)
        confirmDialog.title = I18n.s("dialog.deleteTag.title")
        confirmDialog.headerText = null
        confirmDialog.contentText = I18n.s("dialog.deleteTag.message", tagToDelete)

        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        confirmDialog.buttonTypes.setAll(okButtonType, cancelButtonType)

        val dialogResult = DialogHelper.showDialog(confirmDialog, ThemeManager.currentTheme == Theme.DARK)
        if (dialogResult.isPresent && dialogResult.get() == okButtonType) {
            // Delete tag globally
            val reloadedDisks = DiskRepository.loadAll()
            reloadedDisks.forEach { disk ->
                if (disk.tag == tagToDelete) {
                    disk.tag = ""
                    DiskRepository.updateDisk(disk)
                }
                disk.partitions.forEach { partition ->
                    val tagsSet = partition.tags.split(",").map { it.trim() }.toMutableSet()
                    if (tagsSet.remove(tagToDelete)) {
                        partition.tags = tagsSet.joinToString(", ")
                        DiskRepository.updatePartition(partition)
                    }
                }
            }
            // Update local state
            if (currentTags.contains(tagToDelete)) {
                currentTags.remove(tagToDelete)
            }
            (usedTags as? MutableSet)?.remove(tagToDelete)
        }
    }
}

