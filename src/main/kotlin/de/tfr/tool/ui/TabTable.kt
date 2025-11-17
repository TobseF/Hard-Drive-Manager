package de.tfr.tool.ui

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.percentOf
import de.tfr.tool.model.toTBString
import de.tfr.tool.persist.DiskRepository
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTreeTableCell
import javafx.scene.control.cell.TextFieldTreeTableCell
import javafx.scene.layout.*

/**
 * Encapsulates all table UI and logic previously mixed into MainView.
 * Provides an updateData(list) to rebuild the tree and exposes [tree] for external utilities (CSV export, etc.).
 */
class TabTable(
    private val showHiddenProp: BooleanProperty = SimpleBooleanProperty(false),
    private val onDataChanged: () -> Unit = {}
) : VBox(8.0) {

    // Expose the TreeTableView for external usages (e.g., CSV export)
    val tree: TreeTableView<Any> = TreeTableView()

    // Toolbar controls need to update texts on language change
    private val editBtn = ToggleButton()
    private val addDiskBtn = Button()
    private val addPartBtn = Button()
    private val delBtn = Button()
    private val expandAllBtn = Button()
    private val collapseAllBtn = Button()
    private val toggleHidden = ToggleButton()

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
    private lateinit var hiddenCol: TreeTableColumn<Any, Boolean>

    // last provided dataset, needed for certain tooltips
    private var currentDisks: List<Disk> = emptyList()

    init {
        padding = Insets(8.0)
        children += buildToolbar()
        VBox.setVgrow(buildTable(), Priority.ALWAYS)
        children += tree
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

        expandAllBtn.text = I18n.s("btn.expand.all")
        expandAllBtn.setOnAction { expandAll() }

        collapseAllBtn.text = I18n.s("btn.collapse.all")
        collapseAllBtn.setOnAction { collapseAll() }

        toggleHidden.selectedProperty().bindBidirectional(showHiddenProp)
        toggleHidden.text = if (showHiddenProp.get()) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")
        toggleHidden.selectedProperty().addListener { _, _, selected ->
            toggleHidden.text = if (selected) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")
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
            toggleHidden
        )
        return row
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
                    is Disk -> SimpleStringProperty(v.sizeTB.toTBString())
                    is Partition -> SimpleStringProperty(v.sizeTB.toTBString())
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
                    is Disk -> SimpleStringProperty(v.usedTB.toTBString())
                    is Partition -> SimpleStringProperty(v.usedTB.toTBString())
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
                val v = data.value.value
                val free = when (v) {
                    is Disk -> (v.sizeTB - v.usedTB).coerceAtLeast(0.0)
                    is Partition -> (v.sizeTB - v.usedTB).coerceAtLeast(0.0)
                    else -> 0.0
                }
                SimpleStringProperty(free.toTBString())
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
                val v = data.value.value
                val pct = when (v) {
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

                        val rowObj = tableRow?.item
                        when (rowObj) {
                            is Partition -> {
                                val disk = tableRow.treeItem?.parent?.value as? Disk
                                val total = disk?.sizeTB ?: 0.0
                                val tipTxt = if (total > 0.0) {
                                    val part = rowObj.sizeTB.coerceAtLeast(0.0)
                                    val pct = (part / total) * 100.0
                                    I18n.s("fmt.partOfDisk", rowObj.sizeTB.toTBString(), total.toTBString(), String.format("%.1f", pct))
                                } else {
                                    I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                                }
                                tip.text = tipTxt
                                tooltip = tip
                            }
                            is Disk -> {
                                val totalAll = currentDisks.sumOf { it.sizeTB.coerceAtLeast(0.0) }
                                val tipTxt = if (totalAll > 0.0) {
                                    val part = rowObj.sizeTB.coerceAtLeast(0.0)
                                    val pct = (part / totalAll) * 100.0
                                    I18n.s("fmt.partOfDisk", rowObj.sizeTB.toTBString(), totalAll.toTBString(), String.format("%.1f", pct))
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
            hiddenCol
        )
        return tree
    }

    fun refresh() = tree.refresh()

    fun applyTranslations() {
        // Buttons
        editBtn.text = if (editBtn.isSelected) I18n.s("btn.done") else I18n.s("btn.edit")
        addDiskBtn.text = I18n.s("btn.add.disk")
        addPartBtn.text = I18n.s("btn.add.partition")
        delBtn.text = I18n.s("btn.delete")
        expandAllBtn.text = I18n.s("btn.expand.all")
        collapseAllBtn.text = I18n.s("btn.collapse.all")
        toggleHidden.text = if (toggleHidden.isSelected) I18n.s("btn.hideHidden") else I18n.s("btn.showHidden")

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
        hiddenCol.text = I18n.s("col.hidden")
    }

    fun updateData(disks: List<Disk>) {
        this.currentDisks = disks
        val root = TreeItem<Any>("root")
        val showAll = showHiddenProp.get()
        disks.forEach { d ->
            if (!showAll && d.hidden) return@forEach
            val diskItem = TreeItem<Any>(d)
            d.partitions.forEach { p -> if (showAll || !p.hidden) diskItem.children += TreeItem<Any>(p) }
            root.children += diskItem
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
        val res = DialogHelper.showAlert(alert, ThemeManager.currentTheme == Theme.DARK)
        if (res.isPresent && res.get() == ButtonType.OK) {
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
}
