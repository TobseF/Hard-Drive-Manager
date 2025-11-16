package de.tfr.tool.ui

import de.tfr.tool.model.Disk
import de.tfr.tool.model.PartitionType
import de.tfr.tool.model.percentOf
import de.tfr.tool.model.toTBString
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Rectangle
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView

class DiskCard(val disk: Disk) : StackPane() {
    private val outer = VBox(8.0)
    // Inner card as a field so we can control its height precisely
    private val card = VBox(12.0)
    // Header/footer references
    private lateinit var titleLabel: Label
    private lateinit var sizeLabelTop: Label
    private lateinit var footerBig: Label
    private lateinit var footerModel: Label
    // Drive usage bar (overall disk usage at the very bottom)
    private lateinit var driveBarBg: Rectangle
    private lateinit var driveBarFill: Rectangle
    // Partition UI references so we can apply the theme live
    private data class PartViews(
        val partBox: VBox,
        val nameLabel: Label,
        val sizeLabel: Label,
        val barBg: Rectangle,
        val barFill: Rectangle
    )
    private val parts = mutableListOf<PartViews>()

    init {
        padding = Insets(8.0)
        children += outer
        maxWidth = 260.0
        prefWidth = 260.0
        style = "-fx-background-color: transparent;"

        build()
    }

