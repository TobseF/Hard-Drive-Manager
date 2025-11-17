package de.tfr.tool

import de.tfr.tool.de.tfr.tool.persist.Settings
import de.tfr.tool.persist.Database
import de.tfr.tool.persist.DiskRepository
import de.tfr.tool.ui.I18n
import de.tfr.tool.ui.MainView
import javafx.application.Application
import javafx.scene.Scene
import javafx.stage.Stage
import java.nio.file.Files
import java.nio.file.Paths

class HardDriveManagerApp : Application() {
    override fun start(primaryStage: Stage) {
        // Before initialization: load custom DB path (if exists)
        run {
            val path = Settings.dbPath
            if (path.isNotEmpty()) {
                try {
                    Database.setDatabaseFile(Paths.get(path))
                } catch (_: Exception) {
                }
            }
        }
        // Initialize DB schema and load seed data only when creating DB for the first time
        val dbExistedBefore = try { Files.exists(Database.getCurrentDbPath()) } catch (_: Exception) { false }
        Database.initSchema()
        if (!dbExistedBefore) {
            DiskRepository.seedIfEmpty()
        }
        val root = MainView(primaryStage)
        val scene = Scene(root, 1280.0, 800.0)
        primaryStage.title = I18n.s("app.title")
        I18n.addListener { primaryStage.title = I18n.s("app.title") }
        primaryStage.scene = scene
        primaryStage.show()
        root.applyTheme()
    }
}

fun main(args: Array<String>) {
    Application.launch(HardDriveManagerApp::class.java, *args)
}
