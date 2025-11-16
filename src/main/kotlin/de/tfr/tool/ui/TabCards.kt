package de.tfr.tool.ui

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
 * Encapsulates the complete Cards tab UI and logic that was previously inside MainView.
 *
 * Responsibilities:
 * - Render disks as [DiskCard]s either flat or grouped by tag
 * - Manage equal/fixed card height options and apply them after layout
 * - React to theme and language changes via dedicated methods
 */
class TabCards : ScrollPane() {

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
                flow.children += c
            }
            flow
        } else {
            // Arrange groups vertically; inside each group the disks are placed horizontally
            val byTag = disks.filter { !it.hidden }
                .groupBy { it.tag.ifBlank { I18n.s("stats.noTag") } }
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
}
