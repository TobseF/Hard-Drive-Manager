package de.tfr.tool.ui

import de.tfr.tool.export.CsvExporter
import de.tfr.tool.export.PngExporter
import de.tfr.tool.model.Disk
import de.tfr.tool.persist.Database
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.settings.AppSettings
import de.tfr.tool.ui.settings.SettingsDialog
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
import net.yetihafen.javafx.customcaption.CustomCaption
import java.nio.file.Paths
import java.util.prefs.Preferences

class MainView(private val primaryStage: Stage) : BorderPane() {
    private val disks = SimpleObjectProperty<MutableList<Disk>>(mutableListOf())
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

    private val tabCards = TabCards()
    private val tabPartitions = TabPartitions()
    private val tabTable = TabTable(showHiddenProp) { applySortingAndGrouping() }
    private val tabStatistics = TabStatistics()

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
        tabCards.content = this@MainView.tabCards

        val tabPartitions = Tab(I18n.s("tab.partitions"))
        tabPartitions.content = this@MainView.tabPartitions

        val tabTable = Tab(I18n.s("tab.table"))
        tabTable.content = this@MainView.tabTable

        val tabStats = Tab(I18n.s("tab.stats"))
        tabStats.content = tabStatistics

        tabs.tabs.addAll(tabCards, tabPartitions, tabTable, tabStats)
        return tabs
    }

    // Statistics moved to StatisticsView

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

        // Help menu with a System Info dialog
        val help = Menu(I18n.s("menu.help"))
        val systemInfo = MenuItem(I18n.s("menu.help.systemInfo"))
        systemInfo.setOnAction { showSystemInfoDialog() }
        help.items.add(systemInfo)

        menuBar.menus.addAll(datei, einstellungen, help)
        return menuBar
    }

    // Cards logic moved to CardsView

    private fun applySortingAndGrouping() {
        // Preserve the current expansion state of the disks
        val expandedDiskIds = tabTable.currentExpandedDiskIds()

        val list = disks.get()
        when (sortBox.selectionModel.selectedIndex) {
            1 -> list.sortByDescending { it.sizeTB }
            else -> list.sortBy { it.name.lowercase() }
        }

        // rebuild cards via CardsView
        tabCards.updateData(list, grouped = groupBox.selectionModel.selectedIndex == 1)

        // rebuild partitions via PartitionsView
        tabPartitions.updateData(list, grouped = groupBox.selectionModel.selectedIndex == 1)

        // rebuild table data via TableView
        tabTable.updateData(list)
        tabTable.setExpandedDiskIds(expandedDiskIds)

        // Update statistics view
        tabStatistics.updateData(list)
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

        val result = SettingsDialog.show(current)
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
                DialogHelper.showAlert(
                    Alert(AlertType.INFORMATION, I18n.s("alert.db.switched", Database.getCurrentDbPath().toString())),
                    ThemeManager.currentTheme == Theme.DARK
                )
            } catch (ex: Exception) {
                DialogHelper.showAlert(
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
        }

        savePreferences()
    }

    // Card height handling moved to CardsView

    // -- Theme ---------------------------------------------------------------------------------
    fun applyTheme(theme: Theme = themeProp.get()) {
        // AtlantaFX manages the theme globally via Application.setUserAgentStylesheet
        // We just need to apply window-specific settings
        CustomCaption.setImmersiveDarkMode(primaryStage, theme == Theme.DARK)

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
            DialogHelper.showAlert(
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
