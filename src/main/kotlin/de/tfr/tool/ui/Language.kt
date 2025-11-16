package de.tfr.tool.ui

enum class Language(val code: String) {
    DE("de"),
    EN("en");

    companion object {
        fun fromString(name: String?): Language = when (name?.uppercase()) {
            "EN" -> EN
            else -> DE
        }
    }
}
