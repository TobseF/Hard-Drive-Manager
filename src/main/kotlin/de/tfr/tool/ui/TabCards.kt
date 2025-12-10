package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.context.ContextMenuFactory
import de.tfr.tool.ui.context.PartitionActions
import de.tfr.tool.ui.settings.TagEditorDialog
import de.tfr.tool.ui.util.DialogHelper
import de.tfr.tool.ui.util.TabTableNameFormatter
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.ContextMenuEvent
import javafx.scene.layout.FlowPane
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/**
 * Encapsulates the complete Cards tab UI and logic that was previously inside MainView.
 *
 * Responsibilities:
 * - Render disks as [DiskCard]s either flat or grouped by tag
 * - Manage equal/fixed card height options and apply them after layout
 * - React to theme and language changes via dedicated methods
 */
class TabCards(private val onRequestRefresh: () -> Unit = {}) : ScrollPane() {

    // Container for swapping the cards content (flat flow or grouped VBox)
    private val cardsContainer = StackPane()

    // Current dataset and grouping-flag to allow rebuilds on translation/theme
    private var currentDisks: List<Disk> = emptyList()
    private var grouped: Boolean = false

    // Height options (fixed height takes precedence over equal height)
    private var equalCardHeights: Boolean = false
    private var fixedCardHeightEnabled: Boolean = false
    private var fixedCardHeightPx: Double = 220.0

    init {
        id = "tabCardsContent"
        isFitToWidth = true
        hbarPolicy = ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollBarPolicy.AS_NEEDED
        style = "-fx-background: transparent; -fx-background-color: transparent;"
        content = cardsContainer
    }

    /** Update the cards with a new dataset and grouping mode. */
    fun updateData(disks: List<Disk>, grouped: Boolean) {
        this.currentDisks = disks
        this.grouped = grouped
        rebuildCardsView()
    }

    /** Apply the given theme to all current DiskCards. */
    fun applyTheme(theme: Theme) {
        collectAllDiskCards().forEach { it.applyTheme(theme) }
    }

    /** Rebuild UI strings (e.g., group headers, fallback texts) on language changes. */
    fun applyTranslations() {
        // Rebuild using the last known dataset and grouping
        rebuildCardsView()
    }

    /** Configure whether cards should have equal heights (ignored when fixed height is enabled). */
    fun setEqualHeightEnabled(flag: Boolean) {
        this.equalCardHeights = flag
        applyEqualHeights()
    }

    /** Configure whether cards should use a fixed pixel height (takes precedence over equal height). */
    fun setFixedHeightEnabled(flag: Boolean) {
        this.fixedCardHeightEnabled = flag
        applyEqualHeights()
    }

    /** Configure the fixed pixel height used when [setFixedHeightEnabled] is true. */
    fun setFixedHeightPx(px: Double) {
        this.fixedCardHeightPx = px
        applyEqualHeights()
    }

    /** Returns the inner content node that should be snapshotted for PNG export, or null if empty. */
    fun getSnapshotContent(): Node? = cardsContainer.children.firstOrNull()

    // -- Internals ---------------------------------------------------------------------------

    private fun rebuildCardsView() {
        val disks = currentDisks
        val content: Node = if (!grouped) {
            val flow = FlowPane()
            flow.hgap = 16.0
            flow.vgap = 16.0
            flow.padding = Insets(12.0)
            disks.filter { !it.hidden }.forEach { d ->
                val c = DiskCard(d)
                c.applyTheme(ThemeManager.currentTheme)
                installContextMenus(c, d)
                flow.children += c
            }
            flow
        } else {
            // Arrange groups vertically; inside each group the disks are placed horizontally
            val byTag = disks.filter { !it.hidden }
                .groupBy { disk ->
                    val tags = disk.tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (tags.isEmpty()) I18n.s("stats.noTag") else tags.first()
                }
            val root = VBox(16.0)
            root.padding = Insets(12.0)
            byTag.toSortedMap().forEach { (tag, list) ->
                val groupBox = VBox(10.0)
                val header = Label(tag)
                header.style = "-fx-font-size: 16px; -fx-font-weight: bold;"

                // Horizontal flow of the cards within the group (wraps automatically if needed)
                val row = FlowPane(16.0, 16.0).apply { maxWidth = Double.MAX_VALUE }
                list.forEach { d ->
                    val c = DiskCard(d)
                    c.applyTheme(ThemeManager.currentTheme)
                    installContextMenus(c, d)
                    row.children += c
                }

                groupBox.children += listOf(header, row)
                root.children += groupBox
            }
            root
        }
        cardsContainer.children.setAll(content)

        // After rebuilding, apply equal/fixed heights if needed
        applyEqualHeights()
    }

    private fun collectAllDiskCards(): List<DiskCard> {
        val result = mutableListOf<DiskCard>()
        val content = cardsContainer.children.firstOrNull() ?: return emptyList()
        fun collect(node: Node) {
            when (node) {
                is DiskCard -> result += node
                is Pane -> node.childrenUnmodifiable.forEach { collect(it) }
                else -> {}
            }
        }
        collect(content)
        return result
    }

