package de.tfr.tool.ui.tag

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox

/**
 * Shared helpers for tag display and formatting across UI components.
 */
object TagChipFactory {

    private const val CHIP_STYLE =
        "-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 4 8 4 8; -fx-border-radius: 12; -fx-background-radius: 12;"
    private const val LABEL_STYLE = "-fx-text-fill: white; -fx-font-size: 11px;"
    private const val CLOSE_STYLE =
        "-fx-padding: 0; -fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;"

    fun parseTags(tags: String?): MutableSet<String> {
        if (tags.isNullOrBlank()) {
            return mutableSetOf()
        }
        return tags.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
    }

    fun formatTags(tags: Set<String>): String {
        return tags.filter { it.isNotBlank() }
            .joinToString(", ")
    }

    fun createTagChip(tag: String, removable: Boolean = true, onRemove: ((HBox) -> Unit)? = null): HBox {
        val chip = HBox(4.0)
        chip.alignment = Pos.CENTER
        chip.style = CHIP_STYLE

        val label = Label(tag)
        label.style = LABEL_STYLE
        chip.children += label

        if (removable) {
            val closeBtn = Button("✕")
            closeBtn.style = CLOSE_STYLE
            closeBtn.isFocusTraversable = false
            closeBtn.setOnAction { onRemove?.invoke(chip) }
            chip.children += closeBtn
        }

        return chip
    }
}