    private fun build() {
        card.padding = Insets(14.0)
        card.alignment = Pos.TOP_LEFT
        card.background = Background(BackgroundFill(Color.web("#d9d9d9"), CornerRadii(10.0), Insets.EMPTY))
        card.border = Border(BorderStroke(Color.web("#a5a5a5"), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(2.0)))
        // Card may grow to fill available height (can be toggled dynamically later)
        VBox.setVgrow(card, Priority.ALWAYS)

        val headRow = HBox(8.0)
        headRow.alignment = Pos.CENTER_LEFT

        titleLabel = Label(disk.name)
        titleLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold;"

        sizeLabelTop = Label(disk.sizeTB.toTBString())
        sizeLabelTop.style = "-fx-font-size: 14px; -fx-text-fill: #444;"
        HBox.setHgrow(Region(), Priority.ALWAYS)

        val pin1 = Circle(4.0, Color.web("#7a7a7a"))
        val pin2 = Circle(4.0, Color.web("#7a7a7a"))
        val pinRow = HBox(6.0, pin1, Region(), pin2)
        pinRow.alignment = Pos.CENTER
        pinRow.prefHeight = 6.0

        val headerBox = VBox(6.0,
            HBox(12.0, titleLabel, Region().apply { HBox.setHgrow(this, Priority.ALWAYS) }, sizeLabelTop),
            pinRow)

        card.children += headerBox

        // Partitions area
        val partsBox = VBox(6.0)
        // Show only non-hidden partitions in the card view
        disk.partitions.filter { !it.hidden }.forEach { p ->
            // Outer container per partition (acts like a "box")
            val partStack = StackPane()
            val partBox = VBox(6.0)
            partBox.alignment = Pos.CENTER_LEFT
            partBox.background = Background(BackgroundFill(Color.rgb(255, 250, 229), CornerRadii(6.0), Insets.EMPTY))
            partBox.border = Border(BorderStroke(Color.rgb(234, 210, 140), BorderStrokeStyle.SOLID, CornerRadii(6.0), BorderWidths(1.0)))
            partBox.padding = Insets(8.0, 10.0, 8.0, 10.0)

            // Top row: name (left) and size (right), optional lock icon near size
            val headerRow = HBox(8.0)
            headerRow.alignment = Pos.CENTER_LEFT

            // Name on the left – composed as "{Letter}: {Name}"
            val name = Label(partitionDisplayName(p.letter, p.name))
            name.style = "-fx-font-size: 13px; -fx-text-fill: #333333;"

            val size = Label(p.sizeTB.toTBString())
            size.style = "-fx-font-size: 13px; -fx-text-fill: #444;"

            val lockEmoji = Label("🔒")
            lockEmoji.isVisible = false // replaced by PNG below when available

            headerRow.children += listOf(
                name,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                size,
                lockEmoji
            )

            // Bottom row: usage bar (free/used) across the width
            // IMPORTANT: Do NOT bind the rectangles directly to partBox.widthProperty().
            // This can cause a layout feedback loop where the child width influences
            // the parent's preferred width which then resizes the child again (seen as
            // continuously growing card after "Reload").
            // Instead, let VBox size an intermediate container (bar) and bind the
            // rectangles to that container's width. Also mark rectangles as unmanaged so
            // they don't contribute to their parent's preferred size.
            val barBg = Rectangle(0.0, 6.0, Color.web("#ffd08a"))
            val barFill = Rectangle(0.0, 6.0, Color.web("#f59e42"))
            barBg.height = 6.0
            barFill.height = 6.0

            val bar = StackPane()
            bar.alignment = Pos.CENTER_LEFT
            bar.maxWidth = Double.MAX_VALUE // allow bar to stretch to full width of partBox

            // Unmanaged so their preferred size does not affect the parent's preferred size
            barBg.isManaged = false
            barFill.isManaged = false
            // Bind to actual laid-out width of the bar (which VBox controls)
            barBg.widthProperty().bind(bar.widthProperty().subtract(20))
            barFill.widthProperty().bind(barBg.widthProperty().multiply(p.usedTB.percentOf(p.sizeTB)))

            bar.children.addAll(barBg, barFill)

            partBox.children += listOf(headerRow, bar)

            // Overlay icons
            val overlay = Pane()
            overlay.isPickOnBounds = false

            // Cloud backup top-right (small blue cloud) – now as PNG
            val cloudUrl = javaClass.getResource("/cloud-backup.png")?.toExternalForm()
            val cloudIcon = if (cloudUrl != null) svgIcon(cloudUrl, 16.0, 16.0) else null
            cloudIcon?.isVisible = p.cloudBackup
            cloudIcon?.let {
                StackPane.setAlignment(it, Pos.TOP_RIGHT)
                StackPane.setMargin(it, Insets(2.0, 2.0, 0.0, 0.0))
            }

            // Lock bottom-right (small, yellow) – now as PNG
            val lockUrl = javaClass.getResource("/encrypted.png")?.toExternalForm()
            val lockIcon = if (lockUrl != null) svgIcon(lockUrl, 14.0, 14.0) else null
            // Derive visibility from the "encrypted" flag, fallback: type EncryptedContainer
            val isEncrypted = try { p.encrypted } catch (_: Exception) { p.type == PartitionType.EncryptedContainer.name }
            lockIcon?.isVisible = isEncrypted
            lockIcon?.let {
                StackPane.setAlignment(it, Pos.BOTTOM_RIGHT)
                StackPane.setMargin(it, Insets(0.0, 2.0, 2.0, 0.0))
            }

            partStack.children += partBox
            if (cloudIcon != null) partStack.children += cloudIcon
            if (lockIcon != null) partStack.children += lockIcon

            // Visibility updates when properties change
            try { p.cloudBackupProp.addListener { _, _, new -> cloudIcon?.isVisible = new } } catch (_: Exception) {}
            try { p.encryptedProp.addListener { _, _, new -> lockIcon?.isVisible = new } } catch (_: Exception) {}

            partsBox.children += partStack
            parts += PartViews(partBox, name, size, barBg, barFill)
        }

        card.children += partsBox

        // Flexible spacer so the footer moves to the bottom for tall cards
        val spacer = Region()
        VBox.setVgrow(spacer, Priority.ALWAYS)

        val footer = VBox(2.0)
        footerBig = Label("${disk.sizeTB.toTBString()} ${disk.type}")
        footerBig.style = "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #4a4a4a;"
        footerModel = Label(disk.model)
        footerModel.style = "-fx-text-fill: #555;"
        footer.children += listOf(footerBig, footerModel)

        card.children += spacer
        card.children += footer

        // --- Drive usage bar (overall) ------------------------------------------------------
        // Horizontal bar showing used vs size for the whole drive, similar to partitions.
        // We use an intermediate container (StackPane) whose width is controlled by the VBox.
        // The rectangles are marked unmanaged and bound to that container's width to avoid
        // layout feedback loops where children would influence parent's preferred width.
        val driveBar = StackPane()
        driveBar.alignment = Pos.CENTER_LEFT
        driveBar.maxWidth = Double.MAX_VALUE

        driveBarBg = Rectangle(0.0, 6.0, Color.web("#ffd08a"))
        driveBarFill = Rectangle(0.0, 6.0, Color.web("#f59e42"))
        driveBarBg.height = 6.0
        driveBarFill.height = 6.0
        // Unmanaged so their preferred size does not affect the parent's preferred size
        driveBarBg.isManaged = false
        driveBarFill.isManaged = false
        // Bind widths to the actual laid-out width of the bar
        driveBarBg.widthProperty().bind(driveBar.widthProperty().subtract(20))
        driveBarFill.widthProperty().bind(
            driveBarBg.widthProperty().multiply(disk.usedTB.percentOf(disk.sizeTB))
        )

        driveBar.children.addAll(driveBarBg, driveBarFill)
        VBox.setMargin(driveBar, Insets(8.0, 0.0, 0.0, 0.0))

        // Place the drive bar at the very bottom of the card (below footer)
        card.children += driveBar

        outer.children += card
    }

