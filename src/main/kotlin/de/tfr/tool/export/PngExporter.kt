package de.tfr.tool.export

import de.tfr.tool.ui.DialogHelper
import de.tfr.tool.ui.I18n
import de.tfr.tool.ui.Theme
import de.tfr.tool.ui.ThemeManager
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.control.Alert
import javafx.scene.image.WritableImage
import javafx.stage.FileChooser
import javafx.stage.Window
import java.io.File
import javax.imageio.ImageIO

/**
 * PNG-file-export for the table view.
 */
object PngExporter {
    fun exportCardsAsPng(node: Node, owner: Window?) {
        if (node.boundsInParent.width <= 0.0 || node.boundsInParent.height <= 0.0) {
            DialogHelper.showAlert(
                Alert(Alert.AlertType.INFORMATION, I18n.s("alert.info.noCards")),
                ThemeManager.currentTheme == Theme.DARK
            )
            return
        }

        val chooser = FileChooser()
        chooser.title = I18n.s("alert.export.cards.title")
        chooser.extensionFilters.add(FileChooser.ExtensionFilter(I18n.s("file.filter.png"), "*.png"))
        chooser.initialFileName = I18n.s("file.name.cards")
        val file: File = chooser.showSaveDialog(owner) ?: return

        try {
            val params = SnapshotParameters()
            val image = node.snapshot(params, null as WritableImage?)
            val buffered = SwingFXUtils.fromFXImage(image, null)
            ImageIO.write(buffered, "png", file)
            DialogHelper.showAlert(
                Alert(Alert.AlertType.INFORMATION, I18n.s("alert.export.success", file.absolutePath)),
                ThemeManager.currentTheme == Theme.DARK
            )
        } catch (ex: Exception) {
            DialogHelper.showAlert(
                Alert(Alert.AlertType.ERROR, I18n.s("alert.export.error", ex.message ?: "")),
                ThemeManager.currentTheme == Theme.DARK
            )
        }
    }
}
