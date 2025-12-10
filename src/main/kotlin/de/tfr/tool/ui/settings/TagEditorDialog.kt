package de.tfr.tool.ui.settings

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.setIcon
import de.tfr.tool.ui.tag.TagChipFactory
import de.tfr.tool.ui.util.DialogHelper
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import org.kordamp.ikonli.materialdesign2.MaterialDesignP

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
    private var allDisks = DiskRepository.loadAll()
    private var usedTags: MutableSet<String> = extractAllUsedTags().toMutableSet()

    private var currentTags: MutableSet<String> = mutableSetOf()

    private fun extractAllUsedTags(): Set<String> {
        val tags = mutableSetOf<String>()
        allDisks.forEach { disk ->
            tags += parseTags(disk.tag)
            disk.partitions.forEach { partition ->
                tags += parseTags(partition.tags)
            }
        }
        return tags
    }

    fun showForDisk(disk: Disk, onApply: (tags: Set<String>) -> Unit) {
        // Reload data to get latest changes
        allDisks = DiskRepository.loadAll()
        usedTags = extractAllUsedTags().toMutableSet()

        currentTags = parseTags(disk.tag)
        showDialog(true, onApply)
    }

    fun showForPartition(partition: Partition, onApply: (tags: Set<String>) -> Unit) {
        // Reload data to get latest changes
        allDisks = DiskRepository.loadAll()
        usedTags = extractAllUsedTags().toMutableSet()

        currentTags = parseTags(partition.tags)
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
                val chip = TagChipFactory.createTagChip(tag) { chipNode ->
                    currentTags.remove(tag)
                    tagsFlowPane.children.remove(chipNode)
                }
                tagsFlowPane.children += chip
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

        // Define renameTagGlobally and deleteTagGlobally FIRST, before addNewTag
        lateinit var renameTagGlobally: (String) -> Unit
        lateinit var deleteTagGlobally: (String) -> Unit
        lateinit var updateAvailableTagsDisplay: () -> Unit

        renameTagGlobally = { oldTag ->
            val dialog = TextInputDialog(oldTag)
            dialog.title = I18n.s("dialog.renameTag.title")
            dialog.headerText = I18n.s("dialog.renameTag.message")
            dialog.contentText = I18n.s("dialog.renameTag.prompt")
            dialog.editor.selectAll()

            val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
            okButton.disableProperty().bind(dialog.editor.textProperty().isEmpty)

            val result = DialogHelper.showDialog(dialog)
            if (result.isPresent) {
                val newTag = result.get().trim()
                if (newTag.isNotEmpty() && newTag != oldTag) {
                    // Apply rename globally
                    val reloadedDisks = DiskRepository.loadAll()
                    reloadedDisks.forEach { disk ->
                        val diskTags = parseTags(disk.tag)
                        if (diskTags.remove(oldTag)) {
                            diskTags.add(newTag)
                            disk.tag = formatTags(diskTags)
                            DiskRepository.updateDisk(disk)
                        }
                        disk.partitions.forEach { partition ->
                            val tagsSet = parseTags(partition.tags)
                            if (tagsSet.remove(oldTag)) {
                                tagsSet.add(newTag)
                                partition.tags = formatTags(tagsSet)
                                DiskRepository.updatePartition(partition)
                            }
                        }
                    }
                    // Reload data after renaming
                    allDisks = DiskRepository.loadAll()
                    usedTags = extractAllUsedTags().toMutableSet()

                    // Update local state
                    if (currentTags.contains(oldTag)) {
                        currentTags.remove(oldTag)
                        currentTags.add(newTag)
                    }
                    updateTagsDisplay()
                    updateAvailableTagsDisplay()
                }
            }
        }

        deleteTagGlobally = { tagToDelete ->
            val confirmDialog = Alert(Alert.AlertType.CONFIRMATION)
            confirmDialog.title = I18n.s("dialog.deleteTag.title")
            confirmDialog.headerText = null
            confirmDialog.contentText = I18n.s("dialog.deleteTag.message", tagToDelete)

            val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
            val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
            confirmDialog.buttonTypes.setAll(okButtonType, cancelButtonType)

            val dialogResult = DialogHelper.showDialog(confirmDialog)
            if (dialogResult.isPresent && dialogResult.get() == okButtonType) {
                // Delete tag globally
                val reloadedDisks = DiskRepository.loadAll()
                reloadedDisks.forEach { disk ->
                    val diskTags = parseTags(disk.tag)
                    if (diskTags.remove(tagToDelete)) {
                        disk.tag = formatTags(diskTags)
                        DiskRepository.updateDisk(disk)
                    }
                    disk.partitions.forEach { partition ->
                        val tagsSet = parseTags(partition.tags)
                        if (tagsSet.remove(tagToDelete)) {
                            partition.tags = formatTags(tagsSet)
                            DiskRepository.updatePartition(partition)
                        }
                    }
                }
                // Reload data after deletion
                allDisks = DiskRepository.loadAll()
                usedTags = extractAllUsedTags().toMutableSet()

                // Update local state
                if (currentTags.contains(tagToDelete)) {
                    currentTags.remove(tagToDelete)
                    updateTagsDisplay()  // Update the UI to reflect the removal
                }
                updateAvailableTagsDisplay()  // Update available tags display
            }
        }

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
        updateAvailableTagsDisplay = {
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

        val result = DialogHelper.showDialog(dialog)
        if (result.isPresent) {
            onApply(result.get())
        }
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

        val addBtn = Button()
        addBtn.setIcon(MaterialDesignP.PLUS)
        addBtn.style = "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 20;"
        addBtn.tooltip = Tooltip(I18n.s("dialog.editTags.addTooltip"))
        addBtn.setOnAction { onAdd() }

        val renameBtn = Button()
        renameBtn.setIcon(MaterialDesignP.PENCIL)
        renameBtn.style = "-fx-font-size: 10px; -fx-padding: 0 4 0 4; -fx-min-width: 20; -fx-min-height: 20;"
        renameBtn.tooltip = Tooltip(I18n.s("dialog.editTags.renameTooltip"))
        renameBtn.setOnAction { onRename() }

        val deleteBtn = Button()
        deleteBtn.setIcon(MaterialDesignD.DELETE_FOREVER_OUTLINE)
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

    // Helper function to parse tags from a comma-separated string
    private fun parseTags(tags: String): MutableSet<String> = TagChipFactory.parseTags(tags)

    // Helper function to format tags as a comma-separated string
    private fun formatTags(tags: Set<String>): String = TagChipFactory.formatTags(tags)
}
