package de.tfr.tool.ui

import de.tfr.tool.export.CsvExporter
import de.tfr.tool.export.PngExporter
import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.toTBString
import de.tfr.tool.model.percentOf
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.binding.Bindings
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.cell.TextFieldTreeTableCell
import javafx.scene.control.cell.CheckBoxTreeTableCell
import javafx.scene.layout.*
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.persist.Database
import de.tfr.tool.ui.settings.AppSettings
import javafx.beans.property.SimpleStringProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.Parent
import javafx.scene.chart.PieChart
import javafx.scene.control.Alert.AlertType
import javafx.stage.FileChooser
import java.io.File
import javax.imageio.ImageIO
import java.util.prefs.Preferences
import java.nio.file.Paths
import kotlin.math.ceil

class MainView : BorderPane() {
    private val disks = SimpleObjectProperty<MutableList<Disk>>(mutableListOf())
    private val editMode = SimpleBooleanProperty(false)
    private val equalCardHeightsProp = SimpleBooleanProperty(false)
    private val fixedCardHeightEnabledProp = SimpleBooleanProperty(false)
    private val fixedCardHeightPxProp = SimpleDoubleProperty(220.0)
    private val themeProp = SimpleObjectProperty<Theme>(Theme.LIGHT)
    private val languageProp = SimpleObjectProperty<Language>(Language.DE)
    private val showHiddenProp = SimpleBooleanProperty(false)
    // Guard to allow mutual checkbox updates without infinite loops
    private var exclusiveToggleGuard = false

    private val sortBox = ComboBox<String>()
    private val groupBox = ComboBox<String>()

    private val cardsContainer = StackPane()
    private val cardsScroll = ScrollPane(cardsContainer).apply {
        isFitToWidth = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        style = "-fx-background: transparent; -fx-background-color: transparent;"
    }
    private val table = buildTreeTable()

    // Statistics charts
    private val pieTotalFreeUsed = PieChart().apply { title = I18n.s("stats.total.title") }
    private val pieCapacityPerDisk = PieChart().apply { title = I18n.s("stats.capacityPerDisk.title") }
    private val pieUsedByTags = PieChart().apply { title = I18n.s("stats.usedByTags.title") }

    init {
        padding = Insets(10.0)
        // Load preferences
        loadPreferences()

        top = VBox().apply {
            children += buildMenuBar()
            children += buildToolbar()
        }
        center = buildTabs()
        // Apply theme initially (after UI was created)
        applyTheme(themeProp.get())
        // React to theme changes
        ThemeManager.addListener { t -> applyTheme(t) }
        // React to language changes
        I18n.addListener { applyTranslations() }
        reloadFromDb()

        // Changing the setting should re-apply the card height
        equalCardHeightsProp.addListener { _, _, _ -> applyEqualHeightsToCards() }
        fixedCardHeightEnabledProp.addListener { _, _, _ -> applyEqualHeightsToCards() }
        fixedCardHeightPxProp.addListener { _, _, _ -> applyEqualHeightsToCards() }

        // Mutual exclusion of the two options at the property level
        equalCardHeightsProp.addListener { _, _, new ->
            if (new == true && !exclusiveToggleGuard) {
                exclusiveToggleGuard = true
                try { fixedCardHeightEnabledProp.set(false) } finally { exclusiveToggleGuard = false }
            }
        }
        fixedCardHeightEnabledProp.addListener { _, _, new ->
            if (new == true && !exclusiveToggleGuard) {
                exclusiveToggleGuard = true
                try { equalCardHeightsProp.set(false) } finally { exclusiveToggleGuard = false }
            }
        }
    }

    private lateinit var toolbar: HBox
    private fun buildToolbar(): Node {
        toolbar = HBox(12.0)
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.padding = Insets(6.0)
        // Style is set via applyTheme()

        val sortLbl = Label(I18n.s("toolbar.sort"))
        sortBox.items.setAll(I18n.s("toolbar.sort.name"), I18n.s("toolbar.sort.size"))
        sortBox.selectionModel.selectFirst()
        sortBox.setOnAction { applySortingAndGrouping() }

        val groupLbl = Label(I18n.s("toolbar.group"))
        groupBox.items.setAll(I18n.s("toolbar.group.none"), I18n.s("toolbar.group.tag"))
        groupBox.selectionModel.selectFirst()
        groupBox.setOnAction { applySortingAndGrouping() }

        val refresh = Button(I18n.s("toolbar.refresh"))
        // Reload from database to reflect external changes (including DB clear)
        refresh.setOnAction { reloadFromDb() }

        val readInfo = Button(I18n.s("toolbar.readInfo"))
        readInfo.setOnAction {
            // Import on a background thread; disable the UI meanwhile
            toolbar.isDisable = true
            val t = Thread {
                try {
                    val res = de.tfr.tool.persist.OshiImporter.readAndMerge()
                    Platform.runLater {
                        reloadFromDb()
                        val msg = I18n.s(
                            "alert.import.result",
                            res.disksUpdated,
                            res.disksInserted,
                            res.partitionsUpdated,
                            res.partitionsInserted
                        )
                        Alert(AlertType.INFORMATION, msg).showAndWait()
                    }
                } catch (ex: Exception) {
                    Platform.runLater {
                        Alert(AlertType.ERROR, I18n.s("alert.import.error", ex.message ?: "")).showAndWait()
                    }
                } finally {
                    Platform.runLater { toolbar.isDisable = false }
                }
            }
            t.isDaemon = true
            t.start()
        }

        toolbar.children.addAll(
            sortLbl, sortBox,
            Separator(),
            groupLbl, groupBox,
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            refresh,
            readInfo
        )
        return toolbar
    }