    /**
     * Apply height constraints to all cards according to the current options.
     * Fixed height wins over equal-height mode. When neither is enabled, cards use their natural height.
     */
    fun applyEqualHeights() {
        Platform.runLater {
            val cards = collectAllDiskCards()
            if (cards.isEmpty()) return@runLater

            if (fixedCardHeightEnabled) {
                val h = fixedCardHeightPx.coerceAtLeast(50.0)
                cards.forEach {
                    it.applyFixedHeight(h)
                    it.setCardGrowEnabled(true)
                }
                return@runLater
            }

            if (!equalCardHeights) {
                // Both options off: natural, individual height
                cards.forEach {
                    it.resetHeightConstraints()
                    it.setCardGrowEnabled(false)
                }
                return@runLater
            }

            // Ensure CSS/layout before measuring
            cards.forEach { it.applyCss(); it.layout() }
            val max = cards.maxOf {
                val h = it.height
                val pref = it.prefHeight(-1.0)
                if (h > 0.0) h else pref
            }
            // Apply uniform height
            cards.forEach {
                it.applyFixedHeight(max)
                it.setCardGrowEnabled(true)
            }
        }
    }

    private fun installContextMenus(card: DiskCard, disk: Disk) {
        var activeMenu: ContextMenu? = null

        fun showMenu(menu: ContextMenu, anchor: Node, event: ContextMenuEvent) {
            activeMenu?.hide()
            menu.show(anchor, event.screenX, event.screenY)
            activeMenu = menu
        }

        card.setOnContextMenuRequested { event ->
            showMenu(buildDiskContextMenu(disk), card, event)
            event.consume()
        }

        card.setPartitionContextMenuHandler { partition, event ->
            val anchor = event.source as? Node ?: card
            showMenu(buildPartitionContextMenu(partition), anchor, event)
        }

        card.setOnMousePressed { event ->
            if (event.isPrimaryButtonDown) {
                activeMenu?.hide()
            }
        }
    }

    private fun buildDiskContextMenu(disk: Disk): ContextMenu {
        return ContextMenuFactory.createDiskMenu(
            disk = disk,
            callbacks = ContextMenuFactory.DiskCallbacks(
                onDelete = { confirmDeleteDisk(disk) },
                onRename = { showRenameDiskDialog(disk) },
                onEditTags = { showEditDiskTagsDialog(disk) },
                onToggleHidden = { hidden ->
                    disk.hidden = hidden
                    DiskRepository.updateDisk(disk)
                    onRequestRefresh()
                },
                onEditComment = {
                    val dialog = DialogHelper.showCommentDialog(
                        initial = disk.comment,
                        titleKey = "dialog.comment.disk.title",
                        promptKey = "dialog.comment.prompt"
                    )
                    if (dialog != null) {
                        disk.comment = dialog
                        DiskRepository.updateDisk(disk)
                        onRequestRefresh()
                    }
                }
            )
        )
    }

    private fun confirmDeleteDisk(disk: Disk) {
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = I18n.s("alert.delete.title")
        alert.headerText = null
        alert.contentText = I18n.s("alert.delete.askDisk", disk.name)

        val confirmButton = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButton = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        alert.buttonTypes.setAll(confirmButton, cancelButton)

        val result = DialogHelper.showDialog(alert, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent && result.get() == confirmButton) {
            DiskRepository.deleteDisk(disk.id)
            onRequestRefresh()
        }
    }

    private fun buildPartitionContextMenu(partition: Partition): ContextMenu {
        return ContextMenuFactory.createPartitionMenu(
            partition,
            ContextMenuFactory.PartitionCallbacks(
                onDelete = { PartitionActions.confirmDeletePartition(partition) { onRequestRefresh() } },
                onRename = { showRenamePartitionDialog(partition) },
                onEditTags = { showEditPartitionTagsDialog(partition) },
                onMove = { PartitionActions.movePartition(partition) { onRequestRefresh() } },
                onToggleEncrypted = { newValue ->
                    partition.encrypted = newValue
                    DiskRepository.updatePartition(partition)
                    onRequestRefresh()
                },
                onToggleCloud = { newValue ->
                    partition.cloudBackup = newValue
                    DiskRepository.updatePartition(partition)
                    onRequestRefresh()
                },
                onToggleVirtual = { newValue ->
                    partition.virtual = newValue
                    DiskRepository.updatePartition(partition)
                    onRequestRefresh()
                },
                onToggleHidden = { newValue ->
                    partition.hidden = newValue
                    DiskRepository.updatePartition(partition)
                    onRequestRefresh()
                },
                onEditComment = {
                    val dialog = DialogHelper.showCommentDialog(
                        initial = partition.comment,
                        titleKey = "dialog.comment.partition.title",
                        promptKey = "dialog.comment.prompt"
                    )
                    if (dialog != null) {
                        partition.comment = dialog
                        DiskRepository.updatePartition(partition)
                        onRequestRefresh()
                    }
                }
            )
        )
    }

    private fun showRenameDiskDialog(disk: Disk) {
        val dialog = TextInputDialog(disk.name)
        dialog.title = I18n.s("dialog.rename.disk.title")
        dialog.headerText = null
        dialog.contentText = I18n.s("dialog.rename.disk.prompt")
        dialog.editor.textFormatter = TabTableNameFormatter.create()
        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.disableProperty().bind(dialog.editor.textProperty().isEmpty)
        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val newName = result.get().trim()
            if (newName.isNotEmpty() && newName != disk.name) {
                disk.name = newName
                DiskRepository.updateDisk(disk)
                onRequestRefresh()
            }
        }
    }

    private fun showRenamePartitionDialog(partition: Partition) {
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
                onRequestRefresh()
            }
        }
    }

    private fun showEditDiskTagsDialog(disk: Disk) {
        TagEditorDialog().showForDisk(disk) { tags ->
            disk.tag = tags.joinToString(", ")
            DiskRepository.updateDisk(disk)
            onRequestRefresh()
        }
    }

    private fun showEditPartitionTagsDialog(partition: Partition) {
        TagEditorDialog().showForPartition(partition) { tags ->
            partition.tags = tags.joinToString(", ")
            DiskRepository.updatePartition(partition)
            onRequestRefresh()
        }
    }
}
