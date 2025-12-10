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
import javafx.stage.Modality
import kotlin.math.ceil

class SizeEditorDialog(
    title: String,
    initialSizeMB: Double,
    initialUsedMB: Double
) : Dialog<Pair<Double, Double>>() {

    private val sizeMBProperty = SimpleDoubleProperty(initialSizeMB)
    private val usedMBProperty = SimpleDoubleProperty(initialUsedMB)

    private val unitGroup = ToggleGroup()
    private val sizeField = TextField()
    private val usedField = TextField()
    private val percentSlider = Slider(0.0, 100.0, 0.0)
    private val freeLabel = Label()
    private val pieChart = PieChart()

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
            prefWidth = 450.0

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

            // Used input
            children += createInputRow(I18n.s("dialog.size.used"), usedField)

            // Percentage slider
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

        sizeMBProperty.addListener { _, _, _ ->
            if (!isUpdating) {
                updateCalculatedValues()
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
            sizeMBProperty.set(convertToMB(value, currentUnit))
            updateCalculatedValues()
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

        // Update percentage slider
        val percent = if (sizeMB > 0) (usedMB / sizeMB * 100) else 0.0
        percentSlider.value = percent

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

