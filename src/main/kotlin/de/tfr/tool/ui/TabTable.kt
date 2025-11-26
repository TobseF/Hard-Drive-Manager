package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.model.*
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.persist.Settings
import de.tfr.tool.ui.context.ContextMenuFactory
import de.tfr.tool.ui.settings.ColumnVisibilityDialog
import de.tfr.tool.ui.util.DialogHelper
import de.tfr.tool.ui.util.TabTableNameFormatter
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTreeTableCell
import javafx.scene.control.cell.TextFieldTreeTableCell
import javafx.scene.layout.*
import javafx.util.StringConverter
import mu.KotlinLogging
import org.kordamp.ikonli.materialdesign2.MaterialDesignT
import org.kordamp.ikonli.materialdesign2.MaterialDesignU

/**
 * Encapsulates all table UI and logic previously mixed into MainView.
 * Provides an updateData(list) to rebuild the tree and exposes [tree] for external utilities (CSV export, etc.).
 */
class TabTable(
    private val showHiddenProp: BooleanProperty = SimpleBooleanProperty(false),
    private val onDataChanged: () -> Unit = {}
) : VBox(8.0) {

    private val logger = KotlinLogging.logger {}

    // Expose the TreeTableView for external usages (e.g., CSV export)
    val tree: TreeTableView<Any> = TreeTableView()

    // Property to control showing only partitions (hiding disks)
    private val onlyPartitionsProp = SimpleBooleanProperty(false)

    // Toolbar controls need to update texts on language change
    private val editBtn = ToggleButton()
    private val addDiskBtn = Button()
    private val addPartBtn = Button()
    private val delBtn = Button()
    private val expandAllBtn = Button()
    private val collapseAllBtn = Button()
    private val toggleHidden = ToggleButton()
    private val toggleOnlyPartitions = ToggleButton()
    private val configColumnsBtn = Button()

    // Keep references to important columns to update captions on translation/theme changes
    private lateinit var nameCol: TreeTableColumn<Any, String>
    private lateinit var typeCol: TreeTableColumn<Any, String>
    private lateinit var letterCol: TreeTableColumn<Any, String>
    private lateinit var sizeCol: TreeTableColumn<Any, String>
    private lateinit var usedCol: TreeTableColumn<Any, String>
    private lateinit var freeCol: TreeTableColumn<Any, String>
    private lateinit var percentTextCol: TreeTableColumn<Any, String>
    private lateinit var partOfDiskBarCol: TreeTableColumn<Any, Double>
    private lateinit var barCol: TreeTableColumn<Any, Double>
    private lateinit var tagCol: TreeTableColumn<Any, String>
    private lateinit var modelCol: TreeTableColumn<Any, String>
    private lateinit var manufacturerCol: TreeTableColumn<Any, String>
    private lateinit var serialCol: TreeTableColumn<Any, String>
    private lateinit var uuidCol: TreeTableColumn<Any, String>
    private lateinit var fsTypeCol: TreeTableColumn<Any, String>
    private lateinit var encCol: TreeTableColumn<Any, Boolean>
    private lateinit var cloudCol: TreeTableColumn<Any, Boolean>
    private lateinit var virtualCol: TreeTableColumn<Any, Boolean>
    private lateinit var hiddenCol: TreeTableColumn<Any, Boolean>

    // last provided dataset, needed for certain tooltips
    private var currentDisks: List<Disk> = emptyList()

    // Context menu items
    private val contextMenu = ContextMenu()
    private val deleteMenuItem = MenuItem()
    private val moveMenuItem = MenuItem()
    private val renameMenuItem = MenuItem()

    init {
        padding = Insets(8.0)
        children += buildToolbar()
        setVgrow(buildTable(), Priority.ALWAYS)
        children += tree

        // Initialize context menu
        setupContextMenu()

        // Reload table when "only partitions" setting changes
        onlyPartitionsProp.addListener { _, _, _ ->
            updateData(currentDisks)
        }
    }

    private fun buildToolbar(): Node {
        val row = HBox(8.0)
        row.alignment = Pos.CENTER_LEFT

        editBtn.selectedProperty().addListener { _, _, new -> tree.isEditable = new }
        editBtn.text = I18n.s("btn.edit")
        editBtn.selectedProperty().addListener { _ , _, selected ->
            editBtn.text = if (selected) I18n.s("btn.done") else I18n.s("btn.edit")
        }

        addDiskBtn.text = I18n.s("btn.add.disk")
        addDiskBtn.setOnAction { onAddDisk() }

        addPartBtn.text = I18n.s("btn.add.partition")
        addPartBtn.setOnAction { onAddPartition() }

        delBtn.text = I18n.s("btn.delete")
        delBtn.setOnAction { onDeleteSelected() }

        expandAllBtn.tooltip = Tooltip(I18n.s("btn.expand.all"))
        expandAllBtn.graphic = ThemeManager.currentTheme.createIcon(MaterialDesignU.UNFOLD_MORE_HORIZONTAL)
        expandAllBtn.setOnAction { expandAll() }

        collapseAllBtn.tooltip = Tooltip(I18n.s("btn.collapse.all"))
        collapseAllBtn.graphic = ThemeManager.currentTheme.createIcon(MaterialDesignU.UNFOLD_LESS_HORIZONTAL)
        collapseAllBtn.setOnAction { collapseAll() }

        toggleHidden.selectedProperty().bindBidirectional(showHiddenProp)
        toggleHidden.text = if (showHiddenProp.get()) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")
        toggleHidden.selectedProperty().addListener { _, _, selected ->
            toggleHidden.text = if (selected) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")
        }

        toggleOnlyPartitions.selectedProperty().bindBidirectional(onlyPartitionsProp)
        toggleOnlyPartitions.text = I18n.s("btn.onlyPartitions")

        configColumnsBtn.tooltip = Tooltip(I18n.s("table.columnVisibility.button"))
        configColumnsBtn.graphic = ThemeManager.currentTheme.createIcon(MaterialDesignT.TABLE_SETTINGS)
        configColumnsBtn.setOnAction {
            val result = ColumnVisibilityDialog.show()
            if (result != null) {
                reloadColumnVisibility()
            }
        }

        row.children += listOf(
            editBtn,
            Separator(),
            addDiskBtn,
            addPartBtn,
            delBtn,
            Separator(),
            expandAllBtn,
            collapseAllBtn,
            Separator(),
            toggleHidden,
            toggleOnlyPartitions,
            Separator(),
            configColumnsBtn
        )
        return row
    }


    private fun applySorting() {
        if (currentDisks.isEmpty()) return

        val currentSort = SortConfiguration(Settings.Table.sortField, Settings.Table.sortDirection)
        val sortComparator = getSortComparator(currentSort.fieldName, currentSort.direction)

        // Sort disks and their partitions (cast to mutable list if needed)
        val disksList = currentDisks.toMutableList()
        disksList.sortWith(sortComparator)

        // Sort partitions within each disk
        disksList.forEach { disk ->
            disk.partitions.sortWith(sortComparator)
        }

        // Replace currentDisks with sorted version
        this.currentDisks = disksList
    }

    private fun getSortComparator(fieldName: String, direction: SortDirection): Comparator<Any> {
        val baseComparator = Comparator<Any> { o1, o2 ->
            when (fieldName) {
                "name" -> {
                    val name1 = when (o1) {
                        is Disk -> o1.name
                        is Partition -> o1.name
                        else -> ""
                    }
                    val name2 = when (o2) {
                        is Disk -> o2.name
                        is Partition -> o2.name
                        else -> ""
                    }
                    name1.compareTo(name2)
                }

                "type" -> {
                    val type1 = when (o1) {
                        is Disk -> o1.type
                        is Partition -> o1.type
                        else -> ""
                    }
                    val type2 = when (o2) {
                        is Disk -> o2.type
                        is Partition -> o2.type
                        else -> ""
                    }
                    type1.compareTo(type2)
                }

                "size" -> {
                    val size1 = when (o1) {
                        is Disk -> o1.sizeMB
                        is Partition -> o1.sizeMB
                        else -> 0.0
                    }
                    val size2 = when (o2) {
                        is Disk -> o2.sizeMB
                        is Partition -> o2.sizeMB
                        else -> 0.0
                    }
                    size1.compareTo(size2)
                }

                "used" -> {
                    val used1 = when (o1) {
                        is Disk -> o1.usedMB
                        is Partition -> o1.usedMB
                        else -> 0.0
                    }
                    val used2 = when (o2) {
                        is Disk -> o2.usedMB
                        is Partition -> o2.usedMB
                        else -> 0.0
                    }
                    used1.compareTo(used2)
                }

                "free" -> {
                    val free1 = when (o1) {
                        is Disk -> (o1.sizeMB - o1.usedMB).coerceAtLeast(0.0)
                        is Partition -> (o1.sizeMB - o1.usedMB).coerceAtLeast(0.0)
                        else -> 0.0
                    }
                    val free2 = when (o2) {
                        is Disk -> (o2.sizeMB - o2.usedMB).coerceAtLeast(0.0)
                        is Partition -> (o2.sizeMB - o2.usedMB).coerceAtLeast(0.0)
                        else -> 0.0
                    }
                    free1.compareTo(free2)
                }

                "letter" -> {
                    val letter1 = (o1 as? Partition)?.letter ?: ""
                    val letter2 = (o2 as? Partition)?.letter ?: ""
                    letter1.compareTo(letter2)
                }

                "tags" -> {
                    val tags1 = when (o1) {
                        is Disk -> o1.tag
                        is Partition -> o1.tags
                        else -> ""
                    }
                    val tags2 = when (o2) {
                        is Disk -> o2.tag
                        is Partition -> o2.tags
                        else -> ""
                    }
                    tags1.compareTo(tags2)
                }

                else -> 0
            }
        }

        return if (direction == SortDirection.DESCENDING) {
            baseComparator.reversed()
        } else {
            baseComparator
        }
    }

    private fun buildTable(): Node {
        tree.isShowRoot = false
        tree.isEditable = false

        nameCol = TreeTableColumn<Any, String>(I18n.s("col.name")).apply {
            prefWidth = 250.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.name)
                    is Partition -> SimpleStringProperty(v.name)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                when (val item = ev.rowValue.value) {
                    is Disk -> { item.name = ev.newValue; DiskRepository.updateDisk(item) }
                    is Partition -> { item.name = ev.newValue; DiskRepository.updatePartition(item) }
                }
                onDataChanged()
            }
        }

        letterCol = TreeTableColumn<Any, String>(I18n.s("col.letter")).apply {
            prefWidth = 80.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> SimpleStringProperty(v.letter)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Partition) {
                    item.letter = ev.newValue
                    DiskRepository.updatePartition(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        typeCol = TreeTableColumn<Any, String>(I18n.s("col.type")).apply {
            prefWidth = 120.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.type)
                    is Partition -> SimpleStringProperty(v.type)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                when (val item = ev.rowValue.value) {
                    is Disk -> { item.type = ev.newValue; DiskRepository.updateDisk(item) }
                    is Partition -> { item.type = ev.newValue; DiskRepository.updatePartition(item) }
                }
                onDataChanged()
            }
        }

        sizeCol = TreeTableColumn<Any, String>(I18n.s("col.size")).apply {
            prefWidth = 100.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.sizeMB.formatSize())
                    is Partition -> SimpleStringProperty(v.sizeMB.formatSize())
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                val raw = ev.newValue.trim().removeSuffix("TB").trim().replace(',', '.')
                val newVal = raw.toDoubleOrNull()
                if (newVal != null) {
                    when (item) {
                        is Disk -> { item.sizeTB = newVal; DiskRepository.updateDisk(item) }
                        is Partition -> { item.sizeTB = newVal; DiskRepository.updatePartition(item) }
                    }
                    onDataChanged()
                } else {
                    tree.refresh()
                }
            }
        }

        usedCol = TreeTableColumn<Any, String>(I18n.s("col.used")).apply {
            prefWidth = 100.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.usedMB.formatSize())
                    is Partition -> SimpleStringProperty(v.usedMB.formatSize())
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                val raw = ev.newValue.trim().removeSuffix("TB").trim().replace(',', '.')
                val newVal = raw.toDoubleOrNull()
                if (newVal != null) {
                    when (item) {
                        is Disk -> { /* derived */ }
                        is Partition -> { item.usedTB = newVal; DiskRepository.updatePartition(item) }
                    }
                    onDataChanged()
                } else tree.refresh()
            }
        }

        freeCol = TreeTableColumn<Any, String>(I18n.s("col.free")).apply {
            prefWidth = 100.0
            setCellValueFactory { data ->
                val free = when (val v = data.value.value) {
                    is Disk -> (v.sizeMB - v.usedMB).coerceAtLeast(0.0)
                    is Partition -> (v.sizeMB - v.usedMB).coerceAtLeast(0.0)
                    else -> 0.0
                }
                SimpleStringProperty(free.formatSize())
            }
        }

        percentTextCol = TreeTableColumn<Any, String>(I18n.s("col.percentUsed")).apply {
            prefWidth = 90.0
            setCellValueFactory { data ->
                val v = data.value.value
                val (used, total) = when (v) {
                    is Disk -> v.usedTB to v.sizeTB
                    is Partition -> v.usedTB to v.sizeTB
                    else -> 0.0 to 0.0
                }
                val pct = used.percentOf(total)
                val txt = String.format("%d %%", Math.round(pct * 100))
                SimpleStringProperty(txt)
            }
        }

        barCol = TreeTableColumn<Any, Double>(I18n.s("col.usedBar")).apply {
            prefWidth = 140.0
            setCellValueFactory { data ->
                val pct = when (val v = data.value.value) {
                    is Disk -> v.usedTB.percentOf(v.sizeTB)
                    is Partition -> v.usedTB.percentOf(v.sizeTB)
                    else -> 0.0
                }
                SimpleObjectProperty(pct)
            }
            setCellFactory {
                object : TreeTableCell<Any, Double>() {
                    private val bg = StackPane().apply {
                        prefHeight = 12.0; maxHeight = 12.0; minHeight = 12.0
                    }
                    private val bar = Region().apply {
                        prefHeight = 12.0; maxHeight = 12.0; minHeight = 12.0
                    }
                    private val container = StackPane().apply {
                        children.addAll(bg, bar)
                        padding = Insets(2.0, 6.0, 2.0, 6.0)
                        StackPane.setAlignment(bar, Pos.CENTER_LEFT)
                    }
                    private val tip = Tooltip()
                    private var pctValue: Double = 0.0
                    private val themeListener: (Theme) -> Unit = { updateTheme(it) }

                    init {
                        container.widthProperty().addListener { _, _, _ -> updateBarWidth() }
                        updateTheme(ThemeManager.currentTheme)
                        ThemeManager.addListener(themeListener)
                    }

                    private fun updateBarWidth() {
                        val pct = pctValue.coerceIn(0.0, 1.0)
                        val w = (container.width - container.padding.left - container.padding.right).coerceAtLeast(0.0)
                        val barW = w * pct
                        if (barW.isFinite()) {
                            bar.prefWidth = barW
                            bar.minWidth = bar.prefWidth
                            bar.maxWidth = bar.prefWidth
                        }
                    }

                    private fun updateTheme(theme: Theme) {
                        if (theme == Theme.DARK) {
                            bg.style = "-fx-background-color: #3f4447; -fx-background-radius: 6; -fx-border-color: #55595c; -fx-border-width: 1; -fx-border-radius: 6;"
                            bar.style = "-fx-background-color: #4aa3ff; -fx-background-radius: 6;"
                        } else {
                            bg.style = "-fx-background-color: white; -fx-background-radius: 6; -fx-border-color: rgba(0,0,0,0.18); -fx-border-width: 1; -fx-border-radius: 6;"
                            bar.style = "-fx-background-color: #2b6cb0; -fx-background-radius: 6;"
                        }
                    }

                    override fun updateItem(item: Double?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            graphic = null
                            text = null
                            tooltip = null
                            pctValue = 0.0
                            updateBarWidth()
                            return
                        }
                        graphic = container
                        text = null
                        pctValue = item.coerceIn(0.0, 1.0)
                        updateBarWidth()
                        tip.text = I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                        tooltip = tip
                    }
                }
            }
        }

        partOfDiskBarCol = TreeTableColumn<Any, Double>(I18n.s("col.sizeOfDiskBar")).apply {
            prefWidth = 140.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> {
                        val parentDisk = data.value.parent?.value as? Disk
                        val total = parentDisk?.sizeTB ?: 0.0
                        SimpleObjectProperty(v.sizeTB.percentOf(total))
                    }
                    is Disk -> {
                        val totalAll = currentDisks.sumOf { it.sizeTB.coerceAtLeast(0.0) }
                        SimpleObjectProperty(v.sizeTB.percentOf(totalAll))
                    }
                    else -> SimpleObjectProperty(0.0)
                }
            }
            setCellFactory {
                object : TreeTableCell<Any, Double>() {
                    private val bg = StackPane().apply { prefHeight = 12.0; maxHeight = 12.0; minHeight = 12.0 }
                    private val bar = Region().apply { prefHeight = 12.0; maxHeight = 12.0; minHeight = 12.0 }
                    private val container = StackPane().apply {
                        children.addAll(bg, bar)
                        padding = Insets(2.0, 6.0, 2.0, 6.0)
                        StackPane.setAlignment(bar, Pos.CENTER_LEFT)
                    }
                    private val tip = Tooltip()
                    private var pctValue: Double = 0.0
                    private val themeListener: (Theme) -> Unit = { updateTheme(it) }

                    init {
                        container.widthProperty().addListener { _, _, _ -> updateBarWidth() }
                        updateTheme(ThemeManager.currentTheme)
                        ThemeManager.addListener(themeListener)
                    }

                    private fun updateBarWidth() {
                        val pct = pctValue.coerceIn(0.0, 1.0)
                        val w = (container.width - container.padding.left - container.padding.right).coerceAtLeast(0.0)
                        val barW = w * pct
                        if (barW.isFinite()) {
                            bar.prefWidth = barW
                            bar.minWidth = bar.prefWidth
                            bar.maxWidth = bar.prefWidth
                        }
                    }

                    private fun updateTheme(theme: Theme) {
                        if (theme == Theme.DARK) {
                            bg.style = "-fx-background-color: #3f4447; -fx-background-radius: 6; -fx-border-color: #55595c; -fx-border-width: 1; -fx-border-radius: 6;"
                            bar.style = "-fx-background-color: #f2c94c; -fx-background-radius: 6;"
                        } else {
                            bg.style = "-fx-background-color: white; -fx-background-radius: 6; -fx-border-color: rgba(0,0,0,0.18); -fx-border-width: 1; -fx-border-radius: 6;"
                            bar.style = "-fx-background-color: #f6c64a; -fx-background-radius: 6;"
                        }
                    }

                    override fun updateItem(item: Double?, empty: Boolean) {
                        super.updateItem(item, empty)
                        if (empty || item == null) {
                            graphic = null
                            text = null
                            tooltip = null
                            pctValue = 0.0
                            updateBarWidth()
                            return
                        }
                        graphic = container
                        text = null
                        pctValue = item.coerceIn(0.0, 1.0)
                        updateBarWidth()

                        when (val rowObj = tableRow?.item) {
                            is Partition -> {
                                val disk = tableRow.treeItem?.parent?.value as? Disk
                                val total = disk?.sizeMB ?: 0.0
                                val tipTxt = if (total > 0.0) {
                                    val part = rowObj.sizeMB.coerceAtLeast(0.0)
                                    val pct = (part / total) * 100.0
                                    I18n.s(
                                        "fmt.partOfDisk",
                                        rowObj.sizeMB.formatSize(),
                                        total.formatSize(),
                                        String.format("%.1f", pct)
                                    )
                                } else {
                                    I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                                }
                                tip.text = tipTxt
                                tooltip = tip
                            }
                            is Disk -> {
                                val totalAll = currentDisks.sumOf { it.sizeMB.coerceAtLeast(0.0) }
                                val tipTxt = if (totalAll > 0.0) {
                                    val part = rowObj.sizeMB.coerceAtLeast(0.0)
                                    val pct = (part / totalAll) * 100.0
                                    I18n.s(
                                        "fmt.partOfDisk",
                                        rowObj.sizeMB.formatSize(),
                                        totalAll.formatSize(),
                                        String.format("%.1f", pct)
                                    )
                                } else {
                                    I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                                }
                                tip.text = tipTxt
                                tooltip = tip
                            }
                            else -> {
                                tip.text = I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                                tooltip = tip
                            }
                        }
                    }
                }
            }
        }

        tagCol = TreeTableColumn<Any, String>(I18n.s("col.tags")).apply {
            prefWidth = 160.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.tag)
                    is Partition -> SimpleStringProperty(v.tags)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                when (val item = ev.rowValue.value) {
                    is Disk -> { item.tag = ev.newValue; DiskRepository.updateDisk(item) }
                    is Partition -> { item.tags = ev.newValue; DiskRepository.updatePartition(item) }
                }
                onDataChanged()
            }
        }

        modelCol = TreeTableColumn<Any, String>(I18n.s("col.model")).apply {
            prefWidth = 180.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.model)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Disk) {
                    item.model = ev.newValue
                    DiskRepository.updateDisk(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        manufacturerCol = TreeTableColumn<Any, String>(I18n.s("col.manufacturer")).apply {
            prefWidth = 150.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.manufacturer)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Disk) {
                    item.manufacturer = ev.newValue
                    DiskRepository.updateDisk(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        serialCol = TreeTableColumn<Any, String>(I18n.s("col.serial")).apply {
            prefWidth = 180.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> SimpleStringProperty(v.serial)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Disk) {
                    item.serial = ev.newValue
                    DiskRepository.updateDisk(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        encCol = TreeTableColumn<Any, Boolean>(I18n.s("col.encrypted")).apply {
            prefWidth = 90.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> v.encryptedProp.also { prop -> prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) } }
                    else -> SimpleBooleanProperty(false)
                }
            }
            cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(this)
            isEditable = true
        }

        uuidCol = TreeTableColumn<Any, String>(I18n.s("col.uuid")).apply {
            prefWidth = 180.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> SimpleStringProperty(v.uuid)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Partition) {
                    item.uuid = ev.newValue
                    DiskRepository.updatePartition(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        fsTypeCol = TreeTableColumn<Any, String>(I18n.s("col.fsType")).apply {
            prefWidth = 130.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> SimpleStringProperty(v.fsType)
                    else -> SimpleStringProperty("")
                }
            }
            cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
            setOnEditCommit { ev ->
                val item = ev.rowValue.value
                if (item is Partition) {
                    item.fsType = ev.newValue
                    DiskRepository.updatePartition(item)
                    onDataChanged()
                } else tree.refresh()
            }
        }

        cloudCol = TreeTableColumn<Any, Boolean>(I18n.s("col.cloud")).apply {
            prefWidth = 110.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> v.cloudBackupProp.also { prop -> prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) } }
                    else -> SimpleBooleanProperty(false)
                }
            }
            cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(this)
            isEditable = true
        }

        virtualCol = TreeTableColumn<Any, Boolean>(I18n.s("col.virtual")).apply {
            prefWidth = 90.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Partition -> v.virtualProp.also { prop ->
                        prop.addListener { _, _, _ ->
                            DiskRepository.updatePartition(
                                v
                            )
                        }
                    }

                    else -> SimpleBooleanProperty(false)
                }
            }
            cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(this)
            isEditable = true
        }

        hiddenCol = TreeTableColumn<Any, Boolean>(I18n.s("col.hidden")).apply {
            prefWidth = 110.0
            setCellValueFactory { data ->
                when (val v = data.value.value) {
                    is Disk -> v.hiddenProp.also { prop -> prop.addListener { _, _, _ -> DiskRepository.updateDisk(v) } }
                    is Partition -> v.hiddenProp.also { prop -> prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) } }
                    else -> SimpleBooleanProperty(false)
                }
            }
            cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(this)
            isEditable = true
        }

        tree.columns.setAll(
            nameCol,
            typeCol,
            letterCol,
            sizeCol,
            usedCol,
            freeCol,
            percentTextCol,
            partOfDiskBarCol,
            barCol,
            tagCol,
            modelCol,
            manufacturerCol,
            serialCol,
            uuidCol,
            fsTypeCol,
            encCol,
            cloudCol,
            virtualCol,
            hiddenCol
        )

        // Apply column visibility settings
        applyColumnVisibility()

        val listChangeListener: ListChangeListener<TreeTableColumn<Any, *>> = {
            val sortedColumn = tree.sortOrder.firstOrNull()
            if (sortedColumn != null) {
                // Find the column name
                val columnName = when (sortedColumn) {
                    nameCol -> "name"
                    typeCol -> "type"
                    letterCol -> "letter"
                    sizeCol -> "size"
                    usedCol -> "used"
                    freeCol -> "free"
                    percentTextCol -> "percentText"
                    partOfDiskBarCol -> "partOfDiskBar"
                    barCol -> "bar"
                    tagCol -> "tag"
                    modelCol -> "model"
                    manufacturerCol -> "manufacturer"
                    serialCol -> "serial"
                    uuidCol -> "uuid"
                    fsTypeCol -> "fsType"
                    encCol -> "encrypted"
                    cloudCol -> "cloud"
                    virtualCol -> "virtual"
                    hiddenCol -> "hidden"
                    else -> null
                }

                if (columnName != null) {
                    val direction = if (sortedColumn.sortType == TreeTableColumn.SortType.ASCENDING)
                        SortDirection.ASCENDING
                    else
                        SortDirection.DESCENDING

                    Settings.Table.sortField = columnName
                    Settings.Table.sortDirection = direction
                }
            }
        }

        // Add listener for sort order changes to persist sorting configuration
        tree.sortOrder.addListener(listChangeListener)


        return tree
    }

    fun refresh() = tree.refresh()

    fun updateColumnVisibility(visibilityMap: Map<String, Boolean>) {
        // Save to settings
        Settings.Table.showName = visibilityMap["name"] ?: true
        Settings.Table.showType = visibilityMap["type"] ?: true
        Settings.Table.showLetter = visibilityMap["letter"] ?: true
        Settings.Table.showSize = visibilityMap["size"] ?: true
        Settings.Table.showUsed = visibilityMap["used"] ?: true
        Settings.Table.showFree = visibilityMap["free"] ?: true
        Settings.Table.showPercentText = visibilityMap["percentText"] ?: true
        Settings.Table.showPartOfDiskBar = visibilityMap["partOfDiskBar"] ?: true
        Settings.Table.showBar = visibilityMap["bar"] ?: true
        Settings.Table.showTag = visibilityMap["tag"] ?: true
        Settings.Table.showModel = visibilityMap["model"] ?: false
        Settings.Table.showManufacturer = visibilityMap["manufacturer"] ?: false
        Settings.Table.showSerial = visibilityMap["serial"] ?: false
        Settings.Table.showUuid = visibilityMap["uuid"] ?: false
        Settings.Table.showFsType = visibilityMap["fsType"] ?: false
        Settings.Table.showEncrypted = visibilityMap["encrypted"] ?: true
        Settings.Table.showCloud = visibilityMap["cloud"] ?: true
        Settings.Table.showVirtual = visibilityMap["virtual"] ?: false
        Settings.Table.showHiddenCol = visibilityMap["hidden"] ?: true

        // Apply changes immediately
        applyColumnVisibility()
    }

    fun reloadColumnVisibility() {
        applyColumnVisibility()
    }

    fun applyTranslations() {
        // Buttons
        editBtn.text = if (editBtn.isSelected) I18n.s("btn.done") else I18n.s("btn.edit")
        addDiskBtn.text = I18n.s("btn.add.disk")
        addPartBtn.text = I18n.s("btn.add.partition")
        delBtn.text = I18n.s("btn.delete")
        expandAllBtn.tooltip = Tooltip(I18n.s("btn.expand.all"))
        collapseAllBtn.tooltip = Tooltip(I18n.s("btn.collapse.all"))
        toggleHidden.text = if (toggleHidden.isSelected) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")
        toggleOnlyPartitions.text = I18n.s("btn.onlyPartitions")
        configColumnsBtn.tooltip = Tooltip(I18n.s("table.columnVisibility.button"))


        // Columns
        nameCol.text = I18n.s("col.name")
        typeCol.text = I18n.s("col.type")
        sizeCol.text = I18n.s("col.size")
        usedCol.text = I18n.s("col.used")
        freeCol.text = I18n.s("col.free")
        percentTextCol.text = I18n.s("col.percentUsed")
        partOfDiskBarCol.text = I18n.s("col.sizeOfDiskBar")
        barCol.text = I18n.s("col.usedBar")
        tagCol.text = I18n.s("col.tags")
        letterCol.text = I18n.s("col.letter")
        modelCol.text = I18n.s("col.model")
        manufacturerCol.text = I18n.s("col.manufacturer")
        serialCol.text = I18n.s("col.serial")
        uuidCol.text = I18n.s("col.uuid")
        fsTypeCol.text = I18n.s("col.fsType")
        encCol.text = I18n.s("col.encrypted")
        cloudCol.text = I18n.s("col.cloud")
        virtualCol.text = I18n.s("col.virtual")
        hiddenCol.text = I18n.s("col.hidden")

        // Context menu items
        deleteMenuItem.text = I18n.s("menu.context.delete")
        moveMenuItem.text = I18n.s("menu.context.move")
        renameMenuItem.text = I18n.s("menu.context.rename")
        renameMenuItem.setOnAction { onRenameSelected() }
    }

    fun updateData(disks: List<Disk>) {
        this.currentDisks = disks

        // Apply sorting
        applySorting()

        val root = TreeItem<Any>("root")
        val showAll = showHiddenProp.get()
        val onlyPartitions = onlyPartitionsProp.get()

        if (onlyPartitions) {
            // Show only partitions (no disk nodes)
            disks.forEach { d ->
                if (!showAll && d.hidden) return@forEach
                d.partitions.forEach { p ->
                    if (showAll || !p.hidden) {
                        root.children += TreeItem<Any>(p)
                    }
                }
            }
        } else {
            // Show disks with their partitions (normal tree structure)
            disks.forEach { d ->
                if (!showAll && d.hidden) return@forEach
                val diskItem = TreeItem<Any>(d)
                d.partitions.forEach { p -> if (showAll || !p.hidden) diskItem.children += TreeItem<Any>(p) }
                root.children += diskItem
            }
        }

        tree.root = root
        tree.root.isExpanded = true
    }

    fun currentExpandedDiskIds(): Set<Long> {
        val result = mutableSetOf<Long>()
        val r = tree.root ?: return emptySet()
        for (diskItem in r.children) {
            val d = diskItem.value as? Disk ?: continue
            if (diskItem.isExpanded) result += d.id
        }
        return result
    }

    fun setExpandedDiskIds(ids: Set<Long>) {
        val r = tree.root ?: return
        for (diskItem in r.children) {
            val disk = (diskItem.value as? Disk)
            diskItem.isExpanded = disk != null && ids.contains(disk.id)
        }
        if (ids.isEmpty()) {
            for (diskItem in r.children) diskItem.isExpanded = true
        }
    }

    private fun getSelected(): Any? = tree.selectionModel.selectedItem?.value

    private fun selectedDiskOrParent(): Disk? {
        val sel = tree.selectionModel.selectedItem
        return when (val v = sel?.value) {
            is Disk -> v
            is Partition -> sel.parent?.value as? Disk
            else -> null
        }
    }

    private fun onAddDisk() {
        val d = Disk().apply {
            name = I18n.s("default.disk.name")
            sizeTB = 1.0
            type = "HD"
            model = ""
            tag = ""
        }
        val id = DiskRepository.insertDisk(d)
        d.id = id
        onDataChanged()
    }

    private fun onAddPartition() {
        val disk = selectedDiskOrParent()
        if (disk == null) {
            Alert(Alert.AlertType.INFORMATION, I18n.s("alert.add.partition.selectDisk")).showAndWait()
            return
        }
        val p = Partition().apply {
            diskId = disk.id
            name = I18n.s("default.partition.name")
            letter = ""
            type = "Partition"
            sizeTB = 0.5
            usedTB = 0.0
            tags = ""
            encrypted = false
            cloudBackup = false
        }
        val id = DiskRepository.insertPartition(p)
        p.id = id
        onDataChanged()
    }

    private fun onDeleteSelected() {
        val v = getSelected() ?: return
        val what = when (v) {
            is Disk -> I18n.s("alert.delete.askDisk", v.name)
            is Partition -> I18n.s("alert.delete.askPartition", v.name)
            else -> I18n.s("alert.delete.askGeneric")
        }
        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = I18n.s("alert.delete.title")
        alert.headerText = null
        alert.contentText = what

        // Replace default buttons with translated ones
        val confirmButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        alert.buttonTypes.setAll(confirmButtonType, cancelButtonType)

        val res = DialogHelper.showDialog(alert, ThemeManager.currentTheme == Theme.DARK)
        if (res.isPresent && res.get() == confirmButtonType) {
            when (v) {
                is Disk -> DiskRepository.deleteDisk(v.id)
                is Partition -> DiskRepository.deletePartition(v.id)
            }
            onDataChanged()
        }
    }

    private fun expandAll() {
        val r = tree.root ?: return
        traverse(r) { it.isExpanded = true }
    }

    private fun collapseAll() {
        val r = tree.root ?: return
        r.children.forEach { traverse(it) { node -> node.isExpanded = false } }
    }

    private fun traverse(item: TreeItem<*>, action: (TreeItem<*>) -> Unit) {
        action(item)
        item.children.forEach { traverse(it, action) }
    }

    private fun applyColumnVisibility() {
        val visibilityMap = mapOf(
            "name" to Settings.Table.showName,
            "type" to Settings.Table.showType,
            "letter" to Settings.Table.showLetter,
            "size" to Settings.Table.showSize,
            "used" to Settings.Table.showUsed,
            "free" to Settings.Table.showFree,
            "percentText" to Settings.Table.showPercentText,
            "partOfDiskBar" to Settings.Table.showPartOfDiskBar,
            "bar" to Settings.Table.showBar,
            "tag" to Settings.Table.showTag,
            "model" to Settings.Table.showModel,
            "manufacturer" to Settings.Table.showManufacturer,
            "serial" to Settings.Table.showSerial,
            "uuid" to Settings.Table.showUuid,
            "fsType" to Settings.Table.showFsType,
            "encrypted" to Settings.Table.showEncrypted,
            "cloud" to Settings.Table.showCloud,
            "virtual" to Settings.Table.showVirtual,
            "hidden" to Settings.Table.showHiddenCol
        )

        val columnMap = mapOf(
            "name" to nameCol,
            "type" to typeCol,
            "letter" to letterCol,
            "size" to sizeCol,
            "used" to usedCol,
            "free" to freeCol,
            "percentText" to percentTextCol,
            "partOfDiskBar" to partOfDiskBarCol,
            "bar" to barCol,
            "tag" to tagCol,
            "model" to modelCol,
            "manufacturer" to manufacturerCol,
            "serial" to serialCol,
            "uuid" to uuidCol,
            "fsType" to fsTypeCol,
            "encrypted" to encCol,
            "cloud" to cloudCol,
            "virtual" to virtualCol,
            "hidden" to hiddenCol
        )

        // Get the saved column order from settings
        val columnOrder = ColumnVisibilityDialog.getColumnOrder()

        // Build list of visible columns in the saved order
        val visibleColumns = columnOrder
            .mapNotNull { id ->
                val col = columnMap[id]
                if (col != null && (visibilityMap[id] ?: true)) col else null
            }

        tree.columns.setAll(visibleColumns)
    }

    private fun setupContextMenu() {
        // Configure delete menu item
        deleteMenuItem.text = I18n.s("menu.context.delete")
        deleteMenuItem.setOnAction { onDeleteSelected() }

        // Configure move menu item
        moveMenuItem.text = I18n.s("menu.context.move")
        moveMenuItem.setOnAction { onMovePartition() }

        // Configure rename menu item
        renameMenuItem.text = I18n.s("menu.context.rename")
        renameMenuItem.setOnAction { onRenameSelected() }

        tree.setOnContextMenuRequested { event ->
            val selectedItem = tree.selectionModel.selectedItem ?: return@setOnContextMenuRequested
            val value = selectedItem.value
            contextMenu.items.setAll(buildContextMenuItems(value))
            contextMenu.show(tree, event.screenX, event.screenY)
        }

        tree.setOnMousePressed { event ->
            if (event.isPrimaryButtonDown) {
                contextMenu.hide()
            }
        }
    }

    private fun buildContextMenuItems(value: Any): List<MenuItem> {
        return when (value) {
            is Partition -> ContextMenuFactory.createPartitionMenu(
                value,
                ContextMenuFactory.PartitionCallbacks(
                    onDelete = { onDeleteSelected() },
                    onRename = { showRenamePartitionDialog(value) },
                    onMove = { onMovePartition() },
                    onToggleEncrypted = { newValue ->
                        value.encrypted = newValue
                        DiskRepository.updatePartition(value)
                        onDataChanged()
                    },
                    onToggleCloud = { newValue ->
                        value.cloudBackup = newValue
                        DiskRepository.updatePartition(value)
                        onDataChanged()
                    },
                    onToggleVirtual = { newValue ->
                        value.virtual = newValue
                        DiskRepository.updatePartition(value)
                        onDataChanged()
                    },
                    onToggleHidden = { newValue ->
                        value.hidden = newValue
                        DiskRepository.updatePartition(value)
                        onDataChanged()
                    }
                )
            ).items.toList()

            is Disk -> ContextMenuFactory.createDiskMenu(
                value,
                ContextMenuFactory.DiskCallbacks(
                    onDelete = { onDeleteSelected() },
                    onRename = { showRenameDiskDialog(value) },
                    onToggleHidden = { hidden ->
                        value.hidden = hidden
                        DiskRepository.updateDisk(value)
                        onDataChanged()
                    }
                )
            ).items.toList()

            else -> listOf(renameMenuItem, deleteMenuItem)
        }
    }

    internal fun onMovePartition() {
        val selectedItem = tree.selectionModel.selectedItem ?: return
        val partition = selectedItem.value as? Partition ?: return

        // Get list of all disks
        val allDisks = currentDisks

        if (allDisks.isEmpty()) {
            Alert(Alert.AlertType.WARNING, I18n.s("alert.add.partition.selectDisk")).showAndWait()
            return
        }

        // Create dialog
        val dialog = Dialog<Disk>()
        dialog.title = I18n.s("alert.move.partition.title")
        dialog.headerText = I18n.s("alert.move.partition.label")

        // Create disk selection combo box
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
                // Extract disk name from "DiskName (partition1, partition2)" format
                val diskName = string?.substringBefore(" (")?.trim() ?: return null
                return allDisks.find { it.name == diskName }
            }
        }

        // Pre-select current disk or first disk
        val currentDisk = allDisks.find { it.id == partition.diskId }
        diskCombo.value = currentDisk ?: allDisks.firstOrNull()

        // Layout
        val content = VBox(10.0)
        content.padding = Insets(10.0)
        content.children.add(diskCombo)
        dialog.dialogPane.content = content

        // Buttons
        val okButtonType = ButtonType(I18n.s("btn.ok"), ButtonBar.ButtonData.OK_DONE)
        val cancelButtonType = ButtonType(I18n.s("btn.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(okButtonType, cancelButtonType)

        // Result converter
        dialog.setResultConverter { buttonType ->
            if (buttonType == okButtonType) diskCombo.value else null
        }

        // Show dialog and process result
        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val targetDisk = result.get()
            if (targetDisk.id != partition.diskId) {
                try {
                    // Store names for success message
                    val partitionName = partition.name
                    val targetDiskName = targetDisk.name

                    // Update partition's disk ID in database
                    partition.diskId = targetDisk.id
                    DiskRepository.updatePartition(partition)

                    logger.info { "Moved partition $partitionName to disk $targetDiskName" }

                    // Refresh data FIRST to reload from database
                    onDataChanged()

                    // Show success message AFTER data is reloaded
                    val successAlert = Alert(Alert.AlertType.INFORMATION)
                    successAlert.title = I18n.s("alert.move.partition.title")
                    successAlert.headerText = null
                    successAlert.contentText = I18n.s("alert.move.partition.success", partitionName, targetDiskName)
                    DialogHelper.showDialog(successAlert, ThemeManager.currentTheme == Theme.DARK)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to move partition to disk $targetDisk" }
                    val errorAlert = Alert(Alert.AlertType.ERROR)
                    errorAlert.title = I18n.s("alert.move.partition.title")
                    errorAlert.headerText = null
                    errorAlert.contentText = I18n.s("alert.move.partition.error", e.message ?: "Unknown error")
                    DialogHelper.showDialog(errorAlert, ThemeManager.currentTheme == Theme.DARK)
                }
            }
        }
    }

    private fun onRenameSelected() {
        when (val selected = getSelected()) {
            is Disk -> showRenameDiskDialog(selected)
            is Partition -> showRenamePartitionDialog(selected)
        }
    }

    private fun showRenameDiskDialog(disk: Disk) {
        val dialog = TextInputDialog(disk.name)
        dialog.title = I18n.s("dialog.rename.disk.title")
        dialog.headerText = null
        dialog.contentText = I18n.s("col.name")
        dialog.editor.textFormatter = TabTableNameFormatter.create()

        val okButton = dialog.dialogPane.lookupButton(ButtonType.OK)
        okButton.disableProperty().bind(dialog.editor.textProperty().isEmpty)

        val result = DialogHelper.showDialog(dialog, ThemeManager.currentTheme == Theme.DARK)
        if (result.isPresent) {
            val newName = result.get().trim()
            if (newName.isNotEmpty() && newName != disk.name) {
                disk.name = newName
                DiskRepository.updateDisk(disk)
                onDataChanged()
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
                onDataChanged()
            }
        }
    }
}