    private fun buildTabs(): Node {
        val tabs = TabPane()
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        val tabCards = Tab(I18n.s("tab.cards"))
        tabCards.content = cardsScroll

        val tabTable = Tab(I18n.s("tab.table"))
        tabTable.content = VBox(8.0).apply {
            padding = Insets(8.0)
            val editBtn = ToggleButton(I18n.s("btn.edit"))
            editBtn.selectedProperty().bindBidirectional(editMode)
            editBtn.textProperty().bind(Bindings.`when`(editBtn.selectedProperty()).then(I18n.s("btn.done")).otherwise(I18n.s("btn.edit")))

            val addDiskBtn = Button(I18n.s("btn.add.disk"))
            addDiskBtn.setOnAction { onAddDisk() }

            val addPartBtn = Button(I18n.s("btn.add.partition"))
            addPartBtn.setOnAction { onAddPartition() }

            val delBtn = Button(I18n.s("btn.delete"))
            delBtn.setOnAction { onDeleteSelected() }

            val expandAllBtn = Button(I18n.s("btn.expand.all"))
            expandAllBtn.setOnAction { expandAll() }

            val collapseAllBtn = Button(I18n.s("btn.collapse.all"))
            collapseAllBtn.setOnAction { collapseAll() }

            // Toggle to show/hide hidden entries
            val toggleHidden = ToggleButton()
            toggleHidden.selectedProperty().bindBidirectional(showHiddenProp)
            toggleHidden.textProperty().bind(
                Bindings.`when`(toggleHidden.selectedProperty())
                    .then(I18n.s("btn.hideHidden"))
                    .otherwise(I18n.s("btn.showHidden"))
            )
            toggleHidden.setOnAction { applySortingAndGrouping(); savePreferences() }

            val row = HBox(8.0, editBtn, Separator(), addDiskBtn, addPartBtn, delBtn, Separator(), expandAllBtn, collapseAllBtn, Separator(), toggleHidden)
            row.alignment = Pos.CENTER_LEFT

            children += listOf(row, table)
            VBox.setVgrow(table, Priority.ALWAYS)
        }

        val tabStats = Tab(I18n.s("tab.stats"))
        tabStats.content = buildStatsContent()

        tabs.tabs.addAll(tabCards, tabTable, tabStats)
        return tabs
    }

    private fun buildStatsContent(): Node {
        val container = VBox(16.0).apply {
            padding = Insets(12.0)
        }

        // Uniform size/look & show legends
        listOf(pieTotalFreeUsed, pieCapacityPerDisk, pieUsedByTags).forEach { chart ->
            chart.labelsVisibleProperty().set(false)
            chart.legendVisibleProperty().set(true)
            chart.setPrefSize(600.0, 360.0)
            chart.minHeight = 300.0
        }

        container.children += pieTotalFreeUsed
        container.children += pieCapacityPerDisk
        container.children += pieUsedByTags

        val scroll = ScrollPane(container).apply {
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        }

        // Initial population
        rebuildStatistics()

        return scroll
    }

