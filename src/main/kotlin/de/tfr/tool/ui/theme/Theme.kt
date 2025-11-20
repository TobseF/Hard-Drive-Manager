package de.tfr.tool.ui

import atlantafx.base.theme.PrimerDark
import atlantafx.base.theme.PrimerLight

enum class Theme(val atlantaFxTheme: atlantafx.base.theme.Theme) {
    LIGHT(PrimerLight()),
    DARK(PrimerDark());

    companion object {
        fun fromString(name: String?): Theme = when (name?.uppercase()) {
            "DARK" -> DARK
            else -> LIGHT
        }
    }
}
