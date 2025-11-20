package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.formatSize
import de.tfr.tool.model.percentOf
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

/**
 * Card view for a single partition.
 * Displays partition details similar to how partitions are shown within DiskCard,
 * but as a standalone card.
 */
class PartitionCard(val disk: Disk, val partition: Partition) : StackPane() {
    private val outer = VBox(8.0)
    private val card = VBox(12.0)

    // UI references
    private lateinit var nameLabel: Label
    private lateinit var sizeLabel: Label
    private lateinit var diskNameLabel: Label
    private lateinit var usedLabel: Label
    private lateinit var barBg: Rectangle
    private lateinit var barFill: Rectangle

    init {
        padding = Insets(8.0)
        children += outer
        maxWidth = 260.0
        prefWidth = 260.0
        style = "-fx-background-color: transparent;"

        build()
    }

    private fun build() {
        card.padding = Insets(14.0)
        card.alignment = Pos.TOP_LEFT
        card.background = Background(BackgroundFill(Color.rgb(255, 250, 229), CornerRadii(10.0), Insets.EMPTY))
        card.border = Border(BorderStroke(Color.rgb(234, 210, 140), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(2.0)))
        VBox.setVgrow(card, Priority.ALWAYS)

        // Header: Partition name/letter
        val name = partitionDisplayName(partition.letter, partition.name)
        nameLabel = Label(name)
        nameLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333333;"

        // Size
        sizeLabel = Label(partition.sizeMB.formatSize())
        sizeLabel.style = "-fx-font-size: 14px; -fx-text-fill: #444;"

        val headerRow = HBox(8.0)
        headerRow.alignment = Pos.CENTER_LEFT
        headerRow.children += listOf(
            nameLabel,
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            sizeLabel
        )

        card.children += headerRow

        // Disk name (which disk this partition belongs to)
        diskNameLabel = Label("${I18n.s("col.disk")}: ${disk.name}")
        diskNameLabel.style = "-fx-font-size: 12px; -fx-text-fill: #666;"
        card.children += diskNameLabel

        // Used/Free info
        val free = (partition.sizeMB - partition.usedMB).coerceAtLeast(0.0)
        usedLabel = Label("${partition.usedMB.formatSize()} / ${free.formatSize()}")
        usedLabel.style = "-fx-font-size: 12px; -fx-text-fill: #555;"
        VBox.setMargin(usedLabel, Insets(0.0, 0.0, -12.0, 0.0))
        card.children += usedLabel

        // Usage bar
        barBg = Rectangle(0.0, 8.0, Color.web("#ffd08a"))
        barFill = Rectangle(0.0, 8.0, Color.web("#f59e42"))
        barBg.height = 8.0
        barFill.height = 8.0

        val bar = StackPane()
        bar.alignment = Pos.CENTER_LEFT
        bar.maxWidth = Double.MAX_VALUE

        barBg.isManaged = false
        barFill.isManaged = false
        barBg.widthProperty().bind(bar.widthProperty().subtract(20))
        barFill.widthProperty().bind(barBg.widthProperty().multiply(partition.usedMB.percentOf(partition.sizeMB)))

        bar.children.addAll(barBg, barFill)
        VBox.setMargin(bar, Insets(0.0, 0.0, 0.0, 0.0))
        card.children += bar

        // Spacer
        val spacer = Region()
        VBox.setVgrow(spacer, Priority.ALWAYS)
        card.children += spacer

        // Footer: Type, Tags, Icons
        val footer = HBox(8.0).apply { alignment = Pos.CENTER_LEFT }

        val typeLabel = Label(partition.type)
        typeLabel.style = "-fx-font-size: 11px; -fx-text-fill: #888;"
        footer.children += typeLabel

        val tagsLabel = Label(if (partition.tags.isNotBlank()) partition.tags else I18n.s("stats.byTag.fallback"))
        tagsLabel.style = "-fx-font-size: 11px; -fx-text-fill: #999;"
        footer.children += tagsLabel

        val iconsRow = HBox(4.0)
        iconsRow.alignment = Pos.CENTER_RIGHT
        HBox.setHgrow(iconsRow, Priority.ALWAYS)

        // Encrypted icon
        val encryptedUrl = javaClass.getResource("/encrypted.png")?.toExternalForm()
        if (encryptedUrl != null && partition.encrypted) {
            val encryptedIcon = svgIcon(encryptedUrl, 16.0, 16.0)
            iconsRow.children += encryptedIcon
        }

        // Cloud backup icon
        val cloudUrl = javaClass.getResource("/cloud-backup.png")?.toExternalForm()
        if (cloudUrl != null && partition.cloudBackup) {
            val cloudIcon = svgIcon(cloudUrl, 16.0, 16.0)
            iconsRow.children += cloudIcon
        }

        // Virtual icon
        val virtualUrl = javaClass.getResource("/virtual.png")?.toExternalForm()
        if (virtualUrl != null && partition.virtual) {
            val virtualIcon = svgIcon(virtualUrl, 16.0, 16.0)
            iconsRow.children += virtualIcon
        }

        if (iconsRow.children.isNotEmpty()) {
            footer.children += iconsRow
        }

        card.children += footer

        outer.children += card
    }

