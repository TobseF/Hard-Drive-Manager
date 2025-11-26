package de.tfr.tool.ui.util

import javafx.scene.control.TextFormatter

/** Provides a reusable formatter for disk/partition name inputs. */
object TabTableNameFormatter {
    private val allowedChars = Regex("\\p{Print}{0,100}")

    fun create(): TextFormatter<String> = TextFormatter { change ->
        val newText = change.controlNewText
        if (newText.length <= 100 && allowedChars.matches(newText)) change else null
    }
}
