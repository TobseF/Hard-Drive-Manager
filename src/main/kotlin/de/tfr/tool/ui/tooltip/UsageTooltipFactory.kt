package de.tfr.tool.ui.tooltip

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.formatSize
import de.tfr.tool.ui.CardHoverPalettes
import de.tfr.tool.ui.ThemeManager
import de.tfr.tool.ui.TooltipPalette
import de.tfr.tool.ui.toCss
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.chart.PieChart
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.util.Duration

/**
 * Builds themed tooltips that visualize usage (used vs free) as a pie chart
 * and show additional metadata.
 */
object UsageTooltipFactory {

    fun forDisk(disk: Disk): Tooltip {
        val theme = ThemeManager.currentTheme
        val palette = CardHoverPalettes.tooltip(theme)
        val used = disk.usedMB.coerceAtLeast(0.0)
        val free = (disk.sizeMB - used).coerceAtLeast(0.0)

        val content = tooltipBox(palette).apply {
            children += headerRow(disk.name, disk.sizeMB.formatSize(), palette)
            children += usagePieChart(used, free, palette)
            children += metricRow(I18n.s("tooltip.used"), used.formatSize(), palette)
            children += metricRow(I18n.s("tooltip.free"), free.formatSize(), palette)
            children += metricRow(I18n.s("tooltip.type"), disk.type.ifBlank { "-" }, palette)
            if (disk.comment.isNotBlank()) {
                children += commentBlock(disk.comment, palette)
            }
        }

        return Tooltip().apply {
            graphic = content
            style = tooltipStyle()
            showDelay = Duration.millis(500.0)
            hideDelay = Duration.millis(200.0)
            showDuration = Duration.seconds(30.0)
        }
    }

    fun forPartition(partition: Partition): Tooltip {
        val theme = ThemeManager.currentTheme
        val palette = CardHoverPalettes.tooltip(theme)
        val used = partition.usedMB.coerceAtLeast(0.0)
        val free = (partition.sizeMB - used).coerceAtLeast(0.0)

        val content = tooltipBox(palette).apply {
            val title = buildString {
                if (partition.letter.isNotBlank()) append(partition.letter).append(": ")
                append(partition.name.ifBlank { "Partition" })
            }
            children += headerRow(title, partition.sizeMB.formatSize(), palette)
            children += usagePieChart(used, free, palette)
            children += metricRow(I18n.s("tooltip.used"), used.formatSize(), palette)
            children += metricRow(I18n.s("tooltip.free"), free.formatSize(), palette)
            val fileSystem = partition.fsType
                .takeIf { it.isNotBlank() }
                ?: partition.type.takeIf { it.isNotBlank() }
                ?: I18n.s("tooltip.unknownFs")
            children += metricRow(I18n.s("tooltip.fsType"), fileSystem, palette)
            if (partition.comment.isNotBlank()) {
                children += commentBlock(partition.comment, palette)
            }
        }

        return Tooltip().apply {
            graphic = content
            style = tooltipStyle()
            showDelay = Duration.millis(500.0)
            hideDelay = Duration.millis(200.0)
            showDuration = Duration.seconds(30.0)
        }
    }

    private fun tooltipBox(palette: TooltipPalette): VBox = VBox(6.0).apply {
        padding = Insets(10.0)
        alignment = Pos.TOP_LEFT
        prefWidth = 220.0
        maxWidth = 220.0
        background = Background(BackgroundFill(palette.background, CornerRadii(8.0), Insets.EMPTY))
        border = Border(BorderStroke(palette.border, BorderStrokeStyle.SOLID, CornerRadii(8.0), BorderWidths(1.0)))
    }

    private fun headerRow(title: String, size: String, palette: TooltipPalette): Node {
        return HBox(8.0).apply {
            alignment = Pos.CENTER_LEFT
            children += Label(title).apply {
                style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: ${palette.headerText.toCss()};"
                maxWidth = 130.0
                textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
            }
            children += HBox().apply { HBox.setHgrow(this, Priority.ALWAYS) }
            children += Label(size).apply {
                style = "-fx-font-size: 13px; -fx-text-fill: ${palette.headerText.toCss()};"
            }
        }
    }

    private fun metricRow(label: String, value: String, palette: TooltipPalette): Node {
        return HBox(6.0).apply {
            alignment = Pos.CENTER_LEFT
            children += Label("$label:").apply {
                style = "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: ${palette.bodyText.toCss()};"
            }
            children += Label(value).apply {
                style = "-fx-font-size: 12px; -fx-text-fill: ${palette.bodyText.toCss()};"
                maxWidth = 130.0
                textOverrun = javafx.scene.control.OverrunStyle.ELLIPSIS
                HBox.setHgrow(this, Priority.ALWAYS)
            }
        }
    }

    private fun commentBlock(text: String, palette: TooltipPalette): Node {
        val trimmed = text.trim()
        val noteHeader = Label(I18n.s("tooltip.note")).apply {
            style = "-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: ${palette.bodyText.toCss()};"
        }
        val noteContent = Label(trimmed).apply {
            isWrapText = true
            maxWidth = 200.0
            style = "-fx-font-size: 12px; -fx-text-fill: ${palette.bodyText.toCss()};"
        }

        val scroll = ScrollPane(noteContent).apply {
            isFitToWidth = true
            prefHeight = 80.0
            maxHeight = 80.0
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            style = "-fx-background-color: transparent; -fx-padding: 0; -fx-background: transparent;"
        }

        return VBox(4.0, noteHeader, scroll)
    }

    private fun usagePieChart(used: Double, free: Double, palette: TooltipPalette): Node {
        val safeUsed = used.coerceAtLeast(0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
        val safeFree = free.coerceAtLeast(0.0).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }

        val data = FXCollections.observableArrayList(
            PieChart.Data(I18n.s("tooltip.used"), safeUsed),
            PieChart.Data(I18n.s("tooltip.free"), safeFree)
        )
        val chart = PieChart(data).apply {
            labelsVisible = false
            isLegendVisible = false
            startAngle = 90.0
            animated = false
            stylePieSlice(data[0], palette.barFill)
            stylePieSlice(data[1], palette.barBackground)
        }
        return StackPane(chart).apply {
            prefHeight = 120.0
            prefWidth = 120.0
            maxHeight = 120.0
            maxWidth = 140.0
            chart.prefHeightProperty().bind(heightProperty())
            chart.prefWidthProperty().bind(widthProperty())
        }
    }

    private fun stylePieSlice(slice: PieChart.Data, color: Color) {
        fun apply(node: Node?) {
            node?.style = "-fx-pie-color: ${color.toCss()};"
        }
        apply(slice.node)
        slice.nodeProperty().addListener { _, _, node -> apply(node) }
    }

    private fun tooltipStyle(): String = buildString {
        append("-fx-background-color: transparent;")
        append("-fx-border-color: transparent;")
        append("-fx-padding: 0;")
    }
}
