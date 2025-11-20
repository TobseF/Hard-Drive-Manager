package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox

/**
 * Displays only partitions from all disks as cards.
 * Similar to TabCards but focuses exclusively on partitions.
 */
class TabPartitions : ScrollPane() {

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
}

