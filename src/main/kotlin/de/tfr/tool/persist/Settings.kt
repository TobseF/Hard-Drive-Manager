package de.tfr.tool.de.tfr.tool.persist

import de.tfr.tool.ui.Language
import de.tfr.tool.ui.Theme
import java.util.prefs.Preferences
import kotlin.reflect.KProperty

/**
 *  Typesafe settings provider which stores all values in the Windows registry.
 */
object Settings {

    /**
     * Saves the settings in the Windows registry.
     */
    private val prefs = Preferences.userRoot().node("de/tfr/tool/harddrivemanager")

    var equalCardHeights by BooleanProp("equalCardHeights")
    var fixedCardHeightEnabled by BooleanProp("fixedCardHeightEnabled")
    var fixedCardHeightPx by DoubleProp("fixedCardHeightPx", 220.0)
    var theme by EnumProp("theme", Theme::fromString, Theme.LIGHT)
    var language by EnumProp("language", Language::fromString, Language.DE)
    var dbPath by StringProp("db.path", trim = true)

    object Table {
        var showHidden by BooleanProp("table.showHidden")
    }

    class StringProp(private val key: String, private val defaultValue: String = "", private val trim: Boolean = true) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return prefs.get(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            if (trim) {
                prefs.put(key, value.trim())
            } else {
                prefs.put(key, value)
            }
        }
    }

    class BooleanProp(private val key: String, private val defaultValue: Boolean = false) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            return prefs.getBoolean(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.putBoolean(key, value)
        }
    }

    class DoubleProp(private val key: String, private val defaultValue: Double = 0.0) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Double {
            return prefs.getDouble(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
            prefs.putDouble(key, value)
        }
    }

    class EnumProp<T : Enum<T>>(private val key: String, val getter: (String) -> T, private val defaultValue: T) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            val savedName = prefs.get(key, null) ?: return defaultValue
            return getter.invoke(savedName)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            prefs.put(key, value.name)
        }
    }
}