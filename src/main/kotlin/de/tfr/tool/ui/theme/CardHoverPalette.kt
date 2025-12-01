package de.tfr.tool.ui

import javafx.scene.effect.DropShadow
import javafx.scene.paint.Color

/**
 * Describes the base and hover colors that are reused across card components.
 */
data class HoverPalette(
    val baseBackground: Color,
    val baseBorder: Color,
    val hoverBackground: Color,
    val hoverBorder: Color,
    val shadowColor: Color
)

data class TooltipPalette(
    val background: Color,
    val border: Color,
    val headerText: Color,
    val bodyText: Color,
    val barBackground: Color,
    val barFill: Color,
    val barBorder: Color
)

object CardHoverPalettes {
    fun disk(theme: Theme) = when (theme) {
        Theme.DARK -> HoverPalette(
            baseBackground = Color.web("#3c3f41"),
            baseBorder = Color.web("#55595c"),
            hoverBackground = Color.web("#4a4d50"),
            hoverBorder = Color.web("#6c7074"),
            shadowColor = Color.rgb(0, 0, 0, 0.55)
        )

        Theme.LIGHT -> HoverPalette(
            baseBackground = Color.web("#d9d9d9"),
            baseBorder = Color.web("#a5a5a5"),
            hoverBackground = Color.web("#f1f1f1"),
            hoverBorder = Color.web("#cfcfcf"),
            shadowColor = Color.rgb(0, 0, 0, 0.22)
        )
    }

    fun partition(theme: Theme) = when (theme) {
        Theme.DARK -> HoverPalette(
            baseBackground = Color.web("#4b4f51"),
            baseBorder = Color.web("#6a6e70"),
            hoverBackground = Color.web("#5a5f61"),
            hoverBorder = Color.web("#8c9194"),
            shadowColor = Color.rgb(0, 0, 0, 0.45)
        )

        Theme.LIGHT -> HoverPalette(
            baseBackground = Color.rgb(255, 250, 229),
            baseBorder = Color.rgb(234, 210, 140),
            hoverBackground = Color.rgb(255, 248, 215),
            hoverBorder = Color.rgb(247, 202, 120),
            shadowColor = Color.rgb(0, 0, 0, 0.12)
        )
    }

    fun tooltip(theme: Theme) = when (theme) {
        Theme.DARK -> TooltipPalette(
            background = Color.web("#2b2d2f"),
            border = Color.web("#4a4d50"),
            headerText = Color.web("#f2f2f2"),
            bodyText = Color.web("#d7d7d7"),
            barBackground = Color.web("#1f2123"),
            barFill = Color.web("#4aa3ff"),
            barBorder = Color.web("#6a6e70")
        )

        Theme.LIGHT -> TooltipPalette(
            background = Color.web("#ffffff"),
            border = Color.web("#d5d5d5"),
            headerText = Color.web("#1f1f1f"),
            bodyText = Color.web("#333333"),
            barBackground = Color.web("#f2f2f2"),
            barFill = Color.web("#f59e42"),
            barBorder = Color.web("#cfcfcf")
        )
    }
}

fun HoverPalette.createShadow(
    radius: Double = 18.0,
    offsetY: Double = 4.0,
    spread: Double = 0.18
): DropShadow = DropShadow(radius, shadowColor).apply {
    this.offsetY = offsetY
    this.spread = spread
}

fun Color.toCss(): String = "#" + String.format(
    "%02x%02x%02x",
    (red * 255).toInt().coerceIn(0, 255),
    (green * 255).toInt().coerceIn(0, 255),
    (blue * 255).toInt().coerceIn(0, 255)
)