    /**
     * Returns the display name for a partition in the format "{Letter}: {Name}".
     * If the stored name already starts with "{Letter}:", that prefix is removed
     * to avoid showing it twice.
     */
    private fun partitionDisplayName(letter: String?, rawName: String?): String {
        val l = (letter ?: "").trim()
        val base = (rawName ?: "").trim()
        if (l.isEmpty()) return base
        val pattern = Regex("^" + Regex.escape(l) + ":\\s*", RegexOption.IGNORE_CASE)
        val cleaned = base.replace(pattern, "")
        return if (cleaned.isEmpty()) "$l:" else "$l: $cleaned"
    }

    fun applyTheme(theme: Theme) {
        if (theme == Theme.DARK) {
            // Card
            card.background = Background(BackgroundFill(Color.web("#3c3f41"), CornerRadii(10.0), Insets.EMPTY))
            card.border = Border(BorderStroke(Color.web("#55595c"), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(2.0)))
            // Header/texts
            titleLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6;"
            sizeLabelTop.style = "-fx-font-size: 14px; -fx-text-fill: #c8c8c8;"
            footerBig.style = "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #f0f0f0;"
            footerModel.style = "-fx-text-fill: #c0c0c0;"
            // Partitions
            parts.forEach {
                it.partBox.background = Background(BackgroundFill(Color.web("#4b4f51"), CornerRadii(6.0), Insets.EMPTY))
                it.partBox.border = Border(BorderStroke(Color.web("#6a6e70"), BorderStrokeStyle.SOLID, CornerRadii(6.0), BorderWidths(1.0)))
                it.nameLabel.style = "-fx-font-size: 13px; -fx-text-fill: #e8e8e8;"
                it.sizeLabel.style = "-fx-font-size: 13px; -fx-text-fill: #d0d0d0;"
                it.barBg.fill = Color.web("#5a5e60")
                it.barFill.fill = Color.web("#4aa3ff")
            }
            // Drive bar colors (dark theme)
            if (this::driveBarBg.isInitialized) {
                driveBarBg.fill = Color.web("#5a5e60")
                driveBarFill.fill = Color.web("#4aa3ff")
            }
            // Slightly darken the root background
            this.style = "-fx-background-color: transparent;"
        } else {
            // Light theme (existing colors)
            card.background = Background(BackgroundFill(Color.web("#d9d9d9"), CornerRadii(10.0), Insets.EMPTY))
            card.border = Border(BorderStroke(Color.web("#a5a5a5"), BorderStrokeStyle.SOLID, CornerRadii(10.0), BorderWidths(2.0)))
            titleLabel.style = "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #000000;"
            sizeLabelTop.style = "-fx-font-size: 14px; -fx-text-fill: #444;"
            footerBig.style = "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #4a4a4a;"
            footerModel.style = "-fx-text-fill: #555;"
            parts.forEach {
                it.partBox.background = Background(BackgroundFill(Color.rgb(255, 250, 229), CornerRadii(6.0), Insets.EMPTY))
                it.partBox.border = Border(BorderStroke(Color.rgb(234, 210, 140), BorderStrokeStyle.SOLID, CornerRadii(6.0), BorderWidths(1.0)))
                it.nameLabel.style = "-fx-font-size: 13px; -fx-text-fill: #333333;"
                it.sizeLabel.style = "-fx-font-size: 13px; -fx-text-fill: #444;"
                it.barBg.fill = Color.web("#ffd08a")
                it.barFill.fill = Color.web("#f59e42")
            }
            // Drive bar colors (light theme)
            if (this::driveBarBg.isInitialized) {
                driveBarBg.fill = Color.web("#ffd08a")
                driveBarFill.fill = Color.web("#f59e42")
            }
            this.style = "-fx-background-color: transparent;"
        }
    }