    private fun partitionDisplayName(letter: String?, rawName: String?): String {
        val l = (letter ?: "").trim()
        val base = (rawName ?: "").trim()
        if (l.isEmpty()) return base
        val pattern = Regex("^" + Regex.escape(l) + ":\\s*", RegexOption.IGNORE_CASE)
        val cleaned = base.replace(pattern, "")
        return if (cleaned.isEmpty()) "$l:" else "$l: $cleaned"
    }

    private fun svgIcon(url: String, w: Double, h: Double): ImageView {
        return ImageView(url).apply {
            fitWidth = w
            fitHeight = h
            isPreserveRatio = true
            isSmooth = true
        }
    }

    fun applyTheme(theme: Theme) {
        if (theme == Theme.DARK) {
            card.background = Background(BackgroundFill(Color.web("#4b4f51"), CornerRadii(10.0), Insets.EMPTY))
            card.border = Border(BorderStroke(Color.web("#6a6e70"), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(2.0)))
            nameLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e8e8e8;"
            sizeLabel.style = "-fx-font-size: 14px; -fx-text-fill: #d0d0d0;"
            diskNameLabel.style = "-fx-font-size: 12px; -fx-text-fill: #c0c0c0;"
            usedLabel.style = "-fx-font-size: 12px; -fx-text-fill: #b0b0b0;"
        } else {
            card.background = Background(BackgroundFill(Color.rgb(255, 250, 229), CornerRadii(10.0), Insets.EMPTY))
            card.border = Border(
                BorderStroke(
                    Color.rgb(234, 210, 140),
                    BorderStrokeStyle.SOLID,
                    CornerRadii(10.0),
                    BorderWidths(2.0)
                )
            )
            nameLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333333;"
            sizeLabel.style = "-fx-font-size: 14px; -fx-text-fill: #444;"
            diskNameLabel.style = "-fx-font-size: 12px; -fx-text-fill: #666;"
            usedLabel.style = "-fx-font-size: 12px; -fx-text-fill: #555;"
        }
    }

    fun setEqualHeight(h: Double) {
        minHeight = h
        prefHeight = h
        maxHeight = h
        outer.minHeight = h
        outer.prefHeight = h
        outer.maxHeight = h
        card.minHeight = h - 16.0
        card.prefHeight = h - 16.0
        card.maxHeight = h - 16.0
    }

    fun applyFixedHeight(h: Double) = setEqualHeight(h)

    fun resetHeightConstraints() {
        minHeight = USE_COMPUTED_SIZE
        prefHeight = USE_COMPUTED_SIZE
        maxHeight = USE_COMPUTED_SIZE
        outer.minHeight = USE_COMPUTED_SIZE
        outer.prefHeight = USE_COMPUTED_SIZE
        outer.maxHeight = USE_COMPUTED_SIZE
        card.minHeight = USE_COMPUTED_SIZE
        card.prefHeight = USE_COMPUTED_SIZE
        card.maxHeight = USE_COMPUTED_SIZE
    }

    fun setCardGrowEnabled(enabled: Boolean) {
        if (enabled) {
            VBox.setVgrow(this, Priority.ALWAYS)
            VBox.setVgrow(card, Priority.ALWAYS)
        } else {
            VBox.setVgrow(this, null)
            VBox.setVgrow(card, null)
        }
    }
}

