package de.tfr.tool.ui

enum class Theme {
    LIGHT, DARK;

    companion object {
        fun fromString(name: String?): Theme = when (name?.uppercase()) {
            "DARK" -> DARK
            else -> LIGHT
        }
    }
}