    /**
     * Apply a fixed height (in pixels) to the card and its inner containers,
     * so the visual card actually becomes taller (not just the outer wrapper).
     */
    fun applyFixedHeight(heightPx: Double) {
        val h = heightPx.coerceAtLeast(50.0)
        // StackPane (root)
        minHeight = h
        prefHeight = h
        maxHeight = h
        // Outer VBox
        outer.minHeight = h
        outer.prefHeight = h
        outer.maxHeight = h
        // Inner card
        card.minHeight = h
        card.prefHeight = h
        card.maxHeight = h
    }

    /**
     * Reset all explicit height constraints back to automatic computation.
     */
    fun resetHeightConstraints() {
        // Root
        minHeight = Region.USE_COMPUTED_SIZE
        prefHeight = Region.USE_COMPUTED_SIZE
        maxHeight = Region.USE_COMPUTED_SIZE
        // Outer
        outer.minHeight = Region.USE_COMPUTED_SIZE
        outer.prefHeight = Region.USE_COMPUTED_SIZE
        outer.maxHeight = Region.USE_COMPUTED_SIZE
        // Card
        card.minHeight = Region.USE_COMPUTED_SIZE
        card.prefHeight = Region.USE_COMPUTED_SIZE
        card.maxHeight = Region.USE_COMPUTED_SIZE
    }

    /**
     * Controls whether the inner card may fill the available free space.
     * When Equal/Fixed Height is disabled this should be false,
     * so the card keeps its natural height.
     */
    fun setCardGrowEnabled(enabled: Boolean) {
        VBox.setVgrow(card, if (enabled) Priority.ALWAYS else Priority.NEVER)
    }

    private fun svgIcon(url: String, w: Double, h: Double): Node {
        // Loads a raster graphic (PNG) as Image and scales it.
        val image = try {
            Image(url, true)
        } catch (ex: Exception) {
            // Fallback via classpath stream
            val stream = try { javaClass.getResourceAsStream(url) } catch (_: Exception) { null }
            if (stream != null) Image(stream) else return Region().apply { prefWidth = 0.0; prefHeight = 0.0 }
        }

        return ImageView(image).apply {
            isPreserveRatio = true
            fitWidth = w
            fitHeight = h
            isMouseTransparent = true
            // Wrapper not necessary; StackPane alignment works on Node directly
        }
    }
}
