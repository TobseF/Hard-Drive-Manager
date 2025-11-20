package de.tfr.tool.ui

import de.tfr.tool.model.Disk
import javafx.geometry.Insets
import javafx.scene.chart.PieChart
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.FlowPane

/**
 * Encapsulates the complete statistics view (pie charts) from the MainView.
 *
 * Calling component passes the current disks via [updateData].
 * Language changes are applied via [applyTranslations].
 */
class TabStatistics : ScrollPane() {

    private val container = FlowPane().apply {
        padding = Insets(12.0)
        hgap = 16.0
        vgap = 16.0
    }

    private val pieTotalFreeUsed = PieChart().apply { title = I18n.s("stats.total.title") }
    private val pieCapacityPerDisk = PieChart().apply { title = I18n.s("stats.capacityPerDisk.title") }
    private val pieUsedByTags = PieChart().apply { title = I18n.s("stats.usedByTags.title") }

    init {
        isFitToWidth = true
        hbarPolicy = ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollBarPolicy.AS_NEEDED

        listOf(pieTotalFreeUsed, pieCapacityPerDisk, pieUsedByTags).forEach { chart ->
            chart.labelsVisibleProperty().set(false)
            chart.legendVisibleProperty().set(true)
            chart.maxWidth = 400.0
            chart.setPrefSize(400.0, 360.0)
            chart.minHeight = 300.0
        }

        container.children += pieTotalFreeUsed
        container.children += pieCapacityPerDisk
        container.children += pieUsedByTags

        content = container
    }

    fun applyTranslations() {
        pieTotalFreeUsed.title = I18n.s("stats.total.title")
        pieCapacityPerDisk.title = I18n.s("stats.capacityPerDisk.title")
        pieUsedByTags.title = I18n.s("stats.usedByTags.title")
        // Tooltips will be reset at next updateData
    }

    fun updateData(disks: List<Disk>) {
        // 1) Total free vs. used
        val totalSize = disks.sumOf { it.sizeTB.coerceAtLeast(0.0) }
        val totalUsed = disks.sumOf { it.usedTB.coerceAtLeast(0.0) }
        val totalFree = (totalSize - totalUsed).coerceAtLeast(0.0)
        setPieData(
            pieTotalFreeUsed,
            listOf(
                I18n.s("col.used") to totalUsed,
                I18n.s("col.free") to totalFree
            )
        )

        // 2) Total capacity per disk
        val byDisk = disks.map { it.name to it.sizeTB.coerceAtLeast(0.0) }
        setPieData(pieCapacityPerDisk, byDisk)

        // 3) Used by tags (Partition.Tags, Fallback auf Disk.Tag, oder "Ungetaggt")
        val tagMap = linkedMapOf<String, Double>()
        disks.forEach { d ->
            d.partitions.forEach { p ->
                val tags = p.tags
                    .split(',', ';', '|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty {
                        val fallback = d.tag.trim()
                        if (fallback.isNotEmpty()) listOf(fallback) else listOf(I18n.s("stats.byTag.fallback"))
                    }
                tags.forEach { t ->
                    tagMap[t] = (tagMap[t] ?: 0.0) + p.usedTB.coerceAtLeast(0.0)
                }
            }
        }
        val byTag = tagMap.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }
        setPieData(pieUsedByTags, byTag)
    }

    private fun setPieData(chart: PieChart, pairs: List<Pair<String, Double>>) {
        val filtered = pairs.filter { it.second > 0.0 }
        val total = filtered.sumOf { it.second }
        val data = if (filtered.isEmpty()) {
            // Empty state: Dummy slice with 1.0
            listOf(PieChart.Data(I18n.s("stats.none"), 1.0))
        } else {
            filtered.map { (name, value) -> PieChart.Data(name, value) }
        }
        chart.data.setAll(data)

        // Tooltips with TB + percent
        val denom = if (total > 0.0) total else data.sumOf { it.pieValue }
        chart.data.forEach { d ->
            val value = d.pieValue
            val pct = if (denom > 0.0) (value / denom) * 100.0 else 0.0
            val label = if (filtered.isEmpty()) I18n.s("stats.none") else I18n.s(
                "stats.tooltip.value",
                d.name,
                String.format("%.1f", value),
                String.format("%.1f", pct)
            )
            val tip = Tooltip(label)
            // d.node may not exist until after layout; Tooltip.install does not tolerate null,
            if (d.node != null) Tooltip.install(d.node, tip) else chart.applyCss().also { d.node?.let { Tooltip.install(it, tip) } }
        }
    }
}
