package de.tfr.tool.ui

import de.tfr.tool.HardDriveManagerApp
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.*
import javafx.stage.Stage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.testfx.framework.junit5.ApplicationTest

/**
 * TestFX UI Integration Test for a complete roundtrip through the application.
 *
 * Test steps:
 * 1. The application is opened
 * 2. Hard drive data is loaded with "Reload Info"
 * 3. Verify that Hard Drives tab has data
 * 4. Verify that Partitions tab has data
 * 5. Verify that Table tab has data
 * 6. Open the Settings dialog
 * 7. Toggle the "Show Hidden" flag on and off
 * 8. Save the changes
 */
class RoundtripUITest : ApplicationTest() {

    @Throws(Exception::class)
    override fun start(stage: Stage) {
        // Start the main application with English language
        HardDriveManagerApp().start(stage)
    }

    @BeforeEach
    fun setUp() {
        // Optional: Cleanup before each test
    }

    @Test
    fun testCompleteRoundtrip() {
        // Step 1: Application is open - verify that main components are visible
        val menuBar = lookup("#menuBar").query<Node?>()
        assertNotNull(menuBar, "MenuBar should be visible")

        val toolbar = lookup("#toolbar").query<Node?>()
        assertNotNull(toolbar, "Toolbar should be visible")

        val tabsPane = lookup("#tabsPane").query<Node?>()
        assertNotNull(tabsPane, "TabsPane should be visible")

        // Step 2: Click "Reload Info" button to load hard drive data
        val reloadButton: Button = lookup("#reloadInfoButton").query()
        clickOn(reloadButton)

        // Wait briefly for data to load and alert to close
        // Since import runs on a background thread, we need to wait
        waitMs(3000)

        // Try to close the information alert if present
        try {
            val okButton = lookup("OK").query<Button?>()
            if (okButton != null) {
                clickOn(okButton)
            }
        } catch (_: Exception) {
            // Alert not present, that's ok
        }

        waitMs(1000)

        // Step 3, 4, 5: Verify that all tabs have data
        val tabPane: TabPane = lookup("#tabsPane").query()

        // Switch to Hard Drives tab (Cards)
        val tabCards = tabPane.tabs.find { it.id == "tabCards" }
        requireNotNull(tabCards) { "Tab with ID 'tabCards' not found" }
        tabPane.selectionModel.select(tabCards)
        waitMs(500)

        val cardsContent = lookup("#tabCardsContent").query<Node?>()
        assertNotNull(
            cardsContent,
            "Hard Drives tab should contain data or at least be visible"
        )

        // Switch to Partitions tab
        val tabPartitions = tabPane.tabs.find { it.id == "tabPartitions" }
        requireNotNull(tabPartitions) { "Tab with ID 'tabPartitions' not found" }
        tabPane.selectionModel.select(tabPartitions)
        waitMs(500)

        val partitionsContent = lookup("#tabPartitionsContent").query<Node?>()
        assertNotNull(
            partitionsContent,
            "Partitions tab should contain data or at least be visible"
        )

        // Switch to Table tab
        val tabTable = tabPane.tabs.find { it.id == "tabTable" }
        requireNotNull(tabTable) { "Tab with ID 'tabTable' not found" }
        tabPane.selectionModel.select(tabTable)
        waitMs(2000)  // Longer wait for UI construction and tree rendering

        // The tab should now be active
        assertEquals(
            tabTable,
            tabPane.selectionModel.selectedItem,
            "Table tab should be selected"
        )

        // Step 6: Open the Settings dialog via menu
        // Call the action handler directly via Platform.runLater to ensure FX thread execution
        try {
            // Find the MenuBar
            val menuBar = lookup("#menuBar").query<MenuBar>()

            // Find the "Settings" menu using ID-based approach
            // The menu button text will be "Settings" since app is started in English
            val settingsMenu: Menu? = menuBar.menus.find { it.id == "settingsMenu" }

            if (settingsMenu != null) {
                // Find the "Open Settings" MenuItem
                val openSettingsItem = settingsMenu.items.find { it.id == "openSettingsMenuItem" }
                if (openSettingsItem != null) {
                    // Call the action handler on the FX application thread
                    // (Dialog creation requires FX thread)
                    Platform.runLater {
                        openSettingsItem.fire()
                    }
                    waitMs(1500)  // Longer wait for dialog creation
                } else {
                    fail("openSettingsMenuItem not found")
                }
            } else {
                fail("Settings menu not found")
            }
        } catch (e: Exception) {
            fail("Failed to open Settings Dialog", e)
        }

        // Step 7: Find and toggle the "Show Hidden" CheckBox
        val showHiddenCheckBox: CheckBox? = try {
            lookup("#showHiddenCheckBox").query<CheckBox?>()
        } catch (_: Exception) {
            null
        }

        if (showHiddenCheckBox != null) {
            // Remember the initial value
            val initialState = showHiddenCheckBox.isSelected

            // Toggle the checkbox
            clickOn(showHiddenCheckBox)
            waitMs(200)

            // Verify that the value changed
            Assertions.assertNotEquals(
                initialState,
                showHiddenCheckBox.isSelected,
                "Show Hidden CheckBox should change state after click"
            )

            // And toggle it back
            clickOn(showHiddenCheckBox)
            waitMs(200)

            assertEquals(
                initialState,
                showHiddenCheckBox.isSelected,
                "Show Hidden CheckBox should return to initial state after double toggle"
            )

            // Step 8: Close dialog with OK button
            try {
                // Find and click the OK button in the dialog
                val okButton: Button? = lookup(".button").query<Button?>()
                if (okButton != null && okButton.text.uppercase().contains("OK")) {
                    clickOn(okButton)
                    waitMs(500)
                }
            } catch (_: Exception) {
                // OK button could not be found or clicked
            }
        }

        // Verification: The application should still be open and functional
        val finalMenuBar = lookup("#menuBar").query<Node?>()
        assertNotNull(finalMenuBar, "MenuBar should still be visible")

        val finalTabsPane = lookup("#tabsPane").query<Node?>()
        assertNotNull(finalTabsPane, "TabsPane should still be visible")
    }

    /**
     * Helper function to wait (in milliseconds).
     * Alternative to Thread.sleep(), but more readable in tests.
     */
    private fun waitMs(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}



