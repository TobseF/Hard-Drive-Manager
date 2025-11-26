package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.formatSize
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.context.ContextMenuFactory
import de.tfr.tool.ui.util.DialogHelper
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.FlowPane
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.util.StringConverter

/**
 * Displays only partitions from all disks as cards.
 * Similar to TabCards but focuses exclusively on partitions.
 */
class TabPartitions(private val onRequestRefresh: () -> Unit = {}) : ScrollPane() {

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

    /** Apply the given theme to all current PartitionCards. */
    fun applyTheme(theme: Theme) {
        collectAllPartitionCards().forEach { it.applyTheme(theme) }
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
        // Collect all partitions from all disks
        val allPartitions = disks.filter { !it.hidden }
            .flatMap { disk ->
                disk.partitions.filter { !it.hidden }.map { partition -> disk to partition }
            }

        val content: Node = if (!grouped) {
            val flow = FlowPane()
            flow.hgap = 16.0
            flow.vgap = 16.0
            flow.padding = Insets(12.0)
            allPartitions.forEach { (disk, partition) ->
                val c = PartitionCard(disk, partition)
                c.applyTheme(ThemeManager.currentTheme)
                installPartitionContextMenu(c, disk, partition)
                flow.children += c
            }
            flow
        } else {
            // Group partitions by their tags
            val byTag = allPartitions
                .groupBy { (_, partition) ->
                    val tags = partition.tags.trim()
                    if (tags.isEmpty()) I18n.s("stats.noTag") else tags
                }
            val root = VBox(16.0)
            root.padding = Insets(12.0)
            byTag.toSortedMap().forEach { (tag, list) ->
                val groupBox = VBox(10.0)
                val header = Label(tag)
                header.style = "-fx-font-size: 16px; -fx-font-weight: bold;"

                // Horizontal flow of the cards within the group (wraps automatically if needed)
                val row = FlowPane(16.0, 16.0).apply { maxWidth = Double.MAX_VALUE }
                list.forEach { (disk, partition) ->
                    val c = PartitionCard(disk, partition)
                    c.applyTheme(ThemeManager.currentTheme)
                    installPartitionContextMenu(c, disk, partition)
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

    private fun collectAllPartitionCards(): List<PartitionCard> {
        val result = mutableListOf<PartitionCard>()
        val content = cardsContainer.children.firstOrNull() ?: return emptyList()
        fun collect(node: Node) {
            when (node) {
                is PartitionCard -> result += node
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
            val cards = collectAllPartitionCards()
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

    private fun installPartitionContextMenu(card: PartitionCard, disk: Disk, partition: Partition) {
        var activeMenu: ContextMenu? = null
        card.setOnContextMenuRequested { event ->
            activeMenu?.hide()
            val menu = buildPartitionContextMenu(disk, partition)
            menu.show(card, event.screenX, event.screenY)
            activeMenu = menu
            event.consume()
        }
        card.setOnMousePressed { event ->
            if (event.isPrimaryButtonDown) {
                activeMenu?.hide()
            }
        }
    }

    private fun buildPartitionContextMenu(disk: Disk, partition: Partition): ContextMenu {
        val menu = ContextMenuFactory.createPartitionMenu(
            partition,
            ContextMenuFactory.PartitionCallbacks(
                onDelete = { confirmDeletePartition(partition) },
                onMove = { onMovePartition(partition) },
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
                }
            )
        )
        menu.setOnShowing {
            val moveItem =
                menu.items.filterIsInstance<MenuItem>().firstOrNull { it.text == I18n.s("menu.context.move") }
            moveItem?.isDisable = DiskRepository.loadAll().size <= 1
        }
        return menu
    }

    private fun confirmDeletePartition(partition: Partition) {
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
            onRequestRefresh()
        }
    }

    private fun onMovePartition(partition: Partition) {
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
                    onRequestRefresh()

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
}
