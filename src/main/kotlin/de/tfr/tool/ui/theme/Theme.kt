package de.tfr.tool.ui

import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight
import javafx.scene.paint.Color
import javafx.scene.paint.Color.web
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon

enum class Theme(
    val atlantaFxTheme: atlantafx.base.theme.Theme,
    val fontColor: Color
) {
    LIGHT(PrimerLight(), fontColor = web("#333333")),
    DARK(PrimerDark(), fontColor = web("#E0E0E0"));

    companion object {
        fun fromString(name: String?): Theme = when (name?.uppercase()) {
            "DARK" -> DARK
            else -> LIGHT
        }
    }

    fun createIcon(iconCode: Ikon) = FontIcon(iconCode).apply {
        iconSize = 16
        iconColor = this@Theme.fontColor
    }
}
