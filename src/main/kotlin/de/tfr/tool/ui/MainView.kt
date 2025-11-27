package de.tfr.tool.ui

import de.tfr.tool.de.tfr.tool.ui.i18n.I18n
import de.tfr.tool.de.tfr.tool.ui.i18n.Language
import de.tfr.tool.de.tfr.tool.ui.theme.ThemeHelper
import de.tfr.tool.export.CsvExporter
import de.tfr.tool.export.PngExporter
import de.tfr.tool.model.Disk
import de.tfr.tool.model.SortDirection
import de.tfr.tool.persist.Database
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.persist.Settings
import de.tfr.tool.ui.settings.AppSettings
import de.tfr.tool.ui.settings.SettingsDialog
import de.tfr.tool.ui.util.DialogHelper
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.layout.*
import javafx.stage.Stage
import mu.KotlinLogging
import java.nio.file.Paths

class MainView(private val primaryStage: Stage) : BorderPane() {
    private val logger = KotlinLogging.logger {}

    private val disks = SimpleObjectProperty<MutableList<Disk>>(mutableListOf())
    private val equalCardHeightsProp = SimpleBooleanProperty(false)
    private val fixedCardHeightEnabledProp = SimpleBooleanProperty(false)
    private val fixedCardHeightPxProp = SimpleDoubleProperty(220.0)
    private val themeProp = SimpleObjectProperty<Theme>(Theme.LIGHT)
    private val languageProp = SimpleObjectProperty<Language>(Language.DE)
    private val showHiddenProp = SimpleBooleanProperty(false)
    // Guard to allow mutual checkbox updates without infinite loops
    private var exclusiveToggleGuard = false

    private val groupBox = ComboBox<String>()

    private val tabTable = TabTable(showHiddenProp) { reloadFromDb() }
    private val tabCards = TabCards { reloadFromDb() }
    private val tabPartitions = TabPartitions { reloadFromDb() }
    private val tabStatistics = TabStatistics()
    private lateinit var tabs: TabPane

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
        applyTheme()
        // React to theme changes
        ThemeManager.addListener { t -> applyTheme(t) }
        // React to language changes
        I18n.addListener { applyTranslations() }
        reloadFromDb()

        // Forward card height settings to CardsView and PartitionsView
        tabCards.setEqualHeightEnabled(equalCardHeightsProp.get())
        tabCards.setFixedHeightEnabled(fixedCardHeightEnabledProp.get())
        tabCards.setFixedHeightPx(fixedCardHeightPxProp.get())
        tabPartitions.setEqualHeightEnabled(equalCardHeightsProp.get())
        tabPartitions.setFixedHeightEnabled(fixedCardHeightEnabledProp.get())
        tabPartitions.setFixedHeightPx(fixedCardHeightPxProp.get())

