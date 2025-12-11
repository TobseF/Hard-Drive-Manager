package de.tfr.tool.ui.dialog

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.formatSize
import javafx.beans.property.SimpleDoubleProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.chart.PieChart
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.Modality
import kotlin.math.ceil

/**
 * Data class to hold partition info for the disk bar visualization.
 * @param name The name of the partition
 * @param sizeMB The size of the partition in MB
 * @param isEditing Whether this is the partition currently being edited
 */
data class PartitionInfo(
    val name: String,
    val sizeMB: Double,
    val isEditing: Boolean = false
)

class SizeEditorDialog(
    title: String,
    initialSizeMB: Double,
    initialUsedMB: Double,
    /** The total size of the parent disk in MB (for percentage calculation). Null for disks. */
    private val diskSizeMB: Double? = null,
    /** All partitions on the disk for the bar chart visualization. Empty for disks. */
    private val allPartitions: List<PartitionInfo> = emptyList(),
    /** The name of the partition being edited (for highlighting in bar chart) */
    private val editingPartitionName: String? = null
) : Dialog<Pair<Double, Double>>() {

    private val sizeMBProperty = SimpleDoubleProperty(initialSizeMB)
    private val usedMBProperty = SimpleDoubleProperty(initialUsedMB)

    /** The available free space on disk = disk size - size of all OTHER partitions */
    private val availableFreeSpaceMB: Double = if (diskSizeMB != null && diskSizeMB > 0) {
        val otherPartitionsSize = allPartitions
            .filter { !it.isEditing && it.name != editingPartitionName }
            .sumOf { it.sizeMB }
        (diskSizeMB - otherPartitionsSize).coerceAtLeast(0.0)
    } else {
        0.0
    }

    private val unitGroup = ToggleGroup()
    private val sizeField = TextField()
    private val usedField = TextField()
    private val percentSlider = Slider(0.0, 100.0, 0.0)
    private val diskPercentSlider = Slider(0.0, 100.0, 0.0)
    private val freeLabel = Label()
    private val pieChart = PieChart()
    private val diskBarContainer = HBox()

    private var currentUnit = SizeUnit.GB
    private var isUpdating = false

    init {
        initOwner(null)
        initModality(Modality.APPLICATION_MODAL)
        setTitle(title)

        dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        dialogPane.content = buildContent()

        // Initialize fields
        updateFieldsFromMB()
        updateCalculatedValues()

        // Setup listeners
        setupListeners()

        // Result converter
        setResultConverter { buttonType ->
            if (buttonType == ButtonType.OK) {
                Pair(sizeMBProperty.get(), usedMBProperty.get())
            } else {
                null
            }
        }
    }

    private fun buildContent(): VBox {
        return VBox(15.0).apply {
            padding = Insets(20.0)
            prefWidth = 500.0

            // Unit selection
            children += HBox(10.0).apply {
                alignment = Pos.CENTER_LEFT
                children += Label(I18n.s("dialog.size.unit")).apply {
                    style = "-fx-font-weight: bold;"
                }
                children += createUnitToggle(SizeUnit.MB)
                children += createUnitToggle(SizeUnit.GB)
                children += createUnitToggle(SizeUnit.TB)
            }

            // Size input
            children += createInputRow(I18n.s("dialog.size.total"), sizeField)

            // Used free space percentage slider (only for partitions, when free space is available)
            if (availableFreeSpaceMB > 0) {
                children += HBox(10.0).apply {
                    alignment = Pos.CENTER_LEFT
                    children += Label(I18n.s("dialog.size.usedFreeSpace")).apply {
                        style = "-fx-font-weight: bold;"
                        minWidth = 120.0
                    }
                    children += diskPercentSlider.apply {
                        maxWidth = Double.MAX_VALUE
                        HBox.setHgrow(this, Priority.ALWAYS)
                        isShowTickLabels = true
                        isShowTickMarks = true
                        majorTickUnit = 25.0
                        minorTickCount = 4
                        isSnapToTicks = false
                        blockIncrement = 5.0
                        // Initialize: current partition size as percentage of available free space
                        // 100% = uses entire available free space
                        value = (sizeMBProperty.get() / availableFreeSpaceMB * 100.0).coerceIn(0.0, 100.0)
                    }
                    children += Label().apply {
                        textProperty().bind(diskPercentSlider.valueProperty().asString("%.1f %%"))
                        minWidth = 60.0
                        style = "-fx-font-weight: bold;"
                    }
                }
            }

            // Used input
            children += createInputRow(I18n.s("dialog.size.used"), usedField)

            // Percentage slider (used/total)
            children += HBox(10.0).apply {
                alignment = Pos.CENTER_LEFT
                children += Label(I18n.s("dialog.size.percent")).apply {
                    style = "-fx-font-weight: bold;"
                    minWidth = 120.0
                }
                children += percentSlider.apply {
                    maxWidth = Double.MAX_VALUE
                    HBox.setHgrow(this, Priority.ALWAYS)
                    isShowTickLabels = true
                    isShowTickMarks = true
                    majorTickUnit = 25.0
                    minorTickCount = 4
                    isSnapToTicks = false
                    blockIncrement = 5.0
                }
                children += Label().apply {
                    textProperty().bind(percentSlider.valueProperty().asString("%.1f %%"))
                    minWidth = 60.0
                    style = "-fx-font-weight: bold;"
                }
            }

            // Separator
            children += Separator()

            // Calculated values
            children += HBox(10.0).apply {
                alignment = Pos.CENTER_LEFT
                children += Label(I18n.s("dialog.size.free")).apply {
                    style = "-fx-font-weight: bold;"
                    minWidth = 120.0
                }
                children += freeLabel
            }

            // Disk Bar Chart (only for partitions)
            if (allPartitions.isNotEmpty()) {
                children += Separator()
                children += Label(I18n.s("dialog.size.diskLayout")).apply {
                    style = "-fx-font-weight: bold;"
                }
                children += createDiskBarChart()
            }

            // PieChart
            children += createPieChart()
        }
    }

    private fun createUnitToggle(unit: SizeUnit): RadioButton {
        return RadioButton(unit.name).apply {
            toggleGroup = unitGroup
            isSelected = (unit == currentUnit)
            setOnAction {
                if (isSelected) {
                    currentUnit = unit
                    updateFieldsFromMB()
                }
            }
        }
    }

    private fun createInputRow(label: String, field: TextField): HBox {
        return HBox(10.0).apply {
            alignment = Pos.CENTER_LEFT
            children += Label(label).apply {
                style = "-fx-font-weight: bold;"
                minWidth = 120.0
            }
            children += field.apply {
                prefWidth = 200.0
                promptText = "0.0"
            }
        }
    }

    private fun createDiskBarChart(): VBox {
        return VBox(5.0).apply {
            padding = Insets(10.0, 0.0, 10.0, 0.0)

            diskBarContainer.apply {
                prefHeight = 40.0
                minHeight = 40.0
                maxHeight = 40.0
                prefWidth = 450.0
                maxWidth = 450.0
                style = "-fx-border-color: #888888; -fx-border-width: 1px; -fx-background-color: #3a3e40;"
                // Clip content to prevent overflow
                clip = Rectangle(450.0, 40.0)
            }

            children += diskBarContainer
            updateDiskBarChart()
        }
    }

    private fun updateDiskBarChart() {
        diskBarContainer.children.clear()

        if (diskSizeMB == null || diskSizeMB <= 0 || allPartitions.isEmpty()) {
            return
        }

        val barWidth = 450.0 // Fixed width for the bar

        // Colors for partitions
        val normalColor = Color.web("#5a5e60")
        val editingColor = Color.web("#4aa3ff")
        val unallocatedColor = Color.web("#2a2e30")

        // Calculate total partition size first (clamped to disk size)
        var totalPartitionSize = 0.0
        for (partition in allPartitions) {
            val isCurrentPartition = partition.isEditing || partition.name == editingPartitionName
            val partitionSize = if (isCurrentPartition) {
                sizeMBProperty.get().coerceAtMost(availableFreeSpaceMB)
            } else {
                partition.sizeMB
            }
            totalPartitionSize += partitionSize
        }

        // Clamp total to disk size to prevent overflow
        totalPartitionSize = totalPartitionSize.coerceAtMost(diskSizeMB)

        for (partition in allPartitions) {
            val isCurrentPartition = partition.isEditing || partition.name == editingPartitionName

            // Use updated size for the editing partition, clamped to available space
            val partitionSize = if (isCurrentPartition) {
                sizeMBProperty.get().coerceAtMost(availableFreeSpaceMB)
            } else {
                partition.sizeMB
            }

            val displayWidth = (partitionSize / diskSizeMB) * barWidth

            if (displayWidth > 0) {
                val rect = Rectangle(displayWidth.coerceAtLeast(2.0), 38.0).apply {
                    fill = if (isCurrentPartition) editingColor else normalColor
                    stroke = Color.web("#1a1e20")
                    strokeWidth = 1.0
                }

                val tooltip = Tooltip("${partition.name}\n${partitionSize.formatSize()}")
                Tooltip.install(rect, tooltip)

                diskBarContainer.children += rect
            }
        }

        // Show unallocated space
        val unallocatedMB = (diskSizeMB - totalPartitionSize).coerceAtLeast(0.0)
        if (unallocatedMB > 0) {
            val unallocatedWidth = (unallocatedMB / diskSizeMB) * barWidth
            if (unallocatedWidth > 2) {
                val rect = Rectangle(unallocatedWidth, 38.0).apply {
                    fill = unallocatedColor
                    stroke = Color.web("#1a1e20")
                    strokeWidth = 1.0
                }
                val tooltip = Tooltip("${I18n.s("dialog.size.unallocated")}\n${unallocatedMB.formatSize()}")
                Tooltip.install(rect, tooltip)
                diskBarContainer.children += rect
            }
        }
    }

    private fun createPieChart(): StackPane {
        pieChart.apply {
            labelsVisible = false
            isLegendVisible = false
            startAngle = 90.0
            animated = false
            prefHeight = 200.0
            prefWidth = 200.0
        }
        return StackPane(pieChart).apply {
            alignment = Pos.CENTER
            padding = Insets(10.0)
        }
    }

    private fun setupListeners() {
        sizeField.textProperty().addListener { _, _, newValue ->
            if (!isUpdating) {
                parseAndUpdateSize(newValue)
            }
        }

        usedField.textProperty().addListener { _, _, newValue ->
            if (!isUpdating) {
                parseAndUpdateUsed(newValue)
            }
        }

        // Slider aktualisiert den Used-Wert basierend auf dem Prozentsatz
        percentSlider.valueProperty().addListener { _, _, newValue ->
            if (!isUpdating) {
                val sizeMB = sizeMBProperty.get()
                val newUsedMB = sizeMB * (newValue.toDouble() / 100.0)
                usedMBProperty.set(newUsedMB)
                updateFieldsFromMB()
            }
        }

        // Used free space percentage slider updates the partition size
        // 100% = partition uses entire available free space
        if (availableFreeSpaceMB > 0) {
            diskPercentSlider.valueProperty().addListener { _, _, newValue ->
                if (!isUpdating) {
                    val newSizeMB = availableFreeSpaceMB * (newValue.toDouble() / 100.0)
                    sizeMBProperty.set(newSizeMB)
                    updateFieldsFromMB()
                    updateDiskBarChart()
                }
            }
        }

        sizeMBProperty.addListener { _, _, _ ->
            if (!isUpdating) {
                updateCalculatedValues()
                updateDiskBarChart()
            }
        }

        usedMBProperty.addListener { _, _, _ ->
            if (!isUpdating) {
                updateCalculatedValues()
            }
        }
    }

    private fun parseAndUpdateSize(text: String) {
        val value = text.replace(",", ".").toDoubleOrNull()
        if (value != null && value >= 0) {
            var newSizeMB = convertToMB(value, currentUnit)
            // Limit to available free space if editing a partition
            if (availableFreeSpaceMB > 0) {
                newSizeMB = newSizeMB.coerceAtMost(availableFreeSpaceMB)
            }
            sizeMBProperty.set(newSizeMB)
            updateCalculatedValues()
            // Update field to show clamped value
            updateFieldsFromMB()
        }
    }

    private fun parseAndUpdateUsed(text: String) {
        val value = text.replace(",", ".").toDoubleOrNull()
        if (value != null && value >= 0) {
            usedMBProperty.set(convertToMB(value, currentUnit))
            updateCalculatedValues()
        }
    }

    private fun updateFieldsFromMB() {
        isUpdating = true
        sizeField.text = String.format("%.2f", convertFromMB(sizeMBProperty.get(), currentUnit))
        usedField.text = String.format("%.2f", convertFromMB(usedMBProperty.get(), currentUnit))
        isUpdating = false
    }

    private fun updateCalculatedValues() {
        isUpdating = true

        val sizeMB = sizeMBProperty.get()
        val usedMB = usedMBProperty.get().coerceAtMost(sizeMB)
        val freeMB = (sizeMB - usedMB).coerceAtLeast(0.0)

        // Update percentage slider (used/total)
        val percent = if (sizeMB > 0) (usedMB / sizeMB * 100) else 0.0
        percentSlider.value = percent

        // Update used free space percentage slider (100% = uses entire available free space)
        if (availableFreeSpaceMB > 0) {
            val usedFreeSpacePercent = (sizeMB / availableFreeSpaceMB * 100).coerceIn(0.0, 100.0)
            diskPercentSlider.value = usedFreeSpacePercent
        }

        // Update free space
        freeLabel.text = freeMB.formatSize()

        // Update PieChart
        updatePieChart(usedMB, freeMB)

        isUpdating = false
    }

    private fun updatePieChart(used: Double, free: Double) {
        val data = FXCollections.observableArrayList(
            PieChart.Data(I18n.s("dialog.size.used"), used.coerceAtLeast(0.0)),
            PieChart.Data(I18n.s("dialog.size.free"), free.coerceAtLeast(0.0))
        )
        pieChart.data = data

        // Style slices
        if (data.size >= 2) {
            stylePieSlice(data[0], Color.web("#4aa3ff"))
            stylePieSlice(data[1], Color.web("#5a5e60"))
        }
    }

    private fun stylePieSlice(slice: PieChart.Data, color: Color) {
        fun apply(node: javafx.scene.Node?) {
            node?.style = "-fx-pie-color: #${color.toString().substring(2, 8)};"
        }
        apply(slice.node)
        slice.nodeProperty().addListener { _, _, node -> apply(node) }
    }

    private fun convertToMB(value: Double, unit: SizeUnit): Double {
        return when (unit) {
            SizeUnit.MB -> value
            SizeUnit.GB -> value * 1024.0
            SizeUnit.TB -> ceil(value * 1024.0 * 1024.0) // Auf ganze MB aufrunden bei TB
        }
    }

    private fun convertFromMB(mb: Double, unit: SizeUnit): Double {
        return when (unit) {
            SizeUnit.MB -> mb
            SizeUnit.GB -> mb / 1024.0
            SizeUnit.TB -> mb / 1024.0 / 1024.0
        }
    }

    enum class SizeUnit {
        MB, GB, TB
    }
}