    private fun rebuildStatistics() {
        val items = disks.get()

        // 1) Total free vs. used
        val totalSize = items.sumOf { it.sizeTB.coerceAtLeast(0.0) }
        val totalUsed = items.sumOf { it.usedTB.coerceAtLeast(0.0) }
        val totalFree = (totalSize - totalUsed).coerceAtLeast(0.0)
        setPieData(
            pieTotalFreeUsed,
            listOf(
                I18n.s("col.used") to totalUsed,
                I18n.s("col.free") to totalFree
            )
        )

        // 2) Total capacity per disk
        val byDisk = items.map { it.name to it.sizeTB.coerceAtLeast(0.0) }
        setPieData(pieCapacityPerDisk, byDisk)

        // 3) Used by tags
        val tagMap = linkedMapOf<String, Double>()
        items.forEach { d ->
            d.partitions.forEach { p ->
                val raw = p.tags
                val tags = raw
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
            // Empty state: a 1.0 dummy with label "No data"
            listOf(PieChart.Data(I18n.s("stats.none"), 1.0))
        } else {
            filtered.map { (name, value) -> PieChart.Data(name, value) }
        }
        chart.data.setAll(data)

        // Tooltips with TB + percentage
        val denom = if (total > 0.0) total else data.sumOf { it.pieValue }
        chart.data.forEach { d ->
            val value = d.pieValue
            val pct = if (denom > 0.0) (value / denom) * 100.0 else 0.0
            val label = if (filtered.isEmpty()) I18n.s("stats.none") else I18n.s("stats.tooltip.value", d.name, String.format("%.1f", value), String.format("%.1f", pct))
            val tip = Tooltip(label)
            Tooltip.install(d.node, tip)
        }
    }

    private fun buildMenuBar(): MenuBar {
        val menuBar = MenuBar()

        val datei = Menu(I18n.s("menu.file"))
        val exportCards = MenuItem(I18n.s("menu.file.export.cards"))
        exportCards.setOnAction { exportCardsAsPng() }

        val exportTable = MenuItem(I18n.s("menu.file.export.table"))
        exportTable.setOnAction { exportTableAsCsv() }

        val sep = SeparatorMenuItem()

        val beenden = MenuItem(I18n.s("menu.file.exit"))
        beenden.setOnAction { Platform.exit() }

        datei.items.addAll(exportCards, exportTable, sep, beenden)

        val einstellungen = Menu(I18n.s("menu.settings"))
        val openSettings = MenuItem(I18n.s("menu.settings.open"))
        openSettings.setOnAction { showSettingsDialog() }
        einstellungen.items.add(openSettings)

        menuBar.menus.addAll(datei, einstellungen)
        return menuBar
    }

    private fun rebuildCardsView(grouped: Boolean) {
        val content: Node = if (!grouped) {
            val flow = FlowPane()
            flow.hgap = 16.0
            flow.vgap = 16.0
            flow.padding = Insets(12.0)
            disks.get().filter { !it.hidden }.forEach { 
                val c = DiskCard(it)
                c.applyTheme(ThemeManager.currentTheme)
                flow.children += c 
            }
            flow
        } else {
            // Arrange groups vertically; inside each group the disks are placed horizontally
            val byTag = disks.get().filter { !it.hidden }.groupBy { it.tag.ifBlank { I18n.s("stats.noTag") } }
            val root = VBox(16.0)
            root.padding = Insets(12.0)
            byTag.toSortedMap().forEach { (tag, list) ->
                val groupBox = VBox(10.0)
                val header = Label(tag)
                header.style = "-fx-font-size: 16px; -fx-font-weight: bold;"

                // Horizontal flow of the cards within the group (wraps automatically if needed)
                val row = FlowPane(16.0, 16.0).apply {
                    // ensure the container can use the available width
                    maxWidth = Double.MAX_VALUE
                }
                list.forEach { 
                    val c = DiskCard(it)
                    c.applyTheme(ThemeManager.currentTheme)
                    row.children += c 
                }

                groupBox.children += listOf(header, row)
                root.children += groupBox
            }
            root
        }
        cardsContainer.children.setAll(content)

        // After rebuilding, apply equal heights if needed
        applyEqualHeightsToCards()
    }

    private fun applySortingAndGrouping() {
        // Preserve the current expansion state of the disks
        val expandedDiskIds = currentExpandedDiskIds()

        val list = disks.get()
        when (sortBox.selectionModel.selectedIndex) {
            1 -> list.sortByDescending { it.sizeTB }
            else -> list.sortBy { it.name.lowercase() }
        }

        // rebuild cards
        rebuildCardsView(grouped = groupBox.selectionModel.selectedIndex == 1)

        // rebuild table data
        val newRoot = buildTreeRoot(list)
        // Restore the expansion state
        for (diskItem in newRoot.children) {
            val disk = (diskItem.value as? Disk)
            diskItem.isExpanded = disk != null && expandedDiskIds.contains(disk.id)
        }
        // Default: if no expansion state exists yet (e.g. on first open), expand everything
        if (expandedDiskIds.isEmpty()) {
            for (diskItem in newRoot.children) {
                diskItem.isExpanded = true
            }
        }
        table.root = newRoot
        table.root.isExpanded = true

        // Update statistics
        rebuildStatistics()
    }

    // -- Settings -----------------------------------------------------------------------------

    private fun loadPreferences() {
        val prefs = Preferences.userRoot().node("de/tfr/tool/harddrivemanager")
        val eq = prefs.getBoolean("equalCardHeights", false)
        equalCardHeightsProp.set(eq)
        val fixedEnabled = prefs.getBoolean("fixedCardHeightEnabled", false)
        fixedCardHeightEnabledProp.set(fixedEnabled)
        val fixedPx = prefs.getDouble("fixedCardHeightPx", 220.0)
        fixedCardHeightPxProp.set(fixedPx)
        val theme = Theme.fromString(prefs.get("theme", Theme.LIGHT.name))
        themeProp.set(theme)
        ThemeManager.setTheme(theme)
        val lang = Language.fromString(prefs.get("language", Language.DE.name))
        languageProp.set(lang)
        I18n.setLanguage(lang)
        showHiddenProp.set(prefs.getBoolean("table.showHidden", false))
        // Apply DB path if present
        val dbPath = prefs.get("db.path", "").trim()
        if (dbPath.isNotEmpty()) {
            try { Database.setDatabaseFile(Paths.get(dbPath)) } catch (_: Exception) {}
        }

        // If historically both flags were active: prioritize fixed height, disable equal height
        if (equalCardHeightsProp.get() && fixedCardHeightEnabledProp.get()) {
            exclusiveToggleGuard = true
            try {
                // Fixed height takes precedence
                equalCardHeightsProp.set(false)
            } finally {
                exclusiveToggleGuard = false
            }
        }
    }

    private fun savePreferences() {
        val prefs = Preferences.userRoot().node("de/tfr/tool/harddrivemanager")
        prefs.putBoolean("equalCardHeights", equalCardHeightsProp.get())
        prefs.putBoolean("fixedCardHeightEnabled", fixedCardHeightEnabledProp.get())
        prefs.putDouble("fixedCardHeightPx", fixedCardHeightPxProp.get())
        prefs.put("theme", themeProp.get().name)
        prefs.put("language", I18n.currentLanguage.name)
        prefs.put("db.path", try { Database.getCurrentDbPath().toString() } catch (e: Exception) { "" })
        prefs.putBoolean("table.showHidden", showHiddenProp.get())
    }

    private fun showSettingsDialog() {
        val current = AppSettings(
            equalCardHeights = equalCardHeightsProp.get(),
            fixedCardHeightEnabled = fixedCardHeightEnabledProp.get(),
            fixedCardHeightPx = fixedCardHeightPxProp.get(),
            theme = ThemeManager.currentTheme,
            language = I18n.currentLanguage,
            dbPath = try { Database.getCurrentDbPath().toString() } catch (_: Exception) { null },
            showHidden = showHiddenProp.get()
        )

        val result = de.tfr.tool.ui.settings.SettingsDialog.show(current)
        if (!result.ok) return

        val s = result.settings
        // Apply settings
        equalCardHeightsProp.set(s.equalCardHeights)
        fixedCardHeightEnabledProp.set(s.fixedCardHeightEnabled)
        fixedCardHeightPxProp.set(s.fixedCardHeightPx)
        showHiddenProp.set(s.showHidden)

        // Theme & Language
        themeProp.set(s.theme)
        ThemeManager.setTheme(s.theme)
        I18n.setLanguage(s.language)
        applyTranslations()

        // Switch database path if needed
        if (result.dbPathChanged) {
            try {
                val newPath = s.dbPath?.let { Paths.get(it) }
                // Detect whether the target DB file existed before switching
                val existedBefore = try { newPath?.let { java.nio.file.Files.exists(it) } ?: false } catch (_: Exception) { false }
                Database.setDatabaseFile(newPath)
                Database.initSchema()
                // Only seed when a brand new DB file is created
                if (!existedBefore) {
                    DiskRepository.seedIfEmpty()
                }
                reloadFromDb()
                Alert(AlertType.INFORMATION, I18n.s("alert.db.switched", Database.getCurrentDbPath().toString())).showAndWait()
            } catch (ex: Exception) {
                Alert(AlertType.ERROR, I18n.s("alert.export.error", ex.message ?: "")).showAndWait()
            }
        } else if (result.dbCleared) {
            // If DB was cleared from within the settings dialog, reload the UI to reflect empty state
            reloadFromDb()
        } else {
            // Apply changes
            applySortingAndGrouping()
            applyEqualHeightsToCards()
        }

        savePreferences()
    }


    private fun collectAllDiskCards(): List<DiskCard> {
        val result = mutableListOf<DiskCard>()
        val content = cardsContainer.children.firstOrNull() ?: return emptyList()

        fun collect(node: Node) {
            when (node) {
                is DiskCard -> result += node
                is Pane -> node.childrenUnmodifiable.forEach { collect(it) }
                else -> { /* ignore */ }
            }
        }
        collect(content)
        return result
    }

    private fun applyEqualHeightsToCards() {
        // Wait until layout has been performed so we can measure real heights
        Platform.runLater {
            val cards = collectAllDiskCards()
            if (cards.isEmpty()) return@runLater

            if (fixedCardHeightEnabledProp.get()) {
                val h = fixedCardHeightPxProp.get().coerceAtLeast(50.0)
                cards.forEach { 
                    it.applyFixedHeight(h)
                    it.setCardGrowEnabled(true)
                }
                return@runLater
            }

            // Reset any previous constraints to get correct measurements
            if (!equalCardHeightsProp.get()) {
                // Both options off: natural, individual height
                cards.forEach { 
                    it.resetHeightConstraints()
                    it.setCardGrowEnabled(false)
                }
                return@runLater
            }

            // Ensure CSS/layout
            cards.forEach { it.applyCss(); it.layout() }
            // Some cards may still report 0.0 height here – use prefHeight(-1) as fallback in that case
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

    // -- Theme ---------------------------------------------------------------------------------
    private fun applyTheme(theme: Theme) {
        // Toggle stylesheet
        val darkUrl = javaClass.getResource("/theme/dark.css")?.toExternalForm()
        val sheets = scene?.stylesheets
        if (darkUrl != null && sheets != null) {
            if (theme == Theme.DARK) {
                if (!sheets.contains(darkUrl)) sheets.add(darkUrl)
            } else {
                sheets.remove(darkUrl)
            }
        } else {
            // If the scene does not exist yet, apply later
            sceneProperty().addListener { _, _, _ -> applyTheme(theme) }
        }

        // Colorize toolbar
        if (this::toolbar.isInitialized) {
            toolbar.style = if (theme == Theme.DARK)
                "-fx-background-color: #2b2b2b; -fx-border-color: #3c3f41; -fx-border-width: 0 0 1 0; -fx-text-fill: #e0e0e0;"
            else
                "-fx-background-color: #f3f3f3; -fx-border-color: #d0d0d0; -fx-border-width: 0 0 1 0;"
        }

        // Update DiskCards
        collectAllDiskCards().forEach { it.applyTheme(theme) }

        // Restyle table cells (bars) by refreshing
        table.refresh()
    }

    // -- Translations -------------------------------------------------------------------------
    private fun applyTranslations() {
        // Rebuild top (menu + toolbar)
        top = VBox().apply {
            children += buildMenuBar()
            children += buildToolbar()
        }
        // Rebuild tabs (uses existing table field and charts)
        center = buildTabs()
        // Update chart titles
        pieTotalFreeUsed.title = I18n.s("stats.total.title")
        pieCapacityPerDisk.title = I18n.s("stats.capacityPerDisk.title")
        pieUsedByTags.title = I18n.s("stats.usedByTags.title")
        // Table: set column headers
        table.columns[0].text = I18n.s("col.name")
        table.columns[1].text = I18n.s("col.type")
        table.columns[2].text = I18n.s("col.size")
        table.columns[3].text = I18n.s("col.used")
        table.columns[4].text = I18n.s("col.free")
        table.columns[5].text = I18n.s("col.percentUsed")
        table.columns[6].text = I18n.s("col.sizeOfDiskBar")
        table.columns[7].text = I18n.s("col.usedBar")
        table.columns[8].text = I18n.s("col.tags")
        table.columns[9].text = I18n.s("col.letter")
        table.columns[10].text = I18n.s("col.model")
        table.columns[11].text = I18n.s("col.manufacturer")
        table.columns[12].text = I18n.s("col.serial")
        table.columns[13].text = I18n.s("col.uuid")
        table.columns[14].text = I18n.s("col.fsType")
        table.columns[15].text = I18n.s("col.encrypted")
        table.columns[16].text = I18n.s("col.cloud")
        if (table.columns.size > 17) {
            table.columns[17].text = I18n.s("col.hidden")
        }

        // Reapply theme so the toolbar style is preserved
        applyTheme(themeProp.get())
        // Rebuild contents according to the language if needed
        applySortingAndGrouping()
    }

    // -- Export ---------------------------------------------------------------------------------

    private fun exportCardsAsPng() {
        // Determine the content node of the cards view (first child, not the scroll viewport)
        val contentNode = cardsContainer.children.firstOrNull() ?: run {
            Alert(AlertType.INFORMATION, I18n.s("alert.info.noCards")).showAndWait()
            return
        }
        PngExporter.exportCardsAsPng(contentNode, scene?.window)
    }

    private fun exportTableAsCsv() {
        CsvExporter.exportTableAsCsv(
            table = table,
            disks = disks.get(),
            showHidden = showHiddenProp.get(),
            owner = scene?.window
        )
    }

    private fun buildTreeTable(): TreeTableView<Any> {
        val tree = TreeTableView<Any>()
        tree.isShowRoot = false
        tree.isEditable = true
        tree.editableProperty().bind(editMode)

        val nameCol = TreeTableColumn<Any, String>(I18n.s("col.name"))
        nameCol.prefWidth = 250.0
        nameCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.name)
                is Partition -> SimpleStringProperty(v.name)
                else -> SimpleStringProperty("")
            }
        }
        nameCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        nameCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            when (item) {
                is Disk -> {
                    item.name = ev.newValue
                    DiskRepository.updateDisk(item)
                }
                is Partition -> {
                    item.name = ev.newValue
                    DiskRepository.updatePartition(item)
                }
            }
            applySortingAndGrouping()
        }
        val letterCol = TreeTableColumn<Any, String>(I18n.s("col.letter"))
        letterCol.prefWidth = 80.0
        letterCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Partition -> SimpleStringProperty(v.letter)
                else -> SimpleStringProperty("")
            }
        }
        letterCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        letterCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Partition) {
                item.letter = ev.newValue
                DiskRepository.updatePartition(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        val typeCol = TreeTableColumn<Any, String>(I18n.s("col.type"))
        typeCol.prefWidth = 120.0
        typeCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.type)
                is Partition -> SimpleStringProperty(v.type)
                else -> SimpleStringProperty("")
            }
        }
        typeCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        typeCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            when (item) {
                is Disk -> { item.type = ev.newValue; DiskRepository.updateDisk(item) }
                is Partition -> { item.type = ev.newValue; DiskRepository.updatePartition(item) }
            }
            applySortingAndGrouping()
        }

        val sizeCol = TreeTableColumn<Any, String>(I18n.s("col.size"))
        sizeCol.prefWidth = 100.0
        sizeCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.sizeTB.toTBString())
                is Partition -> SimpleStringProperty(v.sizeTB.toTBString())
                else -> SimpleStringProperty("")
            }
        }
        sizeCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        sizeCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            val raw = ev.newValue.trim().removeSuffix("TB").trim().replace(',', '.')
            val newVal = raw.toDoubleOrNull()
            if (newVal != null) {
                when (item) {
                    is Disk -> { item.sizeTB = newVal; DiskRepository.updateDisk(item) }
                    is Partition -> { item.sizeTB = newVal; DiskRepository.updatePartition(item) }
                }
                applySortingAndGrouping()
            } else {
                // revert display by refreshing
                tree.refresh()
            }
        }

        val usedCol = TreeTableColumn<Any, String>(I18n.s("col.used"))
        usedCol.prefWidth = 100.0
        usedCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.usedTB.toTBString())
                is Partition -> SimpleStringProperty(v.usedTB.toTBString())
                else -> SimpleStringProperty("")
            }
        }
        usedCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        usedCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            val raw = ev.newValue.trim().removeSuffix("TB").trim().replace(',', '.')
            val newVal = raw.toDoubleOrNull()
            if (newVal != null) {
                when (item) {
                    is Disk -> { /* Disk.usedTB ist abgeleitet -> ignorieren */ }
                    is Partition -> { item.usedTB = newVal; DiskRepository.updatePartition(item) }
                }
                applySortingAndGrouping()
            } else {
                tree.refresh()
            }
        }

        // Neue berechnete Spalten -------------------------------------------------------------
        val freeCol = TreeTableColumn<Any, String>(I18n.s("col.free"))
        freeCol.prefWidth = 100.0
        freeCol.setCellValueFactory { data ->
            val v = data.value.value
            val free = when (v) {
                is Disk -> (v.sizeTB - v.usedTB).coerceAtLeast(0.0)
                is Partition -> (v.sizeTB - v.usedTB).coerceAtLeast(0.0)
                else -> 0.0
            }
            SimpleStringProperty(free.toTBString())
        }
        // Nur Anzeige, nicht editierbar

        val percentTextCol = TreeTableColumn<Any, String>(I18n.s("col.percentUsed"))
        percentTextCol.prefWidth = 90.0
        percentTextCol.setCellValueFactory { data ->
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

        // Balken: Belegt-Anteil (blau)
        val barCol = TreeTableColumn<Any, Double>(I18n.s("col.usedBar"))
        barCol.prefWidth = 140.0
        barCol.setCellValueFactory { data ->
            val v = data.value.value
            val pct = when (v) {
                is Disk -> v.usedTB.percentOf(v.sizeTB)
                is Partition -> v.usedTB.percentOf(v.sizeTB)
                else -> 0.0
            }
            SimpleObjectProperty(pct)
        }
        barCol.setCellFactory {
            object : TreeTableCell<Any, Double>() {
                private val bg = StackPane().apply {
                    prefHeight = 12.0
                    maxHeight = 12.0
                    minHeight = 12.0
                }
                private val bar = Region().apply {
                    prefHeight = 12.0
                    maxHeight = 12.0
                    minHeight = 12.0
                }
                private val container = StackPane().apply {
                    children.addAll(bg, bar)
                    padding = Insets(2.0, 6.0, 2.0, 6.0)
                    // Align bar left
                    StackPane.setAlignment(bar, Pos.CENTER_LEFT)
                }
                private val tip = Tooltip()
                private var pctValue: Double = 0.0
                private val themeListener: (Theme) -> Unit = { updateTheme(it) }

                init {
                    // React to cell/column size changes
                    container.widthProperty().addListener { _, _, _ -> updateBarWidth() }
                    // Set initial theme and react to further changes
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
                    // Remember value and apply immediately/on resize
                    pctValue = item.coerceIn(0.0, 1.0)
                    updateBarWidth()
                    tip.text = I18n.s("fmt.percentOne", String.format("%.1f", pctValue * 100))
                    tooltip = tip
                }
            }
        }

        // New bar column: partition size relative to its disk (yellow)
        val partOfDiskBarCol = TreeTableColumn<Any, Double>(I18n.s("col.sizeOfDiskBar"))
        partOfDiskBarCol.prefWidth = 140.0
        partOfDiskBarCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Partition -> {
                    // Determine parent disk
                    val parentDisk = data.value.parent?.value as? Disk
                    val total = parentDisk?.sizeTB ?: 0.0
                    SimpleObjectProperty(v.sizeTB.percentOf(total))
                }
                is Disk -> {
                    val totalAll = disks.get().sumOf { it.sizeTB.coerceAtLeast(0.0) }
                    SimpleObjectProperty(v.sizeTB.percentOf(totalAll))
                }
                else -> SimpleObjectProperty(0.0)
            }
        }
        partOfDiskBarCol.setCellFactory {
            object : TreeTableCell<Any, Double>() {
                private val bg = StackPane().apply {
                    prefHeight = 12.0
                    maxHeight = 12.0
                    minHeight = 12.0
                }
                private val bar = Region().apply {
                    prefHeight = 12.0
                    maxHeight = 12.0
                    minHeight = 12.0
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

                    // Tooltip mit konkreten TB-Werten (Partition vs. Disk vs. andere), wenn möglich
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
                            val totalAll = disks.get().sumOf { it.sizeTB.coerceAtLeast(0.0) }
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

        val tagCol = TreeTableColumn<Any, String>(I18n.s("col.tags"))
        tagCol.prefWidth = 160.0
        tagCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.tag)
                is Partition -> SimpleStringProperty(v.tags)
                else -> SimpleStringProperty("")
            }
        }
        tagCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        tagCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            when (item) {
                is Disk -> { item.tag = ev.newValue; DiskRepository.updateDisk(item) }
                is Partition -> { item.tags = ev.newValue; DiskRepository.updatePartition(item) }
            }
            applySortingAndGrouping()
        }

        val modelCol = TreeTableColumn<Any, String>(I18n.s("col.model"))
        modelCol.prefWidth = 180.0
        modelCol.setCellValueFactory { data ->
            val v = data.value.value
            when (v) {
                is Disk -> SimpleStringProperty(v.model)
                else -> SimpleStringProperty("")
            }
        }
        modelCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        modelCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Disk) {
                item.model = ev.newValue
                DiskRepository.updateDisk(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        val manufacturerCol = TreeTableColumn<Any, String>(I18n.s("col.manufacturer"))
        manufacturerCol.prefWidth = 150.0
        manufacturerCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Disk -> SimpleStringProperty(v.manufacturer)
                else -> SimpleStringProperty("")
            }
        }
        manufacturerCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        manufacturerCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Disk) {
                item.manufacturer = ev.newValue
                DiskRepository.updateDisk(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        val serialCol = TreeTableColumn<Any, String>(I18n.s("col.serial"))
        serialCol.prefWidth = 180.0
        serialCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Disk -> SimpleStringProperty(v.serial)
                else -> SimpleStringProperty("")
            }
        }
        serialCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        serialCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Disk) {
                item.serial = ev.newValue
                DiskRepository.updateDisk(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        // Boolean flag columns for partitions
        val encCol = TreeTableColumn<Any, Boolean>(I18n.s("col.encrypted"))
        encCol.prefWidth = 90.0
        encCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Partition -> {
                    val prop = v.encryptedProp
                    // Persist on change
                    prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) }
                    prop
                }
                else -> SimpleBooleanProperty(false)
            }
        }
        encCol.cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(encCol)
        encCol.setEditable(true)

        val uuidCol = TreeTableColumn<Any, String>(I18n.s("col.uuid"))
        uuidCol.prefWidth = 180.0
        uuidCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Partition -> SimpleStringProperty(v.uuid)
                else -> SimpleStringProperty("")
            }
        }
        uuidCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        uuidCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Partition) {
                item.uuid = ev.newValue
                DiskRepository.updatePartition(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        val fsTypeCol = TreeTableColumn<Any, String>(I18n.s("col.fsType"))
        fsTypeCol.prefWidth = 130.0
        fsTypeCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Partition -> SimpleStringProperty(v.fsType)
                else -> SimpleStringProperty("")
            }
        }
        fsTypeCol.cellFactory = TextFieldTreeTableCell.forTreeTableColumn()
        fsTypeCol.setOnEditCommit { ev ->
            val item = ev.rowValue.value
            if (item is Partition) {
                item.fsType = ev.newValue
                DiskRepository.updatePartition(item)
                applySortingAndGrouping()
            } else tree.refresh()
        }

        val cloudCol = TreeTableColumn<Any, Boolean>(I18n.s("col.cloud"))
        cloudCol.prefWidth = 110.0
        cloudCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Partition -> {
                    val prop = v.cloudBackupProp
                    prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) }
                    prop
                }
                else -> SimpleBooleanProperty(false)
            }
        }
        cloudCol.cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(cloudCol)
        cloudCol.setEditable(true)

        // New column: Hidden (for Disk and Partition)
        val hiddenCol = TreeTableColumn<Any, Boolean>(I18n.s("col.hidden"))
        hiddenCol.prefWidth = 110.0
        hiddenCol.setCellValueFactory { data ->
            when (val v = data.value.value) {
                is Disk -> {
                    val prop = v.hiddenProp
                    prop.addListener { _, _, _ -> DiskRepository.updateDisk(v) }
                    prop
                }
                is Partition -> {
                    val prop = v.hiddenProp
                    prop.addListener { _, _, _ -> DiskRepository.updatePartition(v) }
                    prop
                }
                else -> SimpleBooleanProperty(false)
            }
        }
        hiddenCol.cellFactory = CheckBoxTreeTableCell.forTreeTableColumn(hiddenCol)
        hiddenCol.setEditable(true)

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

    private fun buildTreeRoot(items: List<Disk>): TreeItem<Any> {
        val root = TreeItem<Any>("root")
        val showAll = showHiddenProp.get()
        items.forEach { d ->
            if (!showAll && d.hidden) return@forEach
            val diskItem = TreeItem<Any>(d)
            d.partitions.forEach { p -> if (showAll || !p.hidden) diskItem.children += TreeItem<Any>(p) }
            root.children += diskItem
        }
        return root
    }

    private fun reloadFromDb() {
        disks.set(DiskRepository.loadAll())
        applySortingAndGrouping()
    }

    // -- Helper: Expand/Collapse all -----------------------------------------------------------

    private fun expandAll() {
        val r = table.root ?: return
        traverse(r) { it.isExpanded = true }
    }

    private fun collapseAll() {
        val r = table.root ?: return
        // root selbst sichtbar nicht schließen (isShowRoot=false, aber intern behalten wir root offen)
        r.children.forEach { traverse(it) { node -> node.isExpanded = false } }
    }

    private fun traverse(item: TreeItem<*>, action: (TreeItem<*>) -> Unit) {
        action(item)
        item.children.forEach { traverse(it, action) }
    }

    private fun currentExpandedDiskIds(): Set<Long> {
        val result = mutableSetOf<Long>()
        val r = table.root ?: return emptySet()
        for (diskItem in r.children) {
            val d = diskItem.value as? Disk ?: continue
            if (diskItem.isExpanded) result += d.id
        }
        return result
    }

    private fun getSelected(): Any? = table.selectionModel.selectedItem?.value

    private fun selectedDiskOrParent(): Disk? {
        val sel = table.selectionModel.selectedItem
        return when (val v = sel?.value) {
            is Disk -> v
            is Partition -> {
                // parent of selected partition
                sel.parent?.value as? Disk
            }
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
        reloadFromDb()
    }

    private fun onAddPartition() {
        val disk = selectedDiskOrParent()
        if (disk == null) {
            Alert(AlertType.INFORMATION, I18n.s("alert.add.partition.selectDisk")).showAndWait()
            return
        }
        val p = de.tfr.tool.model.Partition().apply {
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
        reloadFromDb()
    }

    private fun onDeleteSelected() {
        val v = getSelected() ?: return
        val what = when (v) {
            is Disk -> I18n.s("alert.delete.askDisk", v.name)
            is Partition -> I18n.s("alert.delete.askPartition", v.name)
            else -> I18n.s("alert.delete.askGeneric")
        }
        val alert = Alert(AlertType.CONFIRMATION)
        alert.title = I18n.s("alert.delete.title")
        alert.headerText = null
        alert.contentText = what
        val res = alert.showAndWait()
        if (res.isPresent && res.get() == ButtonType.OK) {
            when (v) {
                is Disk -> DiskRepository.deleteDisk(v.id)
                is Partition -> DiskRepository.deletePartition(v.id)
            }
            reloadFromDb()
        }
    }
}