        equalCardHeightsProp.addListener { _, _, new ->
            tabCards.setEqualHeightEnabled(new)
            tabPartitions.setEqualHeightEnabled(new)
        }
        fixedCardHeightEnabledProp.addListener { _, _, new ->
            tabCards.setFixedHeightEnabled(new)
            tabPartitions.setFixedHeightEnabled(new)
        }
        fixedCardHeightPxProp.addListener { _, _, new ->
            tabCards.setFixedHeightPx(new.toDouble())
            tabPartitions.setFixedHeightPx(new.toDouble())
        }
        // Persist showHidden changes regardless of where they originate (TableView toggle)
        showHiddenProp.addListener { _, _, _ -> savePreferences() }

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
        applyTranslations()
    }

    private lateinit var toolbar: HBox
    private fun buildToolbar(): Node {
        toolbar = HBox(12.0)
        toolbar.id = "toolbar"
        toolbar.alignment = Pos.CENTER_LEFT
        toolbar.padding = Insets(6.0)
        // Style is set via applyTheme()

        val sortLbl = Label(I18n.s("toolbar.sort"))
        val sortMenuBtn = MenuButton()
        buildSortingMenu(sortMenuBtn)

        val groupLbl = Label(I18n.s("toolbar.group"))
        groupBox.items.setAll(I18n.s("toolbar.group.none"), I18n.s("toolbar.group.tag"))
        groupBox.selectionModel.selectFirst()
        groupBox.setOnAction { applySortingAndGrouping() }

        val refresh = Button(I18n.s("toolbar.refresh"))
        // Reload from database to reflect external changes (including DB clear)
        refresh.setOnAction { reloadFromDb() }

        val readInfo = Button(I18n.s("toolbar.readInfo"))
        readInfo.id = "reloadInfoButton"
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
                } catch (e: Exception) {
                    logger.error(e) { "Failed to import disk data" }
                    Platform.runLater {
                        Alert(AlertType.ERROR, I18n.s("alert.import.error", e.message ?: "")).showAndWait()
                    }
                } finally {
                    Platform.runLater { toolbar.isDisable = false }
                }
            }
            t.isDaemon = true
            t.start()
        }

        toolbar.children.addAll(
            sortLbl, sortMenuBtn,
            Separator(),
            groupLbl, groupBox,
            Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
            refresh,
            readInfo
        )
        return toolbar
    }

    private fun buildSortingMenu(sortMenuBtn: MenuButton) {
        val currentSort = de.tfr.tool.model.SortConfiguration(
            Settings.Table.sortField,
            Settings.Table.sortDirection
        )
        updateSortMenuButton(sortMenuBtn, currentSort)

        // RadioMenuItem items for sort direction
        val directionGroup = ToggleGroup()

        val ascMenuItem = RadioMenuItem(I18n.s("sort.ascending")).apply {
            toggleGroup = directionGroup
            isSelected = currentSort.direction == SortDirection.ASCENDING
            setOnAction {
                Settings.Table.sortDirection = SortDirection.ASCENDING
                val updatedSort = de.tfr.tool.model.SortConfiguration(
                    Settings.Table.sortField,
                    Settings.Table.sortDirection
                )
                updateSortMenuButton(sortMenuBtn, updatedSort)
                applySortingAndGrouping()
            }
        }

        val descMenuItem = RadioMenuItem(I18n.s("sort.descending")).apply {
            toggleGroup = directionGroup
            isSelected = currentSort.direction == SortDirection.DESCENDING
            setOnAction {
                Settings.Table.sortDirection = SortDirection.DESCENDING
                val updatedSort = de.tfr.tool.model.SortConfiguration(
                    Settings.Table.sortField,
                    Settings.Table.sortDirection
                )
                updateSortMenuButton(sortMenuBtn, updatedSort)
                applySortingAndGrouping()
            }
        }

        // Sort field menu items with selection indicator
        val fieldItems = listOf(
            "name" to I18n.s("sort.name"),
            "type" to I18n.s("sort.type"),
            "size" to I18n.s("sort.size"),
            "used" to I18n.s("sort.used"),
            "free" to I18n.s("sort.free"),
            "letter" to I18n.s("sort.letter"),
            "tags" to I18n.s("sort.tags")
        )

        val fieldMenuItems = fieldItems.map { (fieldName, label) ->
            MenuItem(label).apply {
                // Add visual indicator if this field is currently selected
                if (fieldName == currentSort.fieldName) {
                    style = "-fx-padding: 5px; -fx-background-color: rgba(74, 163, 255, 0.2);"
                }
                setOnAction {
                    Settings.Table.sortField = fieldName
                    val updatedSort = de.tfr.tool.model.SortConfiguration(
                        Settings.Table.sortField,
                        Settings.Table.sortDirection
                    )
                    updateSortMenuButton(sortMenuBtn, updatedSort)
                    // Rebuild the menu to update visual indicators
                    buildSortingMenu(sortMenuBtn)
                    applySortingAndGrouping()
                }
            }
        }

        sortMenuBtn.items.clear()
        // First group: Sort direction
        sortMenuBtn.items.addAll(ascMenuItem, descMenuItem)
        // Separator between groups
        if (fieldMenuItems.isNotEmpty()) {
            sortMenuBtn.items.add(SeparatorMenuItem())
            // Second group: Sort fields
            sortMenuBtn.items.addAll(fieldMenuItems)
        }
    }

    private fun updateSortMenuButton(sortMenuBtn: MenuButton, currentSort: de.tfr.tool.model.SortConfiguration) {
        val directionIcon = if (currentSort.direction == SortDirection.ASCENDING) "↑" else "↓"
        val fieldLabel = when (currentSort.fieldName) {
            "name" -> I18n.s("sort.name")
            "type" -> I18n.s("sort.type")
            "size" -> I18n.s("sort.size")
            "used" -> I18n.s("sort.used")
            "free" -> I18n.s("sort.free")
            "letter" -> I18n.s("sort.letter")
            "tags" -> I18n.s("sort.tags")
            else -> currentSort.fieldName
        }
        sortMenuBtn.text = "⧉ $fieldLabel $directionIcon"
    }

    private fun buildTabs(): Node {
        tabs = TabPane()
        tabs.id = "tabsPane"
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        val tabCards = Tab(I18n.s("tab.cards"))
        tabCards.id = "tabCards"
        tabCards.content = this@MainView.tabCards

        val tabPartitions = Tab(I18n.s("tab.partitions"))
        tabPartitions.id = "tabPartitions"
        tabPartitions.content = this@MainView.tabPartitions

        val tabTable = Tab(I18n.s("tab.table"))
        tabTable.id = "tabTable"
        tabTable.content = this@MainView.tabTable

        val tabStats = Tab(I18n.s("tab.stats"))
        tabStats.id = "tabStats"
        tabStats.content = tabStatistics

        tabs.tabs.addAll(tabCards, tabPartitions, tabTable, tabStats)
        return tabs
    }

    // Statistics moved to StatisticsView

    private fun buildMenuBar(): MenuBar {
        val menuBar = MenuBar()
        menuBar.id = "menuBar"

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
        einstellungen.id = "settingsMenu"
        val openSettings = MenuItem(I18n.s("menu.settings.open"))
        openSettings.id = "openSettingsMenuItem"
        openSettings.setOnAction { showSettingsDialog() }
        einstellungen.items.add(openSettings)

        // Help menu with a System Info dialog
        val help = Menu(I18n.s("menu.help"))
        val systemInfo = MenuItem(I18n.s("menu.help.systemInfo"))
        systemInfo.setOnAction { showSystemInfoDialog() }
        help.items.add(systemInfo)

        menuBar.menus.addAll(datei, einstellungen, help)
        return menuBar
    }

    private fun applySortingAndGrouping() {
        // Preserve the current expansion state of the disks
        val expandedDiskIds = tabTable.currentExpandedDiskIds()

        val list = disks.get()
        // Apply the sort order hierarchically: first Disks, then Partitions
        applySortingToDisks(list)

        // Rebuild cards via CardsView
        tabCards.updateData(list, grouped = groupBox.selectionModel.selectedIndex == 1)

        // Rebuild partitions via PartitionsView
        tabPartitions.updateData(list, grouped = groupBox.selectionModel.selectedIndex == 1)

        // Rebuild table data via TableView (Sortierung erfolgt dort intern)
        tabTable.updateData(list)
        tabTable.setExpandedDiskIds(expandedDiskIds)

        // Update statistics view
        tabStatistics.updateData(list)
    }

    /**
     * Sorts the disks and their partitions hierarchically, according to the settings.
     * Applies the sorting to the disks first, then to the partitions of each disk.
     */
    private fun applySortingToDisks(disks: List<Disk>) {
        if (disks.isEmpty()) return

        val currentSort = de.tfr.tool.model.SortConfiguration(
            Settings.Table.sortField,
            Settings.Table.sortDirection
        )
        val sortComparator = SorterFactory.getSortComparator(currentSort.fieldName, currentSort.direction)

        if (disks is MutableList) {
            disks.sortWith(sortComparator)
        }

        disks.forEach { disk ->
            disk.partitions.sortWith(sortComparator)
        }
    }

    // -- Settings -----------------------------------------------------------------------------

    private fun loadPreferences() {
        equalCardHeightsProp.set(Settings.equalCardHeights)
        fixedCardHeightEnabledProp.set(Settings.fixedCardHeightEnabled)
        fixedCardHeightPxProp.set(Settings.fixedCardHeightPx)
        val theme = Settings.theme
        themeProp.set(theme)
        ThemeManager.setTheme(theme)
        val lang = Settings.language
        languageProp.set(lang)
        I18n.setLanguage(lang)
        showHiddenProp.set(Settings.Table.showHidden)
        // Apply DB path if present
        val dbPath = Settings.dbPath
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
        Settings.equalCardHeights = equalCardHeightsProp.get()
        Settings.fixedCardHeightEnabled = fixedCardHeightEnabledProp.get()
        Settings.fixedCardHeightPx = fixedCardHeightPxProp.get()
        Settings.theme = themeProp.get()
        Settings.language = I18n.currentLanguage
        Settings.dbPath = Database.getCurrentDbPathAsString()
        Settings.Table.showHidden = showHiddenProp.get()
    }

    private fun showSettingsDialog() {
        val current = AppSettings(
            equalCardHeights = equalCardHeightsProp.get(),
            fixedCardHeightEnabled = fixedCardHeightEnabledProp.get(),
            fixedCardHeightPx = fixedCardHeightPxProp.get(),
            theme = ThemeManager.currentTheme,
            language = I18n.currentLanguage,
            displayUnit = Settings.displayUnit,
            dbPath = try { Database.getCurrentDbPath().toString() } catch (_: Exception) { null },
            showHidden = showHiddenProp.get()
        )

        // Save the current tab index BEFORE opening the settings dialog
        val currentTabIndex = tabs.selectionModel.selectedIndex

        val result = SettingsDialog.show(current)
        if (!result.ok) return

        val setting = result.settings
        // Apply settings
        equalCardHeightsProp.set(setting.equalCardHeights)
        fixedCardHeightEnabledProp.set(setting.fixedCardHeightEnabled)
        fixedCardHeightPxProp.set(setting.fixedCardHeightPx)
        showHiddenProp.set(setting.showHidden)

        // Apply displayUnit setting
        Settings.displayUnit = setting.displayUnit

        // Theme & Language
        themeProp.set(setting.theme)
        ThemeManager.setTheme(setting.theme)
        I18n.setLanguage(setting.language)
        applyTranslations()

        // Switch database path if needed
        if (result.dbPathChanged) {
            try {
                val newPath = setting.dbPath?.let { Paths.get(it) }
                // Detect whether the target DB file existed before switching
                val existedBefore = try { newPath?.let { java.nio.file.Files.exists(it) } ?: false } catch (_: Exception) { false }
                Database.setDatabaseFile(newPath)
                Database.initSchema()
                // Only seed when a brand new DB file is created
                if (!existedBefore) {
                    DiskRepository.seedIfEmpty()
                }
                reloadFromDb()
                DialogHelper.showDialog(
                    Alert(AlertType.INFORMATION, I18n.s("alert.db.switched", Database.getCurrentDbPath().toString())),
                    ThemeManager.currentTheme == Theme.DARK
                )
            } catch (ex: Exception) {
                DialogHelper.showDialog(
                    Alert(AlertType.ERROR, I18n.s("alert.export.error", ex.message ?: "")),
                    ThemeManager.currentTheme == Theme.DARK
                )
            }
        } else if (result.dbCleared) {
            // If DB was cleared from within the settings dialog, reload the UI to reflect empty state
            reloadFromDb()
        } else {
            // Apply changes
            applySortingAndGrouping()
            tabCards.applyEqualHeights()
            tabPartitions.applyEqualHeights()
            if (result.columnVisibilityChanged) {
                tabTable.reloadColumnVisibility()
            }
        }

        // Restore the tab index ONCE at the very end, after all UI updates
        tabs.selectionModel.select(currentTabIndex)

        savePreferences()
    }

    // Card height handling moved to CardsView

    // -- Theme ---------------------------------------------------------------------------------
    fun applyTheme(theme: Theme = themeProp.get()) {
        // AtlantaFX manages the theme globally via Application.setUserAgentStylesheet
        // We just need to apply window-specific settings
        ThemeHelper.setDarkTitleBar(primaryStage, theme)

        // Update CardsView and PartitionsView
        tabCards.applyTheme(theme)
        tabPartitions.applyTheme(theme)

        // Restyle table cells (bars) by refreshing
        tabTable.refresh()
    }

    // -- Translations -------------------------------------------------------------------------
    private fun applyTranslations() {
        // Rebuild top (menu + toolbar)
        top = VBox().apply {
            children += buildMenuBar()
            children += buildToolbar()
        }
        // Rebuild tabs (uses existing components)
        center = buildTabs()

        // Update translations in components
        tabTable.applyTranslations()
        tabCards.applyTranslations()
        tabPartitions.applyTranslations()
        tabStatistics.applyTranslations()

        // Reapply theme so the toolbar style is preserved
        applyTheme(themeProp.get())

        // Rebuild contents according to the language if needed
        // This updates all data with the new language
        applySortingAndGrouping()
    }

    // -- Export ---------------------------------------------------------------------------------

    private fun exportCardsAsPng() {
        // Determine the content node of the cards view (first child, not the scroll viewport)
        val contentNode = tabCards.getSnapshotContent() ?: run {
            DialogHelper.showDialog(
                Alert(AlertType.INFORMATION, I18n.s("alert.info.noCards")),
                ThemeManager.currentTheme == Theme.DARK
            )
            return
        }
        PngExporter.exportCardsAsPng(contentNode, scene?.window)
    }

    private fun exportTableAsCsv() {
        CsvExporter.exportTableAsCsv(
            table = tabTable.tree,
            disks = disks.get(),
            showHidden = showHiddenProp.get(),
            owner = scene?.window
        )
    }

    private fun reloadFromDb() {
        disks.set(DiskRepository.loadAll())
        applySortingAndGrouping()
    }

    /**
     * Show a simple system information dialog that lists key environment details.
     */
    private fun showSystemInfoDialog() {
        SystemInfoDialog().showDialog()
    }
}
